package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.application.airuntime.view.FusionImageView;
import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.CreationFusionImage;
import com.li.lipicturecloud.domain.airuntime.CreationFusionImageRepository;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationLineage;
import com.li.lipicturecloud.domain.airuntime.CreationLineageRepository;
import com.li.lipicturecloud.domain.airuntime.CreationStatus;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.airuntime.CreationTaskRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 多图融合应用服务。
 *
 * <p><b>玩法当前未开放：</b>现有图像模型适配器只支持文生图（images/generations），
 * 没有 reference image / image edit 输入，无法把多张授权源图真正融合为新图。
 * 在支持多图参考输入且通过供应商能力验证的适配器就绪前，{@code generate} 一律明确
 * 失败：不调用图像模型、不预占/结算额度、不生成误导性血缘。任务的创建/保存/预览
 * API 保留以便适配器就绪后恢复闭环；原图永不覆盖。</p>
 */
@Service
public class FusionImageService {

    public static final String NOT_OPEN_YET_MESSAGE =
            "真实多图融合能力尚未开放：当前图像模型适配器不支持多图参考输入。"
                    + "需要支持图片编辑/多参考图的适配器验证完成后才会开放。";
    public static final String CAPABILITY_SAVE = "IMAGE_FUSION_SAVE";
    public static final String PROMPT_TEMPLATE_VERSION = "fusion-v1";
    public static final int MIN_SOURCE_PICTURES = 2;

    private static final Logger log = LoggerFactory.getLogger(FusionImageService.class);

    private final CreationTaskRepository taskRepository;
    private final CreationFusionImageRepository fusionImageRepository;
    private final CreationLineageRepository lineageRepository;
    private final CreationServiceSupport support;
    private final FusionArtworkSaver artworkSaver;
    private final ModelConnectionService connectionService;
    private final Clock clock;

    public FusionImageService(CreationTaskRepository taskRepository,
                              CreationFusionImageRepository fusionImageRepository,
                              CreationLineageRepository lineageRepository,
                              CreationServiceSupport support,
                              FusionArtworkSaver artworkSaver,
                              ModelConnectionService connectionService,
                              Clock clock) {
        this.taskRepository = taskRepository;
        this.fusionImageRepository = fusionImageRepository;
        this.lineageRepository = lineageRepository;
        this.support = support;
        this.artworkSaver = artworkSaver;
        this.connectionService = connectionService;
        this.clock = clock;
    }

