package com.li.lipicturecloud.aop;

import com.li.lipicturecloud.annotation.SpacePermission;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class SpacePermissionInterceptor {

    private final SpaceAuthorizationAccessService accessService;
    private final ExpressionParser parser = new SpelExpressionParser();

    public SpacePermissionInterceptor(SpaceAuthorizationAccessService accessService) {
        this.accessService = accessService;
    }

    @Around("@annotation(permission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, SpacePermission permission) throws Throwable {
        StandardEvaluationContext context = new StandardEvaluationContext();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }

        Long spaceId = evaluateId(permission.spaceId(), context);
        Long pictureId = evaluateId(permission.pictureId(), context);
        Long spaceUserId = evaluateId(permission.spaceUserId(), context);
        int resourceCount = countPresent(spaceId, pictureId, spaceUserId);
        if (resourceCount != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "权限注解必须且只能指定一种资源");
        }

        accessService.check(permission.value(), spaceId, pictureId, spaceUserId, currentRequest(args));
        return joinPoint.proceed();
    }

    private Long evaluateId(String expression, StandardEvaluationContext context) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        return parser.parseExpression(expression).getValue(context, Long.class);
    }

    private int countPresent(Long... ids) {
        int count = 0;
        for (Long id : ids) {
            if (id != null) {
                count++;
            }
        }
        return count;
    }

    private HttpServletRequest currentRequest(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest request) {
                return request;
            }
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法获取当前 HTTP 请求");
    }
}
