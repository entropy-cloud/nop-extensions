package io.nop.solon.web;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.OrderedComparator;
import io.nop.http.api.server.HttpServerHelper;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.http.api.server.IHttpServerFilter;
import org.noear.solon.annotation.Component;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Filter;
import org.noear.solon.core.handle.FilterChain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class SolonHttpServerFilter implements Filter {

    private List<IHttpServerFilter> filters;

    public synchronized List<IHttpServerFilter> getFilters(boolean sys) {
        if (filters == null) {
            filters = new ArrayList<>(
                    BeanContainer.instance().getBeansOfType(IHttpServerFilter.class).values());
            Collections.sort(filters, OrderedComparator.instance());
        }
        return filters;

        /*
        return filters.stream().filter(filter -> {
            boolean high = filter.order() < IHttpServerFilter.NORMAL_PRIORITY;
            return sys == high;
        }).collect(Collectors.toList());
        */
    }

    @Override
    public void doFilter(Context context, FilterChain chain) throws Throwable {
        List<IHttpServerFilter> serverFilters = getFilters(false);

        if (serverFilters.isEmpty()) {
            chain.doFilter(context);
        } else {
            IHttpServerContext ctx = new SolonServerContext(context);
            HttpServerHelper.runWithFilters(serverFilters, ctx, () -> {
                return FutureHelper.futureCall(() -> {
                    try {
                        chain.doFilter(context);
                        return null;
                    } catch (Error e) {
                        throw e;
                    } catch (Throwable e) {
                        throw NopException.adapt(e);
                    }
                });
            });
        }
    }
}
