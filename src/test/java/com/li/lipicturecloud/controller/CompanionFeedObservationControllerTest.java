package com.li.lipicturecloud.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.lipicturecloud.annotation.AuthCheck;
import com.li.lipicturecloud.aop.AuthInterceptor;
import com.li.lipicturecloud.application.companion.observation.CompanionFeedObservationService;
import com.li.lipicturecloud.application.companion.observation.view.CompanionFeedRunDetailView;
import com.li.lipicturecloud.application.companion.observation.view.CompanionFeedRunSummaryView;
import com.li.lipicturecloud.common.BaseResponse;
import com.li.lipicturecloud.constant.UserConstant;
import com.li.lipicturecloud.model.dto.companion.CompanionFeedObservationQueryRequest;
import com.li.lipicturecloud.model.vo.UserVO;
import com.li.lipicturecloud.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionFeedObservationControllerTest {

    private final CompanionFeedObservationService service = mock(CompanionFeedObservationService.class);
    private final CompanionFeedObservationController controller =
            new CompanionFeedObservationController(service);

    @Test
    void pageDelegatesQueryAndWrapsTheResult() {
        CompanionFeedObservationQueryRequest query = new CompanionFeedObservationQueryRequest();
        IPage<CompanionFeedRunSummaryView> page = new Page<>(1, 10);
        when(service.page(query)).thenReturn(page);

        BaseResponse<IPage<CompanionFeedRunSummaryView>> response = controller.page(query);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isSameAs(page);
        verify(service).page(query);
    }

    @Test
    void detailDelegatesRunIdAndWrapsTheResult() {
        CompanionFeedRunDetailView detail = mock(CompanionFeedRunDetailView.class);
        when(service.detail(123L)).thenReturn(detail);

        BaseResponse<CompanionFeedRunDetailView> response = controller.detail(123L);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isSameAs(detail);
        verify(service).detail(123L);
    }

    @Test
    void endpointsRequireAdministratorRole() throws Exception {
        Method page = CompanionFeedObservationController.class
                .getMethod("page", CompanionFeedObservationQueryRequest.class);
        Method detail = CompanionFeedObservationController.class
                .getMethod("detail", long.class);

        assertThat(page.getAnnotation(AuthCheck.class).mustRole()).isEqualTo(UserConstant.ADMIN_ROLE);
        assertThat(detail.getAnnotation(AuthCheck.class).mustRole()).isEqualTo(UserConstant.ADMIN_ROLE);
    }

    @Test
    void ordinaryUserIsRejectedByTheEndpointAuthorizationAspect() throws Throwable {
        UserService userService = mock(UserService.class);
        UserVO ordinaryUser = new UserVO();
        ordinaryUser.setUserRole("user");
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(userService.getLoginUser(request)).thenReturn(ordinaryUser);

        AuthInterceptor interceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "userService", userService);
        var joinPoint = mock(org.aspectj.lang.ProceedingJoinPoint.class);
        AuthCheck authCheck = CompanionFeedObservationController.class
                .getMethod("page", CompanionFeedObservationQueryRequest.class)
                .getAnnotation(AuthCheck.class);

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThatThrownBy(() -> interceptor.doInterceptor(joinPoint, authCheck))
                    .isInstanceOf(com.li.lipicturecloud.exception.BusinessException.class);
            verify(joinPoint, never()).proceed();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
