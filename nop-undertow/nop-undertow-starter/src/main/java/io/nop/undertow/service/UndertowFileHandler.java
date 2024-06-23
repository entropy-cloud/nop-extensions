package io.nop.undertow.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.json.JsonTool;
import io.nop.file.core.AbstractGraphQLFileService;
import io.nop.file.core.DownloadRequestBean;
import io.nop.file.core.FileConstants;
import io.nop.file.core.MediaTypeHelper;
import io.nop.file.core.UploadRequestBean;
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
public class UndertowFileHandler extends AbstractGraphQLFileService implements HttpHandler {
    private final static Pattern DOWNLOAD_URL_MATCHER = Pattern.compile("^"
                                                                        + FileConstants.PATH_DOWNLOAD
                                                                        + "/([^/\\\\]+)$");

    private final HttpHandler next;
    private final EagerFormParsingHandler formHandler;

    public UndertowFileHandler(HttpHandler next) {
        this.next = next;
        // https://stackoverflow.com/questions/37839418/multipart-form-data-example-using-undertow#answer-46374193
        this.formHandler = new EagerFormParsingHandler(this::handleUpload);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        UndertowContext.withExchange(exchange, () -> {
            String path = exchange.getRequestPath();
            String method = exchange.getRequestMethod().toString();

            if (FileConstants.PATH_UPLOAD.equals(path) && "POST".equalsIgnoreCase(method)) {
                this.formHandler.handleRequest(exchange);
                return;
            }

            Matcher matcher = DOWNLOAD_URL_MATCHER.matcher(path);
            if (matcher.matches()) {
                String fileId = matcher.group(1);
                String contentType = UndertowWebHelper.getQueryParam(exchange, "contentType");

                handleDownload(exchange, fileId, contentType);
                return;
            }

            this.next.handleRequest(exchange);
        });
    }

    private void handleUpload(HttpServerExchange exchange) {
        String locale = ContextProvider.currentLocale();

        CompletionStage<ApiResponse<?>> future;
        try {
            future = UndertowWebHelper.consumeFormData(exchange, (files, params) -> {
                // https://github.com/undertow-io/undertow/blob/main/core/src/test/java/io/undertow/server/handlers/form/MultipartFormDataParserTestCase.java
                FormData.FormValue formFile = files.get(0);
                String contentType = formFile.getHeaders().getFirst(Headers.CONTENT_TYPE);

                InputStream is = formFile.getFileItem().getInputStream();
                String fileName = StringHelper.fileFullName(formFile.getFileName());
                String mimeType = MediaTypeHelper.getMimeType(contentType, StringHelper.fileExt(fileName));

                UploadRequestBean req = new UploadRequestBean(is,
                                                              fileName,
                                                              formFile.getFileItem().getFileSize(),
                                                              mimeType);
                req.setBizObjName(params.get(FileConstants.PARAM_BIZ_OBJ_NAME));
                req.setFieldName(params.get(FileConstants.PARAM_FIELD_NAME));

                return uploadAsync(buildApiRequest(exchange, req));
            });
        } catch (IOException e) {
            future = FutureHelper.success(ErrorMessageManager.instance().buildResponse(locale, e));
        }

        future.thenAccept(resp -> sendData(resp.getHttpStatus(), resp));
    }

    private void handleDownload(HttpServerExchange exchange, String fileId, String contentType) {
        DownloadRequestBean req = new DownloadRequestBean();
        req.setFileId(fileId);
        req.setContentType(contentType);

        CompletionStage<ApiResponse<WebContentBean>> future = downloadAsync(buildApiRequest(exchange, req));

        future.thenAccept(resp -> {
            if (!resp.isOk()) {
                int status = resp.getHttpStatus();
                if (status == 0) {
                    status = 500;
                }

                sendData(status, resp);
            } else {
                sendData(resp.getHttpStatus(), resp.getData());
            }
        });
    }

    protected <T> ApiRequest<T> buildApiRequest(HttpServerExchange exchange, T data) {
        ApiRequest<T> request = new ApiRequest<>();
        request.setData(data);

        exchange.getRequestHeaders().forEach(header -> {
            String name = header.getHeaderName().toString().toLowerCase(Locale.ENGLISH);
            if (shouldIgnoreHeader(name)) {
                return;
            }

            request.setHeader(name, header.getFirst());
        });

        return request;
    }

    public void sendData(int status, Object data) {
        HttpServerExchange exchange = UndertowContext.getExchange();

        if (status == 0) {
            status = 200;
        }

        Map<String, Object> headers = new HashMap<>();

        Object body;
        if (data instanceof WebContentBean) {
            WebContentBean contentBean = (WebContentBean) data;

            headers.put(ApiConstants.HEADER_CONTENT_TYPE, contentBean.getContentType());

            if (!StringHelper.isEmpty(contentBean.getFileName())) {
                String encoded = StringHelper.encodeURL(contentBean.getFileName());
                headers.put("Content-Disposition", "attachment; filename=" + encoded);
            }

            body = contentBean.getContent();
        } else {
            body = JsonTool.stringify(data);
        }

        UndertowWebHelper.send(exchange, headers, body, status);
    }
}
