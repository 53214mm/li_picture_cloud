package com.li.lipicturecloud.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个接口需要具备哪项“空间资源权限”。
 *
 * <p>这份注解只负责描述两个问题：</p>
 * <ol>
 *     <li>用户想做什么，例如 {@code picture:edit}；</li>
 *     <li>用户正在操作哪个资源，例如某个空间、图片或空间成员关系。</li>
 * </ol>
 *
 * <p>资源字段使用 Spring Expression Language（SpEL）从方法参数中取值。
 * AOP 拦截器会把第一个参数注册为 {@code #p0} 和 {@code #a0}，第二个参数注册为
 * {@code #p1} 和 {@code #a1}，以此类推。例如：</p>
 *
 * <pre>{@code
 * @SpacePermission(
 *     value = "spaceUser:manage",
 *     spaceId = "#p0.spaceId"
 * )
 * public void addMember(SpaceUserAddRequest request) { ... }
 * }</pre>
 *
 * <p>上面的 {@code #p0.spaceId} 等价于从第一个方法参数中读取
 * {@code request.getSpaceId()}。三种资源表达式必须且只能填写一种，避免授权系统
 * 无法判断本次校验究竟针对空间、图片还是成员关系。</p>
 *
 * @see com.li.lipicturecloud.aop.SpacePermissionInterceptor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SpacePermission {

    /**
     * 接口要求的权限码，例如 {@code picture:view}、{@code picture:edit}。
     */
    String value();

    /**
     * 从方法参数中解析空间 ID 的 SpEL 表达式。
     */
    String spaceId() default "";

    /**
     * 从方法参数中解析图片 ID 的 SpEL 表达式。授权服务会继续查询图片所属空间。
     */
    String pictureId() default "";

    /**
     * 从方法参数中解析空间成员关系 ID 的 SpEL 表达式。授权服务会继续查询其所属空间。
     */
    String spaceUserId() default "";
}
