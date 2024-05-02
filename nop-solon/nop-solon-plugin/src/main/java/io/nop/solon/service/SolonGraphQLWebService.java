package io.nop.solon.service;

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
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Path;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class SolonGraphQLWebService extends GraphQLWebService {
    static final Logger LOG = LoggerFactory.getLogger(SolonGraphQLWebService.class);

    @Override
    protected Map<String, String> getParams() {
        Context context = Context.current();
        Map<String, String> map = context.paramMap();
        Map<String, String> ret = new LinkedHashMap<>();
        map.forEach((name, value) -> {
            name = StringHelper.replace(name, "%40", "@");

            ret.put(name, value);
        });
        return ret;
    }

    @Override
    protected Map<String, Object> getHeaders() {
        Map<String, Object> headers = new HashMap<>();
        Context.current().headersMap().forEach((name, list) -> {
            headers.put(name, list.get(0));
        });
        return headers;
    }

    @Mapping(path = "/graphql", method = MethodType.POST, produces = "application/json")
    public String graphqlSolon(Context context) throws IOException {
        String body = context.body();
        return FutureHelper.syncGet(runGraphQL(body, this::transformGraphQLResponse));
    }

    protected String transformGraphQLResponse(GraphQLResponseBean response, IGraphQLExecutionContext gqlContext) {
        SolonWebHelper.setResponseHeader(Context.current(), gqlContext.getResponseHeaders());
        return JsonTool.serialize(response, false);
    }

    @Mapping(path = "/r/{@operationName}", method = {MethodType.GET, MethodType.POST}, produces = "application/json")
    public String restSolon(Context context, @Path("@operationName") String operationName) throws IOException {
        String selection = getSelectionParam(context);
        String body = "GET".equalsIgnoreCase(context.method()) ? null : context.body();
        return FutureHelper.syncGet(runRest(null, operationName, () -> {
            return buildRequest(body, selection, true);
        }, this::transformRestResponse));
    }

    private String getSelectionParam(Context context) {
        String selection = context.param("%40selection");
        if (selection == null)
            selection = context.param("@selection");
        return selection;
    }

    private String getArgsParam(Context context) {
        String args = context.param("%40args");
        if (args == null)
            args = context.param("@args");
        return args;
    }

    protected String transformRestResponse(ApiResponse<?> response, IGraphQLExecutionContext gqlContext) {
        SolonWebHelper.setResponseHeader(Context.current(), response.getHeaders());

        String str = JSON.stringify(response.cloneInstance(false));
        int status = response.getHttpStatus();
        if (status == 0)
            status = 200;

        Context.current().status(status);
        return str;
    }


    @Mapping(path = "/p/**", method = {MethodType.GET, MethodType.POST})
    public void pageQuerySolon(Context context) {
        String query = StringHelper.removeHead(context.path(), "/p/");
        String selection = getSelectionParam(context);
        String args = getArgsParam(context);
        GraphQLOperationType operationType = "GET".equalsIgnoreCase(context.method()) ?
                GraphQLOperationType.query : null;
        doPageQuery(operationType, query, selection, args);
    }

    protected void doPageQuery(GraphQLOperationType operationType,
                               String query, String selection, String args) {
        int pos = query.indexOf('/');
        String operationName = query;
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
        }, this::outputSolonPageResponse);
    }

    protected Void outputSolonPageResponse(ApiResponse<?> res, IGraphQLExecutionContext gqlContext) {

        int status = res.getHttpStatus();
        if (status == 0)
            status = 200;

        Context context = Context.current();
        context.status(status);

        Object data = res.getData();
        if (data instanceof String) {
            context.headerSet(ApiConstants.HEADER_CONTENT_TYPE, WebContentBean.CONTENT_TYPE_TEXT);
            LOG.debug("nop.graphql.response:{}", data);
            context.output((String) data);
        } else if (data instanceof WebContentBean) {
            WebContentBean contentBean = (WebContentBean) data;
            outputContent(context, contentBean.getContentType(), contentBean.getContent(), contentBean.getFileName());
        } else if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            if (map.containsKey("contentType") && map.containsKey("content") && map.size() >= 2) {
                String contentType = ConvertHelper.toString(map.get("contentType"));
                outputContent(context, contentType, map.get("content"), (String) map.get("fileName"));
            } else {
                outputJson(context, res);
            }
        } else {
            outputJson(context, res);
        }
        return null;
    }

    private void outputContent(Context context, String contentType, Object content, String fileName) {
        context.headerSet(ApiConstants.HEADER_CONTENT_TYPE, contentType);
        if (!StringHelper.isEmpty(fileName)) {
            String encoded = StringHelper.encodeURL(fileName);
            context.headerSet("Content-Disposition", "attachment; filename=" + encoded);
        }

        if (content instanceof String) {
            LOG.debug("nop.graphql.response:{}", content);
            context.output((String) content);
        } else if (content instanceof byte[]) {
            context.output((byte[]) content);
        } else if (content instanceof InputStream || content instanceof File || content instanceof IResource) {
            if (content instanceof InputStream) {
                context.output((InputStream) content);
            } else if (content instanceof IResource) {
                InputStream is = ((IResource) content).getInputStream();
                try {
                    context.output(is);
                } finally {
                    IoHelper.safeCloseObject(is);
                }
            } else {
                File file = (File) content;
                InputStream is = null;
                try {
                    is = new FileInputStream(file);
                    context.output(is);
                } catch (Exception e) {
                    throw NopException.adapt(e);
                } finally {
                    IoHelper.safeClose(is);
                }
            }
        } else {
            String str = JSON.stringify(content);
            LOG.debug("nop.graphql.response:{}", str);
            context.output(str);
        }
    }

    private void outputJson(Context context, ApiResponse<?> res) {
        context.headerSet(ApiConstants.HEADER_CONTENT_TYPE, WebContentBean.CONTENT_TYPE_JSON);
        String str;
        if (res.isOk()) {
            str = JSON.stringify(res.getData());
        } else {
            str = JSON.stringify(res.cloneInstance(false));
        }
        LOG.debug("nop.graphql.response:{}", str);
        context.output(str);
    }
}
