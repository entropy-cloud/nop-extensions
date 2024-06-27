package io.nop.undertow.service;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-05-05
 */
public class UndertowContext {
    private static final Logger LOG = LoggerFactory.getLogger(UndertowContext.class);

    private static final ThreadLocal<HttpServerExchange> currentExchange = new InheritableThreadLocal<>();

    public static HttpServerExchange getExchange() {
        return currentExchange.get();
    }

    /**
     * 在当前线程及其子线程内可以通过 {@link #getExchange()} 得到 {@link HttpServerExchange}，
     * 而在线程池内无法自动传递该变量，需自行处理
     */
    public static CompletionStage<Void> withExchange(
            HttpServerExchange exchange, Supplier<CompletionStage<Void>> supplier
    ) {
        currentExchange.set(exchange);

        return supplier.get().whenComplete((r, e) -> {
            if (e != null) {
                LOG.error("nop.extension.undertow.unhandled-error", e);
            }
            currentExchange.remove();
        });
    }
}
