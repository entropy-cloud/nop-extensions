package io.nop.undertow.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.web.GraphQLWebService;
import io.nop.undertow.web.UndertowWebHelper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_ARGS;
import static io.nop.graphql.core.GraphQLConstants.SYS_PARAM_SELECTION;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-05-06
 */
public class UndertowGraphQLHandler extends GraphQLWebService implements HttpHandler {
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
        UndertowWebHelper.consumeRequestBody(exchange, (body) -> runGraphQL(body, this::outputJson));
    }

    protected void handleRest(HttpServerExchange exchange, String operationName) {
        String selection = UndertowWebHelper.getQueryParam(exchange, SYS_PARAM_SELECTION);

        UndertowWebHelper.consumeRequestBody(exchange,
                                             (body) -> runRest(null,
                                                               operationName,
                                                               () -> buildRequest(body, selection, true),
                                                               this::outputJson));
    }

    protected void handlePageQuery(HttpServerExchange exchange, String method, String query) {
        String selection = UndertowWebHelper.getQueryParam(exchange, SYS_PARAM_SELECTION);

        if ("GET".equalsIgnoreCase(method)) {
            String args = UndertowWebHelper.getQueryParam(exchange, SYS_PARAM_ARGS);

            doPageQuery(GraphQLOperationType.query, query, selection, args, this::outputPageQuery);
        } else {
            UndertowWebHelper.consumeRequestBody(exchange,
                                                 (body) -> doPageQuery(null,
                                                                       query,
                                                                       selection,
                                                                       body,
                                                                       this::outputPageQuery));
        }
    }

    protected Void outputJson(Map<String, Object> headers, String body, int status) {
        HttpServerExchange exchange = UndertowContext.getExchange();

        headers.put(Headers.CONTENT_TYPE_STRING, WebContentBean.CONTENT_TYPE_JSON);

        UndertowWebHelper.send(exchange, headers, body, status);

        return null;
    }

    protected Void outputPageQuery(ApiResponse<?> response, IGraphQLExecutionContext gqlContext) {
        HttpServerExchange exchange = UndertowContext.getExchange();
        WebContentBean contentBean = buildWebContent(response);

        return consumeWebContent(response, contentBean, (headers, body, status) -> {
            UndertowWebHelper.send(exchange, headers, body, status);
            return null;
        });
    }
}
