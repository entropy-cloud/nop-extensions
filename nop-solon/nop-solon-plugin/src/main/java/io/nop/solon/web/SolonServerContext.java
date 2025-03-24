package io.nop.solon.web;

import io.nop.api.core.context.IContext;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.server.IAsyncBody;
import io.nop.http.api.server.IHttpServerContext;
import org.noear.solon.core.NvMap;
import org.noear.solon.core.handle.Context;

import java.net.HttpCookie;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

public class SolonServerContext implements IHttpServerContext {
    private final Context context;

    private IContext nopContext;

    public SolonServerContext(Context context) {
        this.context = context;
    }

    @Override
    public String getHost() {
        return context.realIp();
    }

    @Override
    public String getRequestPath() {
        return context.path();
    }

    @Override
    public String getRequestUrl() {
        return context.path();
    }

    @Override
    public String getQueryParam(String name) {
        return context.param(name);
    }

    @Override
    public Map<String, String> getQueryParams() {
        Map<String, String> params = new LinkedHashMap<>();
        for (String name : context.paramsMap().keySet()) {
            params.put(name, getQueryParam(name));
        }
        return params;
    }

    @Override
    public Map<String, Object> getRequestHeaders() {
        Map<String, Object> map = new HashMap<>();
        NvMap headers = context.headerMap();
        for (String key : headers.keySet()) {
            map.put(key, headers.get(key));
        }
        return map;
    }

    @Override
    public Object getRequestHeader(String headerName) {
        return context.header(headerName);
    }

    @Override
    public String getCookie(String name) {
        return context.cookie(name);
    }

    @Override
    public void addCookie(String sameSite, HttpCookie cookie) {
        context.cookieSet(cookie.getName(), cookie.getValue(), cookie.getDomain(),
                cookie.getPath(), (int) cookie.getMaxAge());
    }

    @Override
    public void removeCookie(String name) {
        context.cookieRemove(name);
    }

    @Override
    public void removeCookie(String name, String domain, String path) {
        context.cookieRemove(name);
    }

    @Override
    public void setResponseHeader(String headerName, Object value) {
        context.headerSet(headerName, String.valueOf(value));
    }

    @Override
    public void sendRedirect(String url) {
        context.redirect(url);
    }

    @Override
    public void sendResponse(int httpStatus, String body) {
        context.status(httpStatus);
        if (!StringHelper.isEmpty(body))
            context.output(body);
    }

    @Override
    public boolean isResponseSent() {
        return false;
    }

    @Override
    public String getAcceptableContentType() {
        return context.accept();
    }

    @Override
    public String getResponseContentType() {
        return context.contentType();
    }

    @Override
    public void setResponseContentType(String contentType) {
        context.contentType(contentType);
    }

    @Override
    public IAsyncBody getRequestBody() {
        return new IAsyncBody() {
            @Override
            public CompletionStage<String> getTextAsync() {
                return FutureHelper.futureCall(() -> {
                    String text = context.body();
                    return text;
                });
            }
        };
    }

    @Override
    public CompletionStage<Object> executeBlocking(Callable<?> task) {
        return FutureHelper.futureCall(task);
    }

    @Override
    public IContext getContext() {
        return nopContext;
    }

    @Override
    public void setContext(IContext context) {
        this.nopContext = context;
    }

    @Override
    public void setResponseCharacterEncoding(String encoding) {
        context.charset(encoding);
    }
}
