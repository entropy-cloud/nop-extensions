package io.nop.undertow.web;

import java.util.ArrayList;
import java.util.List;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.OrderedComparator;
import io.nop.http.api.server.HttpServerHelper;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.http.api.server.IHttpServerFilter;
import io.nop.undertow.service.UndertowGraphQLHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.PreCompressedResourceSupplier;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.server.handlers.resource.ResourceSupplier;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-05-06
 */
public class UndertowHttpServerHandler implements HttpHandler {
    private List<IHttpServerFilter> filters;

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        List<IHttpServerFilter> serverFilters = getFilters();

        if (serverFilters.isEmpty()) {
            next(exchange);
        } else {
            IHttpServerContext ctx = new UndertowHttpServerContext(exchange);

            HttpServerHelper.runWithFilters(serverFilters, ctx, () -> FutureHelper.futureCall(() -> {
                try {
                    next(exchange);
                    return null;
                } catch (Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw NopException.adapt(e);
                }
            }));
        }
    }

    public synchronized List<IHttpServerFilter> getFilters() {
        if (this.filters == null) {
            this.filters = new ArrayList<>(BeanContainer.instance().getBeansOfType(IHttpServerFilter.class).values());
            this.filters.sort(OrderedComparator.instance());
        }
        return this.filters;
    }

    protected void next(HttpServerExchange exchange) throws Exception {
        ResourceManager resourceManager = new ClassPathResourceManager(getClass().getClassLoader(),
                                                                       "META-INF/resources");
        ResourceSupplier resourceSupplier = new PreCompressedResourceSupplier(resourceManager).addEncoding("gzip",
                                                                                                           ".gz");
        HttpHandler resource = new ResourceHandler(resourceSupplier);

        // Note: ResourceHandler 会在新线程中执行后继的 HttpHandler，
        // 从而导致在 Nop ContextProvider 中与当前线程绑定的变量无法在
        // ResourceHandler 的后继中获取到，因此，必须先在当前线程中执行
        // UndertowGraphQLHandler，再将未处理的请求交给 ResourceHandler
        HttpHandler graphql = new UndertowGraphQLHandler(resource);
        graphql.handleRequest(exchange);
    }
}
