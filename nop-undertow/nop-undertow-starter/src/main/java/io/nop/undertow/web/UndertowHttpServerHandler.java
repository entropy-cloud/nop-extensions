package io.nop.undertow.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.OrderedComparator;
import io.nop.http.api.server.HttpServerHelper;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.http.api.server.IHttpServerFilter;
import io.nop.undertow.UndertowConfigs;
import io.nop.undertow.service.UndertowGraphQLHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.PreCompressedResourceSupplier;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.server.handlers.resource.ResourceSupplier;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-05-06
 */
public class UndertowHttpServerHandler implements HttpHandler {
    private final HttpHandler handler;

    private List<IHttpServerFilter> filters;

    public UndertowHttpServerHandler() {
        this.handler = createHandler();
    }

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
        this.handler.handleRequest(exchange);
    }

    private HttpHandler createHandler() {
        ResourceManager resourceManager = new ClassPathResourceManager(getClass().getClassLoader(),
                                                                       "META-INF/resources");
        // 对包含预压缩的资源（.gz 后缀），则优先返回其对应的压缩文件
        ResourceSupplier resourceSupplier = new PreCompressedResourceSupplier(resourceManager).addEncoding("gzip",
                                                                                                           ".gz");
        HttpHandler resource = new ResourceHandler(resourceSupplier);

        // Note: ResourceHandler 会在新线程中执行后继的 HttpHandler，
        // 从而导致在 Nop ContextProvider 中与当前线程绑定的变量无法在
        // ResourceHandler 的后继中获取到，因此，必须先在当前线程中执行
        // UndertowGraphQLHandler，再将未处理的请求交给 ResourceHandler
        HttpHandler handler = new UndertowGraphQLHandler(resource);

        // 启用对响应的压缩支持
        if (UndertowConfigs.CFG_SERVER_COMPRESSION_ENABLED.get()) {
            int minResponseSize = UndertowConfigs.CFG_SERVER_COMPRESSION_MIN_RESPONSE_SIZE.get();
            Set mimeTypes = UndertowConfigs.CFG_SERVER_COMPRESSION_MIME_TYPES.get();

            ContentEncodingRepository encodingRepository = new ContentEncodingRepository();
            encodingRepository.addEncodingHandler("gzip", new GzipEncodingProvider(), 50, value -> {
                HeaderMap headers = value.getResponseHeaders();

                String length = headers.getFirst(Headers.CONTENT_LENGTH);
                String contentType = headers.getFirst(Headers.CONTENT_TYPE);

                return length != null && Long.parseLong(length) > minResponseSize //
                       && contentType != null && mimeTypes.contains(contentType);
            });

            handler = new EncodingHandler(handler, encodingRepository);
        }

        return handler;
    }
}
