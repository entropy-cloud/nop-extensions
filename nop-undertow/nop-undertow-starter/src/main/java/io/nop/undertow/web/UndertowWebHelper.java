package io.nop.undertow.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
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

    public static <T> T consumeFormData(HttpServerExchange exchange, FormDataConsumer<T> consumer) throws IOException {
        FormData formData = exchange.getAttachment(FormDataParser.FORM_DATA);

        Map<String, String> formParams = new HashMap<>();
        List<FormData.FormValue> formFileList = new ArrayList<>();

        for (String name : formData) {
            for (FormData.FormValue formValue : formData.get(name)) {
                if (formValue.isFileItem()) {
                    formFileList.add(formValue);
                } else {
                    formParams.put(name, formValue.getValue());
                }
            }
        }

        // URL 查询参数优先于表单参数
        formParams.putAll(getQueryParams(exchange));

        return consumer.consume(formFileList, formParams);
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

    public static void send(HttpServerExchange exchange, Map<String, Object> headers, Object body, int status) {
        setResponseHeader(exchange, headers);

        exchange.setStatusCode(status);

        if (body instanceof IResource) {
            InputStream is = ((IResource) body).getInputStream();
            try {
                send(exchange, is);
            } finally {
                IoHelper.safeCloseObject(is);
            }
        } else if (body instanceof File) {
            InputStream is = null;
            try {
                is = new FileInputStream((File) body);

                send(exchange, is);
            } catch (Exception e) {
                throw NopException.adapt(e);
            } finally {
                IoHelper.safeClose(is);
            }
        } else if (body instanceof byte[]) {
            send(exchange, (byte[]) body);
        } else if (body instanceof InputStream) {
            send(exchange, (InputStream) body);
        } else if (body instanceof String) {
            send(exchange, (String) body);
        } else if (body != null) {
            send(exchange, "INVALID CONTENT TYPE");
        }
    }

    public interface FormDataConsumer<T> {

        T consume(List<FormData.FormValue> files, Map<String, String> params) throws IOException;
    }
}
