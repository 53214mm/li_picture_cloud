package com.li.lipicturecloud.application.companion.observation;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.lipicturecloud.application.companion.CompanionViewAssembler;
import com.li.lipicturecloud.application.companion.observation.view.CompanionFeedRunSummaryView;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.model.dto.companion.CompanionFeedObservationQueryRequest;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.mapper.CompanionFeedObservationMapper;
import com.li.lipicturecloud.repository.PictureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionFeedObservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T10:00:03Z");

    private CompanionFeedObservationMapper mapper;
    private GrowthRecordRepository growthRepository;
    private PictureRepository pictureRepository;
    private CompanionFeedObservationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(CompanionFeedObservationMapper.class);
        growthRepository = mock(GrowthRecordRepository.class);
        pictureRepository = mock(PictureRepository.class);
        CompanionViewAssembler views = new CompanionViewAssembler(CompanionBalance.v1(), null,
                new CompanionFeatureProperties());
        service = new CompanionFeedObservationService(mapper, growthRepository, pictureRepository,
                views, new CompanionFeedObservationAssembler(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void pageMapsRowsAndPreservesDatabaseTotal() {
        CompanionFeedObservationQueryRequest query = new CompanionFeedObservationQueryRequest();
        query.setCurrent(2);
        query.setPageSize(10);
        Page<CompanionFeedObservationRow> stored = new Page<>(2, 10);
        stored.setTotal(21);
        stored.setRecords(List.of(row()));
        when(mapper.selectPage(any(), eq(query))).thenReturn(stored);

        var result = service.page(query);

        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getTotal()).isEqualTo(21);
        assertThat(result.getRecords()).singleElement()
                .extracting(CompanionFeedRunSummaryView::statusLabel, CompanionFeedRunSummaryView::stageLabel)
                .containsExactly("已完成", "成长结算");
        verify(mapper).selectPage(any(), eq(query));
    }

    @Test
    void detailCombinesTheRunWithOptionalGrowthAndPictureFacts() {
        when(mapper.selectByRunId(21L)).thenReturn(row());
        Picture picture = new Picture();
        picture.setName("图片.jpg");
        when(pictureRepository.findById(102L)).thenReturn(java.util.Optional.of(picture));

        var result = service.detail(21L);

        assertThat(result.summary().pictureName()).isEqualTo("图片.jpg");
        assertThat(result.summary().statusLabel()).isEqualTo("已完成");
        assertThat(result.growth()).isNull();
        verify(growthRepository).findByFeedingRunId(21L);
        verify(pictureRepository).findById(102L);
    }

    @Test
    void detailExplainsWhenThePictureIsNoLongerAvailable() {
        when(mapper.selectByRunId(21L)).thenReturn(row());
        when(pictureRepository.findById(102L)).thenReturn(java.util.Optional.empty());

        var result = service.detail(21L);

        assertThat(result.summary().pictureName()).isEqualTo("图片已删除或当前不可用");
    }

    @Test
    void detailRejectsAnUnknownRunId() {
        when(mapper.selectByRunId(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("喂养运行不存在");
    }

    @Test
    void pageRejectsOversizedPageInsteadOfSilentlyScanningTooMuch() {
        CompanionFeedObservationQueryRequest query = new CompanionFeedObservationQueryRequest();
        query.setPageSize(51);

        assertThatThrownBy(() -> service.page(query))
                .isInstanceOf(BusinessException.class)
                .hasMessage("单页大小不能超过50");
    }

    @Test
    void pageRejectsAnInvalidTimeRangeBeforeQueryingDatabase() {
        CompanionFeedObservationQueryRequest query = new CompanionFeedObservationQueryRequest();
        query.setStartTime(NOW);
        query.setEndTime(NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.page(query))
                .isInstanceOf(BusinessException.class)
                .hasMessage("开始时间不能晚于结束时间");
    }

    @Test
    void pageRejectsUnknownStatusBeforeQueryingDatabase() {
        CompanionFeedObservationQueryRequest query = new CompanionFeedObservationQueryRequest();
        query.setStatus("BROKEN");

        assertThatThrownBy(() -> service.page(query))
                .isInstanceOf(BusinessException.class)
                .hasMessage("状态不合法");
    }

    private CompanionFeedObservationRow row() {
        Instant created = Instant.parse("2026-08-30T10:00:00Z");
        return new CompanionFeedObservationRow(
                21L, "fef53056-2d9f-467d-9b1d-1afe9a6638fe", 11L, 7L, 102L,
                "alice", "Alice", "COMPLETED", "METADATA_ONLY", null, null, 31L,
                null, null, null, 1, 1L, created, NOW, "图片.jpg",
                "6f26d166-0a82-4d9f-8a61-6c21cf2e59d0", "METADATA_DETERMINISTIC", false,
                "internal", "metadata-v1", null, null, null, null, 42L, "PICTURE_FED",
                "元数据营养", NOW);
    }
}
