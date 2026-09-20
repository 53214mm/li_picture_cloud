package com.li.lipicturecloud.application.companion.observation;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.lipicturecloud.application.companion.CompanionViewAssembler;
import com.li.lipicturecloud.application.companion.observation.view.CompanionFeedRunDetailView;
import com.li.lipicturecloud.application.companion.observation.view.CompanionFeedRunSummaryView;
import com.li.lipicturecloud.application.companion.view.GrowthRecordView;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.FeedingRunStatus;
import com.li.lipicturecloud.domain.companion.NutritionPolicy;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.mapper.CompanionFeedObservationMapper;
import com.li.lipicturecloud.model.dto.companion.CompanionFeedObservationQueryRequest;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.repository.PictureRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 喂养观测的只读应用模块。
 *
 * <p>它把 MyBatis 投影和已有成长事实组合成管理员页面需要的摘要，不参与伙伴领域状态变化。</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.companion", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CompanionFeedObservationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CompanionFeedObservationMapper mapper;
    private final GrowthRecordRepository growthRepository;
    private final PictureRepository pictureRepository;
    private final CompanionViewAssembler views;
    private final CompanionFeedObservationAssembler assembler;
    private final Clock clock;

    public CompanionFeedObservationService(CompanionFeedObservationMapper mapper,
                                           GrowthRecordRepository growthRepository,
                                           PictureRepository pictureRepository,
                                           CompanionViewAssembler views,
                                           CompanionFeedObservationAssembler assembler,
                                           Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.growthRepository = Objects.requireNonNull(growthRepository, "growthRepository");
        this.pictureRepository = Objects.requireNonNull(pictureRepository, "pictureRepository");
        this.views = Objects.requireNonNull(views, "views");
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public IPage<CompanionFeedRunSummaryView> page(CompanionFeedObservationQueryRequest query) {
        validate(query);
        Page<CompanionFeedObservationRow> source = new Page<>(query.getCurrent(), query.getPageSize());
        IPage<CompanionFeedObservationRow> selected = mapper.selectPage(source, query);
        Page<CompanionFeedRunSummaryView> result = new Page<>(selected.getCurrent(), selected.getSize());
        result.setTotal(selected.getTotal());
        result.setPages(selected.getPages());
        Instant now = clock.instant();
        result.setRecords(selected.getRecords().stream()
                .map(row -> assembler.summary(row, now))
                .toList());
        return result;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CompanionFeedRunDetailView detail(long runId) {
        if (runId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "运行 ID 不合法");
        }
        CompanionFeedObservationRow row = mapper.selectByRunId(runId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "喂养运行不存在");
        }
        GrowthRecordView growth = growthRepository.findByFeedingRunId(runId)
                .map(views::growth)
                .orElse(null);
        String pictureName = pictureRepository.findById(row.pictureId())
                .map(Picture::getName)
                .orElse("图片已删除或当前不可用");
        CompanionFeedObservationRow enriched = row.withPictureName(pictureName);
        return assembler.detail(enriched, growth, clock.instant());
    }

    private static void validate(CompanionFeedObservationQueryRequest query) {
        if (query == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "查询参数不能为空");
        }
        if (query.getCurrent() < 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "页码必须大于0");
        }
        if (query.getPageSize() < 1 || query.getPageSize() > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "单页大小不能超过50");
        }
        positive(query.getSubjectId(), "用户 ID");
        positive(query.getPictureId(), "图片 ID");
        enumValue(query.getStatus(), FeedingRunStatus.class, "状态");
        enumValue(query.getRequestedPolicy(), NutritionPolicy.class, "营养策略");
        if (query.getStartTime() != null && query.getEndTime() != null
                && query.getStartTime().isAfter(query.getEndTime())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "开始时间不能晚于结束时间");
        }
        if (query.getUserKeyword() != null && query.getUserKeyword().length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户关键词不能超过64个字符");
        }
        if (query.getCorrelationId() != null && query.getCorrelationId().length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "链路 ID 不能超过64个字符");
        }
    }

    private static void positive(Long value, String label) {
        if (value != null && value <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, label + "必须大于0");
        }
    }

    private static <E extends Enum<E>> void enumValue(String value, Class<E> type, String label) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, label + "不合法");
        }
    }
}
