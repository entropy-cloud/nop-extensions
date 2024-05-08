package io.nop.undertow.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.web.GraphQLWebService;
import io.nop.http.api.HttpStatus;
import io.nop.undertow.web.UndertowWebHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_ARGS;
import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_SELECTION;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-05-06
 */
public class UndertowGraphQLHandler extends GraphQLWebService implements HttpHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UndertowGraphQLHandler.class);

    private final static Pattern REST_URL_MATCHER = Pattern.compile("^/r/([^/\\\\]+)$");
    private final static Pattern PAGE_QUERY_URL_MATCHER = Pattern.compile("^/p/(.+)$");

    private final HttpHandler next;

    public UndertowGraphQLHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    protected Map<String, String> getParams() {
        HttpServerExchange exchange = UndertowContext.getExchange();

        return UndertowWebHelper.getQueryParams(exchange);
    }

    @Override
    protected Map<String, Object> getHeaders() {
        HttpServerExchange exchange = UndertowContext.getExchange();

        return UndertowWebHelper.getRequestHeaders(exchange);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        UndertowContext.withExchange(exchange, () -> {
            String path = exchange.getRequestPath();
            String method = exchange.getRequestMethod().toString();

            if ("/graphql".equals(path) && "POST".equalsIgnoreCase(method)) {
                handleGraphQL(exchange);
                return;
            }

            Matcher matcher = REST_URL_MATCHER.matcher(path);
            if (matcher.matches()) {
                String operationName = matcher.group(1);
                handleRest(exchange, operationName);
                return;
            }

            matcher = PAGE_QUERY_URL_MATCHER.matcher(path);
            if (matcher.matches()) {
                String query = matcher.group(1);
                handlePageQuery(exchange, method, query);
                return;
            }

            this.next.handleRequest(exchange);
        });
    }

    protected void handleGraphQL(HttpServerExchange exchange) {
        UndertowWebHelper.consumeRequestBody(exchange, (body) -> {
            String result = FutureHelper.syncGet(runGraphQL(body, this::transformGraphQLResponse));

            exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            exchange.setStatusCode(HttpStatus.SC_OK);
            exchange.getResponseSender().send(result);
        });
    }

    protected void handleRest(HttpServerExchange exchange, String operationName) {
        String selection = UndertowWebHelper.getQueryParam(exchange, SYS_PARAM_SELECTION);

        UndertowWebHelper.consumeRequestBody(exchange, (body) -> {
            String result = FutureHelper.syncGet(runRest(null,
                                                         operationName,
                                                         () -> buildRequest(body, selection, true),
                                                         this::transformRestResponse));

            exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, MediaType.APPLICATION_JSON);
            exchange.getResponseSender().send(result);
        });
    }

    protected void handlePageQuery(HttpServerExchange exchange, String method, String query) {
        String selection = UndertowWebHelper.getQueryParam(exchange, SYS_PARAM_SELECTION);

        if ("GET".equalsIgnoreCase(method)) {
            String args = UndertowWebHelper.getQueryParam(exchange, SYS_PARAM_ARGS);

            doPageQuery(GraphQLOperationType.query, query, selection, args);
        } else {
            UndertowWebHelper.consumeRequestBody(exchange, (body) -> doPageQuery(null, query, selection, body));
        }
    }

    protected void doPageQuery(GraphQLOperationType operationType, String query, String selection, String args) {
        String operationName = query;

        int pos = query.indexOf('/');
        String path = pos > 0 ? query.substring(pos) : null;
        if (pos > 0) {
            operationName = query.substring(0, pos);
        }

        runRest(operationType, operationName, () -> {
            ApiRequest<Map<String, Object>> req = buildRequest(args, selection, true);

            if (path != null) {
                req.getData().put(GraphQLConstants.PARAM_PATH, path);
            }
            return req;
        }, this::outputPageResponse);
    }

    protected String transformGraphQLResponse(GraphQLResponseBean response, IGraphQLExecutionContext gqlContext) {
        HttpServerExchange exchange = UndertowContext.getExchange();

        UndertowWebHelper.setResponseHeader(exchange, gqlContext.getResponseHeaders());

        return JsonTool.serialize(response, false);
    }

    protected String transformRestResponse(ApiResponse<?> response, IGraphQLExecutionContext gqlContext) {
        HttpServerExchange exchange = UndertowContext.getExchange();

        UndertowWebHelper.setResponseHeader(exchange, response.getHeaders());

        int status = response.getHttpStatus();
        if (status == 0) {
            status = 200;
        }
        exchange.setStatusCode(status);

        return JSON.stringify(response.cloneInstance(false));
    }

    protected Void outputPageResponse(ApiResponse<?> res, IGraphQLExecutionContext gqlContext) {
        int status = res.getHttpStatus();
        if (status == 0) {
            status = 200;
        }

        HttpServerExchange exchange = UndertowContext.getExchange();
        exchange.setStatusCode(status);

        Object data = res.getData();
        if (data instanceof String) {
            UndertowWebHelper.setResponseHeader(exchange,
                                                ApiConstants.HEADER_CONTENT_TYPE,
                                                WebContentBean.CONTENT_TYPE_TEXT);
            LOG.debug("nop.graphql.response:{}", data);

            UndertowWebHelper.send(exchange, (String) data);
        } else if (data instanceof WebContentBean) {
            WebContentBean contentBean = (WebContentBean) data;

            outputContent(exchange, contentBean.getContentType(), contentBean.getContent(), contentBean.getFileName());
        } else if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;

            if (map.containsKey("contentType") && map.containsKey("content") && map.size() >= 2) {
                String contentType = ConvertHelper.toString(map.get("contentType"));

                outputContent(exchange, contentType, map.get("content"), (String) map.get("fileName"));
            } else {
                outputJson(exchange, res);
            }
        } else {
            outputJson(exchange, res);
        }
        return null;
    }

    private void outputContent(HttpServerExchange exchange, String contentType, Object content, String fileName) {
        UndertowWebHelper.setResponseHeader(exchange, ApiConstants.HEADER_CONTENT_TYPE, contentType);

        if (!StringHelper.isEmpty(fileName)) {
            String encoded = StringHelper.encodeURL(fileName);
            UndertowWebHelper.setResponseHeader(exchange, "Content-Disposition", "attachment; filename=" + encoded);
        }

        if (content instanceof String) {
            LOG.debug("nop.graphql.response:{}", content);

            UndertowWebHelper.send(exchange, (String) content);
        } else if (content instanceof byte[]) {
            UndertowWebHelper.send(exchange, (byte[]) content);
        } else if (content instanceof InputStream || content instanceof File || content instanceof IResource) {
            if (content instanceof InputStream) {
                UndertowWebHelper.send(exchange, (InputStream) content);
            } else if (content instanceof IResource) {
                InputStream is = ((IResource) content).getInputStream();
                try {
                    UndertowWebHelper.send(exchange, is);
                } finally {
                    IoHelper.safeCloseObject(is);
                }
            } else {
                File file = (File) content;
                InputStream is = null;
                try {
                    is = new FileInputStream(file);
                    UndertowWebHelper.send(exchange, is);
                } catch (Exception e) {
                    throw NopException.adapt(e);
                } finally {
                    IoHelper.safeClose(is);
                }
            }
        } else {
            String str = JSON.stringify(content);
            LOG.debug("nop.graphql.response:{}", str);

            UndertowWebHelper.send(exchange, str);
        }
    }

    private void outputJson(HttpServerExchange exchange, ApiResponse<?> res) {
        UndertowWebHelper.setResponseHeader(exchange,
                                            ApiConstants.HEADER_CONTENT_TYPE,
                                            WebContentBean.CONTENT_TYPE_JSON);

        String str;
        if (res.isOk()) {
            str = JSON.stringify(res.getData());
        } else {
            str = JSON.stringify(res.cloneInstance(false));
        }

        LOG.debug("nop.graphql.response:{}", str);
        UndertowWebHelper.send(exchange, str);
    }
}
