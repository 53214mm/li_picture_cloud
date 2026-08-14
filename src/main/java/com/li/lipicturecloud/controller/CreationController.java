package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.application.airuntime.EmojiDraftService;
import com.li.lipicturecloud.application.airuntime.StoryDraftService;
import com.li.lipicturecloud.application.airuntime.view.CreationTaskView;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.common.ResultUtils;
import com.li.lipicturecloud.domain.airuntime.CreationCandidate;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.model.dto.airuntime.CreationCreateRequest;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 图片炼金 HTTP 边界：故事草稿与表情草稿的创作任务生命周期。
 * 主体来自登录态，绝不信任客户端传来的 userId。
 */
@RestController
@RequestMapping(value = "/creation", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "图片炼金", description = "图片故事草稿与表情草稿的创作任务")
public class CreationController {

    private final StoryDraftService storyDraftService;
    private final EmojiDraftService emojiDraftService;
    private final UserService userService;

    public CreationController(StoryDraftService storyDraftService,
                              EmojiDraftService emojiDraftService,
                              UserService userService) {
        this.storyDraftService = storyDraftService;
        this.emojiDraftService = emojiDraftService;
        this.userService = userService;
    }

    @PostMapping("/story")
    @AuthCheck
    public BaseResponse<CreationTaskView> create(@RequestBody CreationCreateRequest body,
                                                 HttpServletRequest request) {
        return ResultUtils.success(CreationTaskView.of(storyDraftService.create(
                subject(request), body == null ? null : body.getPictureIds(),
                body == null ? null : body.getIdempotencyKey())));
    }

    @PostMapping("/story/{id}/outline")
    @AuthCheck
    public BaseResponse<CreationTaskView> outline(@PathVariable long id,
                                                  HttpServletRequest request) {
        return ResultUtils.success(CreationTaskView.of(
                storyDraftService.outline(subject(request), id)));
    }

    @PostMapping("/story/{id}/confirm-outline")
    @AuthCheck
    public BaseResponse<CreationTaskView> confirmOutline(@PathVariable long id,
                                                         HttpServletRequest request) {
        return ResultUtils.success(CreationTaskView.of(
                storyDraftService.confirmOutline(subject(request), id)));
    }

    @PostMapping("/story/{id}/draft")
    @AuthCheck
    public BaseResponse<CreationTaskView> draft(@PathVariable long id,
                                                HttpServletRequest request) {
        return ResultUtils.success(CreationTaskView.of(
                storyDraftService.draft(subject(request), id)));
    }

    @PostMapping("/story/{id}/save")
    @AuthCheck
    public BaseResponse<CreationTaskView> save(@PathVariable long id,
                                               HttpServletRequest request) {
        return ResultUtils.success(CreationTaskView.of(
                storyDraftService.save(subject(request), id)));
    }

    @GetMapping("/story")
    @AuthCheck
    public BaseResponse<List<CreationTaskView>> list(@RequestParam(defaultValue = "20") int limit,
                                                     HttpServletRequest request) {
        return ResultUtils.success(storyDraftService.list(subject(request), limit).stream()
                .map(CreationTaskView::of).toList());
    }

    // ===== 表情草稿 =====

    @PostMapping("/emoji")
    @AuthCheck
    public BaseResponse<CreationTaskView> createEmoji(@RequestBody CreationCreateRequest body,
                                                      HttpServletRequest request) {
        return ResultUtils.success(CreationTaskView.of(emojiDraftService.create(
                subject(request), body == null ? null : body.getPictureIds(),
                body == null ? null : body.getIdempotencyKey())));
    }

    @PostMapping("/emoji/{id}/generate")
    @AuthCheck
    public BaseResponse<CreationTaskView> generateEmoji(@PathVariable long id,
                                                        HttpServletRequest request) {
        return ResultUtils.success(CreationTaskView.of(
                emojiDraftService.generate(subject(request), id)));
    }

    @GetMapping("/emoji/{id}/candidates")
    @AuthCheck
    public BaseResponse<List<Map<String, Object>>> emojiCandidates(@PathVariable long id,
                                                                   HttpServletRequest request) {
        return ResultUtils.success(emojiDraftService.candidates(subject(request), id).stream()
                .map(candidate -> Map.<String, Object>of("seq", candidate.seq(),
                        "text", candidate.text()))
                .toList());
    }

    @PostMapping("/emoji/{id}/select")
    @AuthCheck
    public BaseResponse<CreationTaskView> selectEmoji(@PathVariable long id,
                                                      @RequestBody Map<String, Integer> body,
                                                      HttpServletRequest request) {
        return ResultUtils.success(CreationTaskView.of(emojiDraftService.select(
                subject(request), id, body == null || body.get("index") == null
                        ? -1 : body.get("index"))));
    }

    @PostMapping("/emoji/{id}/save")
    @AuthCheck
    public BaseResponse<CreationTaskView> saveEmoji(@PathVariable long id,
                                                    HttpServletRequest request) {
        return ResultUtils.success(CreationTaskView.of(
                emojiDraftService.save(subject(request), id)));
    }

    @GetMapping("/emoji")
    @AuthCheck
    public BaseResponse<List<CreationTaskView>> listEmoji(@RequestParam(defaultValue = "20") int limit,
                                                          HttpServletRequest request) {
        return ResultUtils.success(emojiDraftService.list(subject(request), limit).stream()
                .map(CreationTaskView::of).toList());
    }

    private AuthorizationSubject subject(HttpServletRequest request) {
        User loginUser = userService.getLoginUserEntity(request);
        return userService.isAdmin(loginUser)
                ? AuthorizationSubject.platformAdmin(loginUser.getId())
                : AuthorizationSubject.user(loginUser.getId());
    }
}
