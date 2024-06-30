package io.nop.undertow.handler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import io.nop.api.core.util.FutureHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-06-27
 */
public class FutureHttpHandlerHelper {

    /** 统一调用普通和异步 {@link HttpHandler} */
    public static CompletionStage<Void> handleRequest(HttpHandler handler, HttpServerExchange exchange) {
        if (handler instanceof FutureHttpHandler) {
            return ((FutureHttpHandler) handler).handleRequestAsync(exchange);
        }

        try {
            handler.handleRequest(exchange);

            return FutureHelper.success(null);
        } catch (Exception e) {
            return FutureHelper.reject(e);
        }
    }

    /** 将仅做前处理的 {@link HttpHandler} 转换为 {@link FutureHttpHandler}，其必然会调用其后继处理器，并且不做后处理 */
    public static FutureHttpHandler withFuture(
            Function<HttpHandler, HttpHandler> handlerCreator, FutureHttpHandler next
    ) {
        return exchange -> {
            CompletableFuture<Void> promise = new CompletableFuture<>();

            HttpHandler handler = handlerCreator.apply(ex -> {
                //
                next.handleRequestAsync(ex).whenComplete((r, e) -> FutureHelper.complete(promise, r, e));
            });
            handleRequest(handler, exchange);

            return promise;
        };
    }
}
