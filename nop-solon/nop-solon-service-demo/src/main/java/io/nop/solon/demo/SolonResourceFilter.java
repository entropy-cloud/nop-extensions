package io.nop.solon.demo;

import org.noear.solon.annotation.Component;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Filter;
import org.noear.solon.core.handle.FilterChain;

@Component
public class SolonResourceFilter implements Filter {
    @Override
    public void doFilter(Context ctx, FilterChain chain) throws Throwable {
        if ("/".equals(ctx.pathNew())) { //ContextPathFilter 就是类似原理实现的
            ctx.pathNew("/index.html");
        }

        chain.doFilter(ctx);
    }
}