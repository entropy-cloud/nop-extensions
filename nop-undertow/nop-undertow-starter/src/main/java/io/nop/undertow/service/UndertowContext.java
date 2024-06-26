package io.nop.undertow.service;

import java.util.concurrent.CompletionStage;

import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-05-05
 */
public class UndertowContext {
    private static final Logger LOG = LoggerFactory.getLogger(UndertowContext.class);

    private static final ThreadLocal<HttpServerExchange> currentExchange = new ThreadLocal<>();

    public static HttpServerExchange getExchange() {
        return currentExchange.get();
    }

    public static void withExchange(HttpServerExchange exchange, Callback cb) throws Exception {
        currentExchange.set(exchange);

        try {
            cb.call().whenComplete((r, e) -> {
                LOG.error("nop.extension.undertow.unhandled-error", e);
                currentExchange.remove();
            });
        } catch (Exception e) {
            currentExchange.remove();
            throw e;
        }
    }

    public interface Callback {

        CompletionStage<Void> call() throws Exception;
    }
}
