package io.nop.solon.service;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
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

        return FutureHelper.syncGet(runGraphQL(body, this::transformSolonResponse));
    }

    protected String transformSolonResponse(Map<String, Object> headers, String body, int status) {
        Context.current().status(status);

        SolonWebHelper.setResponseHeader(Context.current(), headers);

        return body;
    }

    @Mapping(path = "/r/{@operationName}", method = {MethodType.GET, MethodType.POST}, produces = "application/json")
    public String restSolon(Context context, @Path("@operationName") String operationName) throws IOException {
        String selection = getSelectionParam(context);
        String body = "GET".equalsIgnoreCase(context.method()) ? null : context.body();

        return FutureHelper.syncGet(runRest(null, operationName, () -> {
            return buildRequest(body, selection, true);
        }, this::transformSolonResponse));
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

    @Mapping(path = "/p/**", method = {MethodType.GET, MethodType.POST})
    public void pageQuerySolon(Context context) {
        String query = StringHelper.removeHead(context.path(), "/p/");
        String selection = getSelectionParam(context);
        String args = getArgsParam(context);
        GraphQLOperationType operationType = "GET".equalsIgnoreCase(context.method())
                                             ? GraphQLOperationType.query
                                             : null;

        doPageQuery(operationType, query, selection, args, this::outputPageQuery);
    }

    protected Void outputPageQuery(ApiResponse<?> response, IGraphQLExecutionContext gqlContext) {
        WebContentBean contentBean = buildWebContent(response);

        return consumeWebContent(response, contentBean, this::outputData);
    }

    protected Void outputData(Map<String, Object> headers, Object body, int status) {
        Context context = Context.current();
        context.status(status);

        SolonWebHelper.setResponseHeader(context, headers);

        if (body instanceof IResource) {
            InputStream is = ((IResource) body).getInputStream();
            try {
                context.output(is);
            } finally {
                IoHelper.safeCloseObject(is);
            }
        } else if (body instanceof File) {
            InputStream is = null;
            try {
                is = new FileInputStream((File) body);

                context.output(is);
            } catch (Exception e) {
                throw NopException.adapt(e);
            } finally {
                IoHelper.safeClose(is);
            }
        } else if (body instanceof byte[]) {
            context.output((byte[]) body);
        } else if (body instanceof InputStream) {
            context.output((InputStream) body);
        } else if (body instanceof String) {
            context.output((String) body);
        }

        return null;
    }
}
