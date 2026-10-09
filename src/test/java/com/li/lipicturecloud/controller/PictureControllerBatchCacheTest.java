package com.li.lipicturecloud.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.li.lipicturecloud.config.JacksonConfig;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant;
import com.li.lipicturecloud.model.dto.picture.PictureEditByBatchRequest;
import com.li.lipicturecloud.model.dto.picture.PictureQueryRequest;
import com.li.lipicturecloud.model.entity.Picture;
import com.li.lipicturecloud.model.entity.Space;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.model.vo.PictureVO;
import com.li.lipicturecloud.service.PictureService;
import com.li.lipicturecloud.service.SpaceService;
import com.li.lipicturecloud.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises the controller's real Caffeine cache, serialization and Redis key generation. */
class PictureControllerBatchCacheTest {

    private static final long SPACE_ID = 7L;
    private static final long PICTURE_ID = 9L;

    private PictureController controller;
    private PictureService pictureService;
    private SpaceAuthorizationAccessService authorizationAccessService;
    private HttpServletRequest request;
    private User owner;
    private Picture storedPicture;
    private Map<String, String> redisEntries;
    private List<String> redisReads;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        controller = new PictureController();
        pictureService = mock(PictureService.class);
        SpaceService spaceService = mock(SpaceService.class);
        UserService userService = mock(UserService.class);
        authorizationAccessService = mock(SpaceAuthorizationAccessService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        request = mock(HttpServletRequest.class);
        redisEntries = new LinkedHashMap<>();
        redisReads = new ArrayList<>();

        owner = new User();
        owner.setId(11L);
        Space space = new Space();
        space.setId(SPACE_ID);
        space.setUserId(owner.getId());
        storedPicture = new Picture();
        storedPicture.setId(PICTURE_ID);
        storedPicture.setSpaceId(SPACE_ID);
        storedPicture.setUserId(owner.getId());
        storedPicture.setName("before");
        storedPicture.setCategory("before-category");
        storedPicture.setTags(JSONUtil.toJsonStr(List.of("before-tag")));

        when(spaceService.getById(SPACE_ID)).thenReturn(space);
        when(userService.getLoginUserEntity(request)).thenReturn(owner);
        when(pictureService.getQueryWrapper(any(PictureQueryRequest.class)))
                .thenReturn(new QueryWrapper<>());
        when(pictureService.page(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<Picture> page = invocation.getArgument(0);
                    page.setTotal(1);
                    page.setRecords(List.of(storedPicture));
                    return page;
                });
        when(pictureService.getPictureVOPage(any(Page.class), eq(request)))
                .thenAnswer(invocation -> {
                    Page<Picture> page = invocation.getArgument(0);
                    Page<PictureVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
                    result.setRecords(page.getRecords().stream().map(PictureVO::objToVo).toList());
                    return result;
                });
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            redisReads.add(key);
            return redisEntries.get(key);
        });
        doAnswer(invocation -> {
            redisEntries.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));

        ReflectionTestUtils.setField(controller, "pictureService", pictureService);
        ReflectionTestUtils.setField(controller, "spaceService", spaceService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "authorizationAccessService", authorizationAccessService);
        ReflectionTestUtils.setField(controller, "stringRedisTemplate", redisTemplate);
    }

    @Test
    void successfulBatchRefreshesAnAlreadyWarmLocalQuery() {
        assertBefore(readCachedPage());
        assertBefore(readCachedPage());
        verifyDatabaseReads(1);
        assertThat(redisReads).hasSize(1);
        Map<String, String> staleRedisEntries = Map.copyOf(redisEntries);

        editStoredPictureSuccessfully();

        assertAfter(readCachedPage());
        assertAfter(readCachedPage());
        verifyDatabaseReads(2);
        assertThat(redisReads).hasSize(2).doesNotHaveDuplicates();
        assertThat(redisEntries).hasSize(2).containsAllEntriesOf(staleRedisEntries);
    }

    @Test
    void successfulBatchCannotReuseTheStaleRedisEntryWhenLocalCacheIsEmpty() {
        assertBefore(readCachedPage());
        String staleKey = redisReads.getFirst();
        String stalePayload = redisEntries.get(staleKey);
        evictLocalCacheOnly();
        assertBefore(readCachedPage());
        assertThat(redisReads).containsExactly(staleKey, staleKey);
        verifyDatabaseReads(1);

        editStoredPictureSuccessfully();
        evictLocalCacheOnly();

        assertAfter(readCachedPage());
        verifyDatabaseReads(2);
        assertThat(redisReads).hasSize(3);
        String freshKey = redisReads.getLast();
        assertThat(freshKey).isNotEqualTo(staleKey);
        assertThat(redisEntries).hasSize(2).containsEntry(staleKey, stalePayload);

        evictLocalCacheOnly();
        assertAfter(readCachedPage());
        verifyDatabaseReads(2);
        assertThat(redisReads).containsExactly(staleKey, staleKey, freshKey, freshKey);
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"OPERATION_ERROR", "NO_AUTH_ERROR"})
    void failedOrDeniedBatchLeavesBothCacheTiersAndGenerationUnchanged(ErrorCode errorCode) {
        assertBefore(readCachedPage());
        String existingKey = redisReads.getFirst();
        Map<String, String> existingEntries = Map.copyOf(redisEntries);
        PictureEditByBatchRequest batch = batchRequest();
        BusinessException failure = new BusinessException(errorCode);
        doThrow(failure).when(pictureService).editPictureByBatch(batch, owner);

        assertThatThrownBy(() -> controller.editPictureByBatch(batch, request)).isSameAs(failure);

        assertBefore(readCachedPage());
        assertThat(redisReads).containsExactly(existingKey);
        verifyDatabaseReads(1);
        assertThat(redisEntries).isEqualTo(existingEntries);
        evictLocalCacheOnly();
        assertBefore(readCachedPage());
        assertThat(redisReads).containsExactly(existingKey, existingKey);
        verifyDatabaseReads(1);
        assertThat(redisEntries).isEqualTo(existingEntries);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void everyCacheHitStillRequiresSpaceViewPermission(boolean redisHit) {
        assertBefore(readCachedPage());
        if (redisHit) {
            evictLocalCacheOnly();
        }
        assertBefore(readCachedPage());
        verifyDatabaseReads(1);
        assertThat(redisReads).hasSize(redisHit ? 2 : 1);
        Map<String, String> existingEntries = Map.copyOf(redisEntries);
        List<String> priorReads = List.copyOf(redisReads);
        BusinessException denied = new BusinessException(ErrorCode.NO_AUTH_ERROR);
        doThrow(denied).when(authorizationAccessService).check(
                SpaceUserPermissionConstant.PICTURE_VIEW, SPACE_ID, null, null, request);
        if (redisHit) {
            evictLocalCacheOnly();
        }

        assertThatThrownBy(this::readCachedPage).isSameAs(denied);

        verify(authorizationAccessService, times(3)).check(
                SpaceUserPermissionConstant.PICTURE_VIEW, SPACE_ID, null, null, request);
        verifyDatabaseReads(1);
        assertThat(redisReads).isEqualTo(priorReads);
        assertThat(redisEntries).isEqualTo(existingEntries);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cacheHitsPreserveDateJsonAndExactIds(boolean redisHit) throws Exception {
        storedPicture.setId(9223372036854775806L);
        storedPicture.setCreateTime(new Date(1789971452000L));
        storedPicture.setEditTime(new Date(1789971453000L));
        storedPicture.setUpdateTime(new Date(1789971454000L));
        storedPicture.setReviewTime(new Date(1789971455000L));
        var builder = new Jackson2ObjectMapperBuilder()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        new JacksonConfig().longToStringCustomizer().customize(builder);
        var mapper = builder.build();
        var first = mapper.readTree(mapper.writeValueAsString(readCachedResponse()));
        if (redisHit) evictLocalCacheOnly();
        var cached = readCachedResponse();
        assertThat(mapper.readTree(mapper.writeValueAsString(cached))).isEqualTo(first);
        assertThat(first.at("/data/records/0/id").asText()).isEqualTo("9223372036854775806");
        assertThat(cached.getData().getRecords().getFirst()).isInstanceOf(PictureVO.class);
        verifyDatabaseReads(1);
        assertThat(redisReads).hasSize(redisHit ? 2 : 1);
    }

    private BaseResponse<Page<PictureVO>> readCachedResponse() {
        PictureQueryRequest query = new PictureQueryRequest();
        query.setCurrent(1);
        query.setPageSize(12);
        query.setSpaceId(SPACE_ID);
        return controller.listPictureVOByPageWithCache(query, request);
    }

    private PictureVO readCachedPage() {
        BaseResponse<Page<PictureVO>> response = readCachedResponse();
        assertThat(response.getCode()).isZero();
        Page<PictureVO> page = response.getData();
        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(12);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        return page.getRecords().getFirst();
    }

    private PictureEditByBatchRequest batchRequest() {
        PictureEditByBatchRequest batch = new PictureEditByBatchRequest();
        batch.setSpaceId(SPACE_ID);
        batch.setPictureIdList(List.of(PICTURE_ID));
        batch.setNameRule("after-{序号}");
        batch.setCategory("after-category");
        batch.setTags(List.of("after-tag", "another-tag"));
        return batch;
    }

    private void editStoredPictureSuccessfully() {
        PictureEditByBatchRequest batch = batchRequest();
        doAnswer(invocation -> {
            storedPicture.setName("after-1");
            storedPicture.setCategory(batch.getCategory());
            storedPicture.setTags(JSONUtil.toJsonStr(batch.getTags()));
            return null;
        }).when(pictureService).editPictureByBatch(batch, owner);
        BaseResponse<Boolean> response = controller.editPictureByBatch(batch, request);
        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isTrue();
        verify(pictureService).editPictureByBatch(batch, owner);
    }

    private void verifyDatabaseReads(int count) {
        verify(pictureService, times(count)).page(any(Page.class), any(QueryWrapper.class));
        verify(pictureService, times(count)).getPictureVOPage(any(Page.class), eq(request));
    }

    private void evictLocalCacheOnly() {
        Cache<?, ?> cache = (Cache<?, ?>) ReflectionTestUtils.getField(controller, "LOCAL_CACHE");
        assertThat(cache).isNotNull();
        cache.invalidateAll();
    }

    private void assertBefore(PictureVO picture) {
        assertThat(picture.getId()).isEqualTo(PICTURE_ID);
        assertThat(picture.getSpaceId()).isEqualTo(SPACE_ID);
        assertThat(picture.getName()).isEqualTo("before");
        assertThat(picture.getCategory()).isEqualTo("before-category");
        assertThat(picture.getTags()).containsExactly("before-tag");
    }

    private void assertAfter(PictureVO picture) {
        assertThat(picture.getId()).isEqualTo(PICTURE_ID);
        assertThat(picture.getSpaceId()).isEqualTo(SPACE_ID);
        assertThat(picture.getName()).isEqualTo("after-1");
        assertThat(picture.getCategory()).isEqualTo("after-category");
        assertThat(picture.getTags()).containsExactly("after-tag", "another-tag");
    }
}
