package com.li.lipicturecloud.AI.common;

import com.li.lipicturecloud.model.entity.User;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;

/**
 * AI 工具执行时的用户上下文持有者。
 * <p>
 * 通过 Reactor ContextRegistry 注册，确保跨 reactor 线程（含 ToolCallback 执行线程）的上下文传播。
 */
public class UserContextHolder {

    private static final ThreadLocal<User> CONTEXT = new ThreadLocal<>();

    static {
        // ★ 注册到 Reactor ContextRegistry，Hooks.enableAutomaticContextPropagation() 自动传播
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ThreadLocalAccessor<User>() {
            @Override
            public Object key() { return UserContextHolder.class; }
            @Override
            public User getValue() { return CONTEXT.get(); }
            @Override
            public void setValue(User value) { CONTEXT.set(value); }
            @Override
            public void restore(User previousValue) {
                if (previousValue != null) CONTEXT.set(previousValue);
                else CONTEXT.remove();
            }
            @Override
            public void reset() { CONTEXT.remove(); }
        });
    }

    public static void set(User user) {
        CONTEXT.set(user);
    }

    public static User get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