    public CreationTask create(AuthorizationSubject subject, List<Long> pictureIds,
                               String idempotencyKey) {
        Objects.requireNonNull(subject, "subject");
        if (subject.platformAdmin()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "平台管理员不参与创作");
        }
        if (idempotencyKey == null || idempotencyKey.length() != 36) {
            idempotencyKey = UUID.randomUUID().toString();
        }
        List<Long> ids = support.requireValidPictureIds(pictureIds);
        if (ids.size() < MIN_SOURCE_PICTURES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "多图融合至少需要 " + MIN_SOURCE_PICTURES + " 张图片");
        }
        support.reauthorizePictureIds(subject, ids);
        return taskRepository.insert(CreationTask.create(subject.userId(), CreationKind.IMAGE_FUSION,
                ids, idempotencyKey, clock.instant()));
    }

    public CreationTask generate(AuthorizationSubject subject, long taskId) {
        CreationTask task = requireFusionTask(subject, taskId);
        // 玩法未开放：当前图像模型适配器只支持文生图（images/generations），没有
        // reference image / image edit 输入，无法把多张授权源图真正融合成新图。
        // 在支持多图参考输入的适配器完成供应商能力验证之前，generate 明确失败：
        // 不调用图像模型、不预占/结算额度、不生成误导性血缘；任务进入安全失败终态。
        try {
            task = support.transition(task, task.fail(clock.instant()));
        } catch (IllegalStateException alreadyTerminal) {
            // 已终态则无需再写 FAILED。
        }
        log.warn("fusion_generate_unavailable subjectId={} taskId={} reason=NOT_OPEN_YET",
                subject.userId(), task.id());
        throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, NOT_OPEN_YET_MESSAGE);
    }

    public CreationTask save(AuthorizationSubject subject, long taskId, Long spaceId, String name) {
        Objects.requireNonNull(subject, "subject");
        if (spaceId == null || spaceId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择保存的目标空间");
        }
        CreationTask task = requireFusionTask(subject, taskId);
        try {
            task = support.transition(task, task.confirmFusion(clock.instant()));
        } catch (IllegalStateException wrongState) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务状态已变化，请刷新后重试");
        }
        try {
            // 保存执行前再次校验来源图片查看权与血缘所需的模型连接（规格 §5）。
            support.reauthorizePictures(subject, task);
            if (task.modelConnectionId() == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务缺少模型连接信息，无法保存");
            }
            com.li.lipicturecloud.domain.airuntime.ModelConnection connection =
                    connectionService.findOwned(task.modelConnectionId(), subject.userId());
            CreationFusionImage staged = fusionImageRepository.findByTaskId(taskId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                            "融合结果已失效，请重新生成"));
            long pictureId = artworkSaver.save(new FusionArtworkSaveRequest(subject.userId(),
                    spaceId, name, staged.mimeType(), staged.bytes()));
            // 关键：转移成功后把 task 推进到当前状态，后续失败必须基于最新状态写 FAILED。
            task = support.transition(task, task.completeFusionSave(pictureId, clock.instant()));
            // SAVED 已是终态且作品已回库：血缘追加失败只能告警，绝不可把成功保存报成失败。
            recordLineageBestEffort(task, CAPABILITY_SAVE, connection.modelCode(),
                    CostSource.BYOK.name(), pictureId);
            return task;
        } catch (RuntimeException failure) {
            try {
                support.transition(task, task.fail(clock.instant()));
            } catch (IllegalStateException alreadyTerminal) {
                // 已终态则无需再写 FAILED。
            }
            throw failure;
        }
    }

    public List<CreationTask> list(AuthorizationSubject subject, int limit) {
        Objects.requireNonNull(subject, "subject");
        return taskRepository.findBySubjectIdAndKind(subject.userId(), CreationKind.IMAGE_FUSION,
                limit).stream().map(support::applyExpiry).toList();
    }

    public FusionImageView previewImage(AuthorizationSubject subject, long taskId) {
        CreationTask task = requireFusionTask(subject, taskId);
        // 预览只服务于可确认/已保存的任务；失败或过期任务不再暴露暂存字节。
        if (task.status() != CreationStatus.AWAITING_CONFIRM
                && task.status() != CreationStatus.SAVING
                && task.status() != CreationStatus.SAVED) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "融合结果不存在");
        }
        CreationFusionImage staged = fusionImageRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "融合结果不存在"));
        return new FusionImageView(staged.mimeType(), staged.bytes());
    }

    private CreationTask requireFusionTask(AuthorizationSubject subject, long taskId) {
        return support.requireOwnedOfKind(subject, taskId, CreationKind.IMAGE_FUSION);
    }

    private void recordLineage(CreationTask task, String capabilityId, String modelCode,
                               String costSource, Long resultPictureId) {
        for (Long pictureId : task.sourcePictureIds()) {
            lineageRepository.append(new CreationLineage(null, task.id(), pictureId, resultPictureId,
                    capabilityId, modelCode, PROMPT_TEMPLATE_VERSION, costSource,
                    clock.instant()));
        }
    }

    /** 保存路径专用：作品已回库（终态），血缘追加失败只告警不反悔。 */
    private void recordLineageBestEffort(CreationTask task, String capabilityId, String modelCode,
                                         String costSource, Long resultPictureId) {
        try {
            recordLineage(task, capabilityId, modelCode, costSource, resultPictureId);
        } catch (RuntimeException lineageFailure) {
            log.warn("fusion_lineage_append_failed taskId={} capability={}",
                    task.id(), capabilityId);
        }
    }
}
