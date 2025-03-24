package io.nop.undertow.web;

import java.net.HttpCookie;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

import io.nop.api.core.context.IContext;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.HttpStatus;
import io.nop.http.api.server.IAsyncBody;
import io.nop.http.api.server.IHttpServerContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-05-06
 */
public class UndertowHttpServerContext implements IHttpServerContext {
    private final HttpServerExchange exchange;
    private String characterEncoding;

    private IContext nopContext;

    public UndertowHttpServerContext(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public String getHost() {
        return this.exchange.getHostName();
    }

    @Override
    public String getRequestPath() {
        return this.exchange.getRequestPath();
    }

    @Override
    public String getRequestUrl() {
        return this.exchange.getRequestURL();
    }

    @Override
    public String getQueryParam(String name) {
        return UndertowWebHelper.getQueryParam(this.exchange, name);
    }

    @Override
    public Map<String, String> getQueryParams() {
        return UndertowWebHelper.getQueryParams(this.exchange);
    }

    @Override
    public Map<String, Object> getRequestHeaders() {
        return UndertowWebHelper.getRequestHeaders(this.exchange);
    }

    @Override
    public Object getRequestHeader(String headerName) {
        return this.exchange.getRequestHeaders().getFirst(headerName);
    }

    @Override
    public String getCookie(String name) {
        return this.exchange.getRequestCookie(name).getValue();
    }

    @Override
    public void addCookie(String sameSite, HttpCookie cookie) {
        CookieImpl cook = new CookieImpl(cookie.getName(), cookie.getValue());
        cook.setSameSiteMode(sameSite);
        cook.setDomain(cookie.getDomain());
        cook.setPath(cookie.getPath());
        cook.setComment(cookie.getComment());
        cook.setMaxAge((int) cookie.getMaxAge());
        cook.setHttpOnly(cookie.isHttpOnly());
        cook.setDiscard(cookie.getDiscard());
        cook.setSecure(cookie.getSecure());
        cook.setVersion(cookie.getVersion());

        this.exchange.setResponseCookie(cook);
    }

    @Override
    public void removeCookie(String name) {
        Cookie cookie = this.exchange.getRequestCookie(name);

        if (cookie != null) {
            cookie.setMaxAge(0);
        } else {
            cookie = new CookieImpl(name, "");
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(0);
            cookie.setSecure(true);
        }
        this.exchange.setResponseCookie(cookie);
    }

    @Override
    public void removeCookie(String name, String domain, String path) {
        Cookie cookie = new CookieImpl(name, "");
        cookie.setPath(path);
        cookie.setDomain(domain);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setMaxAge(0);

        this.exchange.setResponseCookie(cookie);
    }

    @Override
    public void setResponseHeader(String headerName, Object value) {
        this.exchange.getResponseHeaders().add(new HttpString(headerName), ConvertHelper.toString(value));
    }

    @Override
    public void sendRedirect(String url) {
        this.exchange.setStatusCode(HttpStatus.SC_MOVED_TEMPORARILY);
        setResponseHeader(Headers.LOCATION_STRING, url);
    }

    @Override
    public void sendResponse(int httpStatus, String body) {
        this.exchange.setStatusCode(httpStatus);
        if (!StringHelper.isEmpty(body)) {
            this.exchange.getResponseSender().send(body);
        }
    }

    @Override
    public boolean isResponseSent() {
        return false;
    }

    @Override
    public String getAcceptableContentType() {
        return this.exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
    }

    @Override
    public String getResponseContentType() {
        return this.exchange.getResponseHeaders().getFirst(Headers.CONTENT_TYPE);
    }

    @Override
    public void setResponseContentType(String contentType) {
        if (characterEncoding != null && !contentType.contains("charset="))
            contentType += ";charset=" + characterEncoding;
        setResponseHeader(Headers.CONTENT_TYPE_STRING, contentType);
    }

    @Override
    public IAsyncBody getRequestBody() {
        String[] receiver = new String[] { null };
        UndertowWebHelper.consumeRequestBody(this.exchange, (body) -> receiver[0] = body);

        return () -> FutureHelper.futureCall(() -> receiver[0]);
    }

    @Override
    public CompletionStage<Object> executeBlocking(Callable<?> task) {
        return FutureHelper.futureCall(task);
    }

    @Override
    public IContext getContext() {
        return this.nopContext;
    }

    @Override
    public void setContext(IContext context) {
        this.nopContext = context;
    }

    @Override
    public void setResponseCharacterEncoding(String encoding) {
        this.characterEncoding = encoding;
        String contentType = getResponseContentType();
        if (contentType != null) {
            setResponseContentType(contentType);
        }
    }
}
