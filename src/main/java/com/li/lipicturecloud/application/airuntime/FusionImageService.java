package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.application.airuntime.view.FusionImageView;
import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.CreationFusionImage;
import com.li.lipicturecloud.domain.airuntime.CreationFusionImageRepository;
import com.li.lipicturecloud.domain.airuntime.CreationKind;
import com.li.lipicturecloud.domain.airuntime.CreationLineage;
import com.li.lipicturecloud.domain.airuntime.CreationLineageRepository;
import com.li.lipicturecloud.domain.airuntime.CreationTask;
import com.li.lipicturecloud.domain.airuntime.CreationTaskRepository;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 多图融合应用服务：授权图片 → 图片创作路由生成融合图（字节暂存专用表）
 * → 用户确认目标空间 → 复用图片上传/保存管线回库 → 血缘记录结果图片。
 *
 * <p>平台图片创作账本未上线前平台路由大声失败；BYOK 失败绝不静默回退。
 * 生成提示词只由安全落地线索构建，绝不携带图片字节、名称或用户原文；
 * 暂存字节不进入任务文本字段；日志不记录提示词与结果。原图永不覆盖。</p>
 */
@Service
public class FusionImageService {

    public static final String CAPABILITY_GENERATE = "IMAGE_FUSION_GENERATE";
    public static final String CAPABILITY_SAVE = "IMAGE_FUSION_SAVE";
    public static final String PROMPT_TEMPLATE_VERSION = "fusion-v1";
    public static final int MIN_SOURCE_PICTURES = 2;
    public static final String DEFAULT_SIZE = "1024x1024";

    private static final Logger log = LoggerFactory.getLogger(FusionImageService.class);
    private static final String GENERATE_PROMPT_TEMPLATE =
            "把用户选择的 %d 张图片融合成一张新图：构图自然和谐，主体完整，光线统一。%s";

    private final CreationTaskRepository taskRepository;
    private final CreationFusionImageRepository fusionImageRepository;
    private final CreationLineageRepository lineageRepository;
    private final CreationServiceSupport support;
    private final ImageRouter imageRouter;
    private final ImageModelInvoker imageInvoker;
    private final FusionArtworkSaver artworkSaver;
    private final ModelUsageService usageService;
    private final ModelConnectionService connectionService;
    private final Clock clock;

