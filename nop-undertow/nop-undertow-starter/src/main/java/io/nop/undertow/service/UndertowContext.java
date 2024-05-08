package io.nop.undertow.service;

import io.undertow.server.HttpServerExchange;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-05-05
 */
public class UndertowContext {
    private static final ThreadLocal<HttpServerExchange> currentExchange = new ThreadLocal<>();

    public static HttpServerExchange getExchange() {
        return currentExchange.get();
    }

    public static void withExchange(HttpServerExchange exchange, Callback cb) throws Exception {
        currentExchange.set(exchange);
        try {
            cb.call();
        } finally {
            currentExchange.remove();
        }
    }

    public interface Callback {
        void call() throws Exception;
    }
}
