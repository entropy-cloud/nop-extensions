package io.nop.solon.service;

import io.nop.api.core.convert.ConvertHelper;
import org.noear.solon.core.handle.Context;

import java.util.Map;

public class SolonWebHelper {
    public static void setResponseHeader(Context context, Map<String, Object> headers) {
        if (headers != null) {
            headers.forEach((name, value) -> {
                context.headerSet(name, ConvertHelper.toString(value));
            });
        }
    }
}
