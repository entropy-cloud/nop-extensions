package io.nop.undertow.service;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.web.GraphQLWebService;
import io.nop.undertow.handler.FutureHttpHandler;
import io.nop.undertow.handler.FutureHttpHandlerHelper;
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
public class UndertowGraphQLHandler extends GraphQLWebService implements FutureHttpHandler {
    private final static Pattern REST_URL_MATCHER = Pattern.compile("^/r/([^/\\\\]+)$");
    private final static Pattern PAGE_QUERY_URL_MATCHER = Pattern.compile("^/p/(.+)$");

    private final HttpHandler next;

    public UndertowGraphQLHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    protected Map<String, String> getParams() {
        // Note：该接口会在异步创建前被调用，所以，可以直接获取线程变量
        HttpServerExchange exchange = UndertowContext.getExchange();

        return UndertowWebHelper.getQueryParams(exchange);
    }

    @Override
    protected Map<String, Object> getHeaders() {
        // Note：该接口会在异步创建前被调用，所以，可以直接获取线程变量
        HttpServerExchange exchange = UndertowContext.getExchange();

        return UndertowWebHelper.getRequestHeaders(exchange);
    }

    @Override
    public CompletionStage<Void> handleRequestAsync(HttpServerExchange exchange) {
        String path = exchange.getRequestPath();
        String method = exchange.getRequestMethod().toString();

        if ("/graphql".equals(path) && "POST".equalsIgnoreCase(method)) {
            return handleGraphQL(exchange);
        }

        Matcher matcher = REST_URL_MATCHER.matcher(path);
        if (matcher.matches()) {
            String operationName = matcher.group(1);

            return handleRest(exchange, operationName);
        }

        matcher = PAGE_QUERY_URL_MATCHER.matcher(path);
        if (matcher.matches()) {
            String query = matcher.group(1);

            return handlePageQuery(exchange, method, query);
        }

        return FutureHttpHandlerHelper.handleRequest(this.next, exchange);
    }

    protected CompletionStage<Void> handleGraphQL(HttpServerExchange exchange) {
        return UndertowWebHelper.consumeRequestBody(exchange, (body) -> {
            //
            return runGraphQL(body, (headers, data, status) -> outputJson(exchange, headers, data, status));
        });
    }

    protected CompletionStage<Void> handleRest(HttpServerExchange exchange, String operationName) {
        String selection = UndertowWebHelper.getQueryParam(exchange, SYS_PARAM_SELECTION);

        return UndertowWebHelper.consumeRequestBody(exchange, (body) -> {
            //
            return runRest(null,
                           operationName,
                           () -> buildRequest(body, selection, true),
                           (headers, data, status) -> outputJson(exchange, headers, data, status));
        });
    }

    protected CompletionStage<Void> handlePageQuery(HttpServerExchange exchange, String method, String query) {
        String selection = UndertowWebHelper.getQueryParam(exchange, SYS_PARAM_SELECTION);

        if ("GET".equalsIgnoreCase(method)) {
            String args = UndertowWebHelper.getQueryParam(exchange, SYS_PARAM_ARGS);

            return doPageQuery(GraphQLOperationType.query, query, selection, args, (response, gqlContext) -> {
                //
                return outputPageQuery(exchange, response, gqlContext);
            });
        } else {
            return UndertowWebHelper.consumeRequestBody(exchange, (body) -> {
                //
                return doPageQuery(null,
                                   query,
                                   selection,
                                   body,
                                   (response, gqlContext) -> outputPageQuery(exchange, response, gqlContext));
            });
        }
    }

    /** Note：输出是异步的，需直接传递 exchange 以避免在先线程中无法通过 {@link UndertowContext#getExchange()} 获取的问题 */
    protected Void outputJson(HttpServerExchange exchange, Map<String, Object> headers, String body, int status) {
        headers.put(Headers.CONTENT_TYPE_STRING, WebContentBean.CONTENT_TYPE_JSON);

        UndertowWebHelper.send(exchange, headers, body, status);

        return null;
    }

    /** Note：输出是异步的，需直接传递 exchange 以避免在先线程中无法通过 {@link UndertowContext#getExchange()} 获取的问题 */
    protected Void outputPageQuery(
            HttpServerExchange exchange, ApiResponse<?> response, IGraphQLExecutionContext gqlContext
    ) {
        WebContentBean contentBean = buildWebContent(response);

        return consumeWebContent(response, contentBean, (headers, body, status) -> {
            UndertowWebHelper.send(exchange, headers, body, status);
            return null;
        });
    }
}
