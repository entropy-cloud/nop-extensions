package io.nop.undertow.web;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.OrderedComparator;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.server.HttpServerHelper;
import io.nop.http.api.server.IHttpServerContext;
import io.nop.http.api.server.IHttpServerFilter;
import io.nop.undertow.UndertowConfigs;
import io.nop.undertow.handler.FutureHttpHandler;
import io.nop.undertow.handler.FutureHttpHandlerHelper;
import io.nop.undertow.service.UndertowContext;
import io.nop.undertow.service.UndertowFileHandler;
import io.nop.undertow.service.UndertowGraphQLHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.PreCompressedResourceSupplier;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.server.handlers.resource.ResourceSupplier;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;

/**
 * Undertow 服务请求的主处理器，由其调用 {@link IHttpServerFilter}、{@link UndertowGraphQLHandler}
 * 和 {@link UndertowFileHandler}
 *
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

            HttpServerHelper.runWithFilters(serverFilters, ctx, () -> next(exchange));
        }
    }

    public synchronized List<IHttpServerFilter> getFilters() {
        if (this.filters == null) {
            this.filters = new ArrayList<>(BeanContainer.instance().getBeansOfType(IHttpServerFilter.class).values());
            this.filters.sort(OrderedComparator.instance());
        }
        return this.filters;
    }

    /**
     * 在当前线程及其子线程内的 {@link HttpHandler} 均可通过
     * {@link UndertowContext#getExchange()} 获取到 {@link HttpServerExchange}
     */
    protected CompletionStage<Void> next(HttpServerExchange exchange) {
        return UndertowContext.withExchange(exchange,
                                            () -> FutureHttpHandlerHelper.handleRequest(this.handler, exchange));
    }

    private HttpHandler createHandler() {
        String staticDir = UndertowConfigs.CFG_SERVER_STATIC_DIR.get();
        List<ResourceManager> resourceManagers = Arrays.asList(
                // 外部资源优先
                StringHelper.isEmpty(staticDir) ? null : new FileResourceManager(new File(staticDir)),
                new ClassPathResourceManager(getClass().getClassLoader(), "META-INF/resources")
                //
        );

        ResourceSupplier resourceSupplier = new CompositeResourceSupplier(resourceManagers);
        HttpHandler resourceHandler = new ResourceHandler(resourceSupplier);

        // Note: ResourceHandler 会在新线程中执行后继的 HttpHandler，
        // 从而导致在 Nop ContextProvider 中与当前线程绑定的变量无法在
        // ResourceHandler 的后继中获取到，因此，必须先在当前线程中执行
        // UndertowGraphQLHandler，再将未处理的请求交给 ResourceHandler
        FutureHttpHandler handler = new UndertowGraphQLHandler(resourceHandler);

        // 若运行环境引入了 nop-file，则启用文件上传和下载支持。
        // Note: Undertow 没有扫描和自动注册机制，只能通过环境中是否存在特定的 class
        // 来判断是否启用文件上传/下载能力
        try {
            ClassHelper.forName("io.nop.file.core.AbstractGraphQLFileService");

            handler = new UndertowFileHandler(handler);
        } catch (ClassNotFoundException ignore) {
        }

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

            handler = FutureHttpHandlerHelper.withFuture((h) -> new EncodingHandler(h, encodingRepository), handler);
        }

        return handler;
    }

    private static class CompositeResourceSupplier implements ResourceSupplier {
        private final ResourceSupplier[] suppliers;

        CompositeResourceSupplier(List<ResourceManager> managers) {
            this.suppliers = managers.stream().filter(Objects::nonNull).map((manager) -> {
                // 若包含预压缩的资源（{@code .gz} 后缀），则优先返回其对应的压缩文件
                return new PreCompressedResourceSupplier(manager).addEncoding("gzip", ".gz");
            }).toArray(ResourceSupplier[]::new);
        }

        @Override
        public Resource getResource(HttpServerExchange exchange, String path) throws IOException {
            for (ResourceSupplier supplier : this.suppliers) {
                Resource resource = supplier.getResource(exchange, path);

                if (resource != null) {
                    return resource;
                }
            }
            return null;
        }
    }
}
