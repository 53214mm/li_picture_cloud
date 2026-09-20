package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.application.airuntime.FusionImageService;
import com.li.lipicturecloud.application.airuntime.view.FusionImageView;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 融合结果预览二进制端点：与 JSON 端点分开，按图片 MIME 返回暂存字节。
 * 登录态会话校验所有权；响应禁止缓存。
 */
@RestController
@RequestMapping("/creation/fusion")
@Tag(name = "图片炼金", description = "多图融合结果预览")
public class CreationFusionPreviewController {

    private final FusionImageService fusionImageService;
    private final UserService userService;

    public CreationFusionPreviewController(FusionImageService fusionImageService,
                                           UserService userService) {
        this.fusionImageService = fusionImageService;
        this.userService = userService;
    }

    @GetMapping("/{id}/preview")
    @AuthCheck
    public ResponseEntity<byte[]> preview(@PathVariable long id, HttpServletRequest request) {
        FusionImageView view = fusionImageService.previewImage(subject(request), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(view.mimeType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(view.bytes());
    }

    private AuthorizationSubject subject(HttpServletRequest request) {
        User loginUser = userService.getLoginUserEntity(request);
        return userService.isAdmin(loginUser)
                ? AuthorizationSubject.platformAdmin(loginUser.getId())
                : AuthorizationSubject.user(loginUser.getId());
    }
}