    public FusionImageService(CreationTaskRepository taskRepository,
                              CreationFusionImageRepository fusionImageRepository,
                              CreationLineageRepository lineageRepository,
                              CreationServiceSupport support,
                              ImageRouter imageRouter,
                              ImageModelInvoker imageInvoker,
                              FusionArtworkSaver artworkSaver,
                              ModelUsageService usageService,
                              ModelConnectionService connectionService,
                              Clock clock) {
        this.taskRepository = taskRepository;
        this.fusionImageRepository = fusionImageRepository;
        this.lineageRepository = lineageRepository;
        this.support = support;
        this.imageRouter = imageRouter;
        this.imageInvoker = imageInvoker;
        this.artworkSaver = artworkSaver;
        this.usageService = usageService;
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
        try {
            task = support.transition(task, task.startOutlining(clock.instant()));
        } catch (IllegalStateException wrongState) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务状态已变化，请刷新后重试");
        }
        ModelRouteDecision route = null;
        try {
            // 执行前重新校验：分享撤销/移动后不得让旧选择越过权限边界（规格 §5）。
            support.reauthorizePictures(subject, task);
            route = imageRouter.decide(subject.userId());
            if (!route.isByok()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "平台图片创作尚未开放，请在控制中心为图像生成任务绑定用户连接");
            }
            String prompt = GENERATE_PROMPT_TEMPLATE.formatted(task.sourcePictureIds().size(),
                    support.grounding(task.sourcePictureIds()));
            ImageGenerationResult result = imageInvoker.invoke(route, prompt, DEFAULT_SIZE);
            byte[] bytes = decodeInlineImage(result);
            String mimeType = ImageFormatSniffer.detect(bytes);
            fusionImageRepository.insert(CreationFusionImage.create(
                    task.id(), mimeType, bytes, clock.instant()));
            // 关键：转移成功后把 task 推进到当前状态，后续失败必须基于最新状态写 FAILED。
            task = support.transition(task, task.completeFusion(route.connection().id(),
                    clock.instant()));
            recordLineage(task, CAPABILITY_GENERATE, route.connection().modelCode(),
                    CostSource.BYOK.name(), null);
            recordUsageSuccess(subject.userId(), route);
            return task;
        } catch (RuntimeException failure) {
            if (route != null && route.isByok()) {
                recordUsageFailure(subject.userId(), route, safeErrorCode(failure));
            }
            try {
                support.transition(task, task.fail(clock.instant()));
            } catch (RuntimeException alreadyTerminal) {
                // 已终态则无需再写 FAILED。
            }
            throw failure;
        }
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
            recordLineage(task, CAPABILITY_SAVE, connection.modelCode(),
                    CostSource.BYOK.name(), pictureId);
            return task;
        } catch (RuntimeException failure) {
            try {
                support.transition(task, task.fail(clock.instant()));
            } catch (RuntimeException alreadyTerminal) {
                // 已终态则无需再写 FAILED。
            }
            throw failure;
        }
    }

    public List<CreationTask> list(AuthorizationSubject subject, int limit) {
        Objects.requireNonNull(subject, "subject");
        return taskRepository.findBySubjectId(subject.userId(), limit).stream()
                .filter(task -> task.kind() == CreationKind.IMAGE_FUSION)
                .map(support::applyExpiry)
                .toList();
    }

    public FusionImageView previewImage(AuthorizationSubject subject, long taskId) {
        requireFusionTask(subject, taskId);
        CreationFusionImage staged = fusionImageRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "融合结果不存在"));
        return new FusionImageView(staged.mimeType(), staged.bytes());
    }

    private CreationTask requireFusionTask(AuthorizationSubject subject, long taskId) {
        return support.requireOwnedOfKind(subject, taskId, CreationKind.IMAGE_FUSION);
    }

    /** 融合暂存只接受内联 base64 图片；仅返回供应商临时 URL 的连接大声失败，不代为抓取（SSRF 风险）。 */
    private byte[] decodeInlineImage(ImageGenerationResult result) {
        if (result.base64Image() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "当前连接的图像模型只返回图片链接，融合暂存需要内联图片（b64_json），请更换连接");
        }
        try {
            return Base64.getDecoder().decode(result.base64Image());
        } catch (IllegalArgumentException malformed) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "融合生成失败：返回图片不完整");
        }
    }

    private void recordLineage(CreationTask task, String capabilityId, String modelCode,
                               String costSource, Long resultPictureId) {
        for (Long pictureId : task.sourcePictureIds()) {
            lineageRepository.append(new CreationLineage(null, task.id(), pictureId, resultPictureId,
                    capabilityId, modelCode, PROMPT_TEMPLATE_VERSION, costSource,
                    clock.instant()));
        }
    }

    private void recordUsageSuccess(long subjectId, ModelRouteDecision route) {
        try {
            usageService.recordSuccess(subjectId, ModelTask.IMAGE_CREATION,
                    route.connection().id(), route.connection().provider(),
                    route.connection().modelCode(), CostSource.BYOK);
        } catch (RuntimeException recordFailure) {
            log.warn("fusion_usage_record_failed subjectId={}", subjectId);
        }
    }

    private void recordUsageFailure(long subjectId, ModelRouteDecision route, String safeErrorCode) {
        try {
            usageService.recordFailure(subjectId, ModelTask.IMAGE_CREATION,
                    route.connection().id(), route.connection().provider(),
                    route.connection().modelCode(), CostSource.BYOK, safeErrorCode);
        } catch (RuntimeException recordFailure) {
            log.warn("fusion_usage_record_failed subjectId={} code={}", subjectId, safeErrorCode);
        }
    }

    private String safeErrorCode(RuntimeException failure) {
        if (failure instanceof ModelInvocationException invocation) {
            return invocation.safeErrorCode();
        }
        return "INTERNAL";
    }
}
