package io.nop.undertow.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.file.core.AbstractGraphQLFileService;
import io.nop.file.core.DownloadRequestBean;
import io.nop.file.core.FileConstants;
import io.nop.file.core.UploadRequestBean;
import io.nop.graphql.core.utils.GraphQLResponseHelper;
import io.nop.undertow.handler.FutureHttpHandler;
import io.nop.undertow.handler.FutureHttpHandlerHelper;
import io.nop.undertow.web.UndertowWebHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.EagerFormParsingHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.util.Headers;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-06-23
 */
public class UndertowFileHandler extends AbstractGraphQLFileService implements FutureHttpHandler {
    private final static Pattern DOWNLOAD_URL_MATCHER = Pattern.compile("^"
                                                                        + FileConstants.PATH_DOWNLOAD
                                                                        + "/([^/\\\\]+)$");

    private final HttpHandler next;

    public UndertowFileHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public CompletionStage<Void> handleRequestAsync(HttpServerExchange exchange) {
        String path = exchange.getRequestPath();
        String method = exchange.getRequestMethod().toString();

        if (FileConstants.PATH_UPLOAD.equals(path) && "POST".equalsIgnoreCase(method)) {
            return handleUpload(exchange);
        }

        Matcher matcher = DOWNLOAD_URL_MATCHER.matcher(path);
        if (matcher.matches()) {
            String fileId = matcher.group(1);
            String contentType = UndertowWebHelper.getQueryParam(exchange, "contentType");

            return handleDownload(exchange, fileId, contentType);
        }

        return FutureHttpHandlerHelper.handleRequest(this.next, exchange);
    }

    private CompletionStage<Void> handleUpload(HttpServerExchange exchange) {
        try {
            CompletableFuture<Void> promise = new CompletableFuture<>();

            // https://stackoverflow.com/questions/37839418/multipart-form-data-example-using-undertow#answer-46374193
            new EagerFormParsingHandler((ex) -> {
                doHandleUpload(ex).whenComplete((r, e) -> FutureHelper.complete(promise, r, e));
            }).handleRequest(exchange);

            return promise;
        } catch (Exception e) {
            return FutureHelper.reject(e);
        }
    }

    private CompletionStage<Void> doHandleUpload(HttpServerExchange exchange) {
        String locale = ContextProvider.currentLocale();

        CompletionStage<ApiResponse<?>> future;
        try {
            future = UndertowWebHelper.consumeFormData(exchange, (files, params) -> {
                // https://github.com/undertow-io/undertow/blob/main/core/src/test/java/io/undertow/server/handlers/form/MultipartFormDataParserTestCase.java
                FormData.FormValue formFile = files.get(0);
                String contentType = formFile.getHeaders().getFirst(Headers.CONTENT_TYPE);

                InputStream is = formFile.getFileItem().getInputStream();
                String fileName = StringHelper.fileFullName(formFile.getFileName());
                long fileSize = formFile.getFileItem().getFileSize();

                UploadRequestBean req = buildUploadRequestBean(is, fileName, fileSize, contentType, params::get);

                return uploadAsync(buildApiRequest(exchange, req));
            });
        } catch (IOException e) {
            future = FutureHelper.success(ErrorMessageManager.instance().buildResponse(locale, e));
        }

        return future.thenApply(resp -> sendJsonData(exchange, resp));
    }

    private CompletionStage<Void> handleDownload(HttpServerExchange exchange, String fileId, String contentType) {
        DownloadRequestBean req = buildDownloadRequestBean(fileId, contentType);

        return downloadAsync(buildApiRequest(exchange, req)).thenApply(res -> {
            if (!res.isOk()) {
                return sendJsonData(exchange, res);
            } else {
                return sendFileData(exchange, res);
            }
        });
    }

    protected <T> ApiRequest<T> buildApiRequest(HttpServerExchange exchange, T data) {
        return buildApiRequest(data, (header) -> {
            exchange.getRequestHeaders().forEach(h -> {
                String name = h.getHeaderName().toString();
                header.accept(name, h.getFirst());
            });
        });
    }

    /** Note：输出可能是异步的，需直接传递 exchange 以避免在先线程中无法通过 {@link UndertowContext#getExchange()} 获取的问题 */
    public Void sendJsonData(HttpServerExchange exchange, ApiResponse<?> response) {
        return GraphQLResponseHelper.consumeJsonResponse(response, (invokeHeaderSet, body, status) -> {
            Map<String, Object> headers = new HashMap<>();
            invokeHeaderSet.accept(headers::put);

            UndertowWebHelper.send(exchange, headers, body, status);

            return null;
        });
    }

    /** Note：输出可能是异步的，需直接传递 exchange 以避免在先线程中无法通过 {@link UndertowContext#getExchange()} 获取的问题 */
    public Void sendFileData(HttpServerExchange exchange, ApiResponse<WebContentBean> response) {
        return GraphQLResponseHelper.consumeWebContent(response, (invokeHeaderSet, content, status) -> {
            Map<String, Object> headers = new HashMap<>();
            invokeHeaderSet.accept(headers::put);

            UndertowWebHelper.send(exchange, headers, content, status);

            return null;
        });
    }
}
