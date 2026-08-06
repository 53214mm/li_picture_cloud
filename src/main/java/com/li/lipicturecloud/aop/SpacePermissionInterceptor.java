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

/**
 * {@link SpacePermission} 的 AOP 执行器。
 *
 * <p>可以把它理解为权限链的“参数翻译层”：</p>
 * <ol>
 *     <li>拦截带有 {@code @SpacePermission} 的方法；</li>
 *     <li>把方法参数注册为 SpEL 变量；</li>
 *     <li>从注解表达式中计算出一个资源 ID；</li>
 *     <li>把权限码、资源 ID 和当前请求交给统一授权服务；</li>
 *     <li>校验通过后，才执行原来的 Controller 方法。</li>
 * </ol>
 *
 * <p>本类不负责判断 viewer 能否编辑。角色和权限的业务规则集中在
 * {@link com.li.lipicturecloud.manager.auth.SpaceAuthorizationManager} 中。</p>
 */
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
        // SpEL 需要一个求值上下文。下面会把被拦截方法的实参放入这个上下文。
        StandardEvaluationContext context = new StandardEvaluationContext();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            // Spring 常用 #p0、#a0 表示第一个参数；同时注册两种名称，注解编写者都可以使用。
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
        }

        // 注解只保存表达式字符串；这里才真正执行表达式并取得 Long 类型的业务 ID。
        Long spaceId = evaluateId(permission.spaceId(), context);
        Long pictureId = evaluateId(permission.pictureId(), context);
        Long spaceUserId = evaluateId(permission.spaceUserId(), context);

        // 一次校验只能指向一种资源。否则授权服务无法确定应该按哪条资源链查询归属关系。
        int resourceCount = countPresent(spaceId, pictureId, spaceUserId);
        if (resourceCount != 1) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "权限注解必须且只能指定一种资源");
        }

        // AccessService 负责解析资源归属、计算权限集合并在权限不足时抛出异常。
        accessService.check(permission.value(), spaceId, pictureId, spaceUserId, currentRequest(args));

        // 能走到这里说明校验已经通过；proceed() 才会真正调用原来的 Controller 方法。
        return joinPoint.proceed();
    }

    /**
     * 计算一个资源表达式。空字符串代表本次没有选择这种资源，因此返回 {@code null}。
     */
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

    /**
     * 优先复用方法参数中的 request；没有显式传入时，再从当前请求线程中获取。
     * AOP 调用仍发生在 HTTP 请求线程内，因此 RequestContextHolder 可以找到对应请求。
     */
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
