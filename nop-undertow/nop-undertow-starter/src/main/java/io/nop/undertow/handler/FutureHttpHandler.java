package io.nop.undertow.handler;

import java.util.concurrent.CompletionStage;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * 用于支持返回 {@link CompletionStage} 异步回调的 {@link HttpHandler}
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-06-27
 */
public interface FutureHttpHandler extends HttpHandler {

    @Override
    default void handleRequest(HttpServerExchange exchange) throws Exception {
        throw new UnsupportedOperationException("Please call #handleRequestAsync(exchange) for "
                                                + getClass().getSimpleName());
    }

    CompletionStage<Void> handleRequestAsync(HttpServerExchange exchange);
}
