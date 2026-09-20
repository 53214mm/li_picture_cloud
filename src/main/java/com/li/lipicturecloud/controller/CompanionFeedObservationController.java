package com.li.lipicturecloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.application.companion.observation.CompanionFeedObservationService;
import com.li.lipicturecloud.application.companion.observation.view.CompanionFeedRunDetailView;
import com.li.lipicturecloud.application.companion.observation.view.CompanionFeedRunSummaryView;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.ResultUtils;
import com.li.lipicturecloud.constant.UserConstant;
import com.li.lipicturecloud.model.dto.companion.CompanionFeedObservationQueryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员查看伙伴喂养链路的只读接口。 */
@RestController
@ConditionalOnProperty(prefix = "app.companion", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@RequestMapping(value = "/admin/companion/feed-runs", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "伙伴喂养观测", description = "管理员查看伙伴喂养运行的阶段、结果和失败位置")
public class CompanionFeedObservationController {

    private final CompanionFeedObservationService service;

    public CompanionFeedObservationController(CompanionFeedObservationService service) {
        this.service = service;
    }

    @PostMapping("/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "分页查看伙伴喂养运行")
    public BaseResponse<IPage<CompanionFeedRunSummaryView>> page(
            @RequestBody CompanionFeedObservationQueryRequest query) {
        return ResultUtils.success(service.page(query));
    }

    @GetMapping("/{runId}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Operation(summary = "查看单次伙伴喂养详情")
    public BaseResponse<CompanionFeedRunDetailView> detail(@PathVariable long runId) {
        return ResultUtils.success(service.detail(runId));
    }
}
