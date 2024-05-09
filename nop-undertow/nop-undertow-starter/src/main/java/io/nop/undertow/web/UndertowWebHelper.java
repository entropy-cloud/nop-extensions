package io.nop.undertow.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-05-06
 */
public class UndertowWebHelper {

    public static String getQueryParam(HttpServerExchange exchange, String name) {
        Deque<String> value = exchange.getQueryParameters().get(name);

        return value != null ? value.getFirst() : null;
    }

    public static Map<String, String> getQueryParams(HttpServerExchange exchange) {
        Map<String, String> params = new LinkedHashMap<>();

        exchange.getQueryParameters().forEach((name, value) -> {
            name = StringHelper.replace(name, "%40", "@");

            params.put(name, value.getFirst());
        });

        return params;
    }

    public static Map<String, Object> getRequestHeaders(HttpServerExchange exchange) {
        Map<String, Object> headers = new LinkedHashMap<>();

        exchange.getRequestHeaders()
                .forEach((header) -> headers.put(header.getHeaderName().toString(), header.getFirst()));

        return headers;
    }

    public static void consumeRequestBody(HttpServerExchange exchange, Consumer<String> consumer) {
        String method = exchange.getRequestMethod().toString();

        if ("GET".equalsIgnoreCase(method)) {
            consumer.accept(null);
        } else {
            exchange.getRequestReceiver()
                    .receiveFullString((ex, body) -> consumer.accept(body), StandardCharsets.UTF_8);
        }
    }

    public static void setResponseHeader(HttpServerExchange exchange, Map<String, Object> headers) {
        if (headers == null) {
            return;
        }

        headers.forEach((name, value) -> setResponseHeader(exchange, name, ConvertHelper.toString(value)));
    }

    public static void setResponseHeader(HttpServerExchange exchange, String name, String value) {
        exchange.getResponseHeaders().add(new HttpString(name), ConvertHelper.toString(value));
    }

    public static void send(HttpServerExchange exchange, String text) {
        send(exchange, text, null);
    }

    public static void send(HttpServerExchange exchange, String text, String contentType) {
        if (contentType != null) {
            setResponseHeader(exchange, Headers.CONTENT_TYPE_STRING, contentType);
        }
        exchange.getResponseSender().send(text, StandardCharsets.UTF_8);
    }

    public static void send(HttpServerExchange exchange, byte[] bytes) {
        exchange.getResponseSender().send(ByteBuffer.wrap(bytes));
    }

    public static void send(HttpServerExchange exchange, InputStream input) {
        try {
            send(exchange, input.readAllBytes());
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }
}
