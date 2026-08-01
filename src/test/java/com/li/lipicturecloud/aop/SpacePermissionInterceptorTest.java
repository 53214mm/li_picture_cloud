package com.li.lipicturecloud.aop;

import com.li.lipicturecloud.annotation.SpacePermission;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SpacePermissionInterceptorTest {

    @Test
    void extractsResourceIdBeforeProceeding() throws Throwable {
        SpaceAuthorizationAccessService service = mock(SpaceAuthorizationAccessService.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        Request dto = new Request(42L);
        when(joinPoint.getArgs()).thenReturn(new Object[]{dto, request});
        when(joinPoint.proceed()).thenReturn("ok");
        SpacePermission annotation = Fixture.class.getDeclaredMethod("handle", Request.class, HttpServletRequest.class)
                .getAnnotation(SpacePermission.class);

        Object result = new SpacePermissionInterceptor(service).checkPermission(joinPoint, annotation);

        assertThat(result).isEqualTo("ok");
        verify(service).check("spaceUser:manage", 42L, null, null, request);
    }

    @Test
    void rejectsAnnotationWithoutExactlyOneResource() throws Exception {
        SpaceAuthorizationAccessService service = mock(SpaceAuthorizationAccessService.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{request});
        SpacePermission annotation = Fixture.class.getDeclaredMethod("invalid", HttpServletRequest.class)
                .getAnnotation(SpacePermission.class);

        assertThatThrownBy(() -> new SpacePermissionInterceptor(service).checkPermission(joinPoint, annotation))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能指定一种资源");
        verifyNoInteractions(service);
    }

    record Request(Long spaceId) {}

    static class Fixture {
        @SpacePermission(value = "spaceUser:manage", spaceId = "#p0.spaceId")
        void handle(Request request, HttpServletRequest servletRequest) {}

        @SpacePermission("space:view")
        void invalid(HttpServletRequest request) {}
    }
}
