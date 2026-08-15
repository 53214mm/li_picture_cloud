package com.li.lipicturecloud.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.application.recipe.RecipeService;
import com.li.lipicturecloud.application.recipe.view.RecipeDetailView;
import com.li.lipicturecloud.application.recipe.view.RecipeTemplateView;
import com.li.lipicturecloud.application.recipe.view.RecipeView;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.ResultUtils;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.model.dto.recipe.RecipeCreateRequest;
import com.li.lipicturecloud.model.dto.recipe.RecipeFromTemplateRequest;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 玩法配方工坊 HTTP 边界：模板起点、配方 CRUD、版本发布与回放。
 * 定义 JSON 只在服务端严格校验（白名单键值 + 收紧语义），主体来自登录态。
 */
@RestController
@RequestMapping(value = "/recipe", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "玩法配方工坊", description = "官方模板、配方定义与版本历史")
public class RecipeController {

    private final RecipeService recipeService;
    private final UserService userService;

    public RecipeController(RecipeService recipeService, UserService userService) {
        this.recipeService = recipeService;
        this.userService = userService;
    }

    @GetMapping("/templates")
    @AuthCheck
    public BaseResponse<List<RecipeTemplateView>> templates() {
        return ResultUtils.success(recipeService.templates());
    }

    @PostMapping
    @AuthCheck
    public BaseResponse<RecipeView> create(@RequestBody RecipeCreateRequest body,
                                           HttpServletRequest request) {
        return ResultUtils.success(recipeService.create(subject(request),
                body == null ? null : body.getName()));
    }

    @PostMapping("/from-template")
    @AuthCheck
    public BaseResponse<RecipeDetailView> createFromTemplate(
            @RequestBody RecipeFromTemplateRequest body, HttpServletRequest request) {
        return ResultUtils.success(recipeService.createFromTemplate(subject(request),
                body == null ? null : body.getTemplateCode(),
                body == null ? null : body.getName()));
    }

    @GetMapping
    @AuthCheck
    public BaseResponse<List<RecipeView>> list(@RequestParam(defaultValue = "20") int limit,
                                               HttpServletRequest request) {
        return ResultUtils.success(recipeService.list(subject(request), limit));
    }

    @GetMapping("/{id}")
    @AuthCheck
    public BaseResponse<RecipeDetailView> detail(@PathVariable long id,
                                                 HttpServletRequest request) {
        return ResultUtils.success(recipeService.detail(subject(request), id));
    }

    @PostMapping("/{id}/versions")
    @AuthCheck
    public BaseResponse<RecipeDetailView> publishVersion(@PathVariable long id,
                                                         @RequestBody JsonNode body,
                                                         HttpServletRequest request) {
        return ResultUtils.success(recipeService.publishDefinition(subject(request), id, body));
    }

    @PostMapping("/{id}/enable")
    @AuthCheck
    public BaseResponse<RecipeView> enable(@PathVariable long id, HttpServletRequest request) {
        return ResultUtils.success(recipeService.enable(subject(request), id));
    }

    @PostMapping("/{id}/disable")
    @AuthCheck
    public BaseResponse<RecipeView> disable(@PathVariable long id, HttpServletRequest request) {
        return ResultUtils.success(recipeService.disable(subject(request), id));
    }

    @DeleteMapping("/{id}")
    @AuthCheck
    public BaseResponse<Boolean> delete(@PathVariable long id, HttpServletRequest request) {
        recipeService.delete(subject(request), id);
        return ResultUtils.success(true);
    }

    private AuthorizationSubject subject(HttpServletRequest request) {
        User loginUser = userService.getLoginUserEntity(request);
        return userService.isAdmin(loginUser)
                ? AuthorizationSubject.platformAdmin(loginUser.getId())
                : AuthorizationSubject.user(loginUser.getId());
    }
}
