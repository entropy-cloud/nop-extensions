package io.nop.undertow;

import java.util.Set;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;
import static io.nop.api.core.config.AppConfig.withPlaceholder;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-06-22
 */
@Locale("zh-CN")
public interface UndertowConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(UndertowConfigs.class);

    @Description("Undertow服务监听的IP地址，默认为127.0.0.1")
    IConfigReference<String> CFG_SERVER_HOST = //
            varRef(s_loc, "nop.extension.undertow.server.host", String.class, "127.0.0.1");
    @Description("Undertow服务监听的端口号，默认为8080")
    IConfigReference<Integer> CFG_SERVER_PORT = //
            varRef(s_loc, "nop.extension.undertow.server.port", Integer.class, 8080);

    @Description("Undertow服务的外部静态资源目录")
    IConfigReference<String> CFG_SERVER_STATIC_DIR = //
            withPlaceholder(varRef(s_loc, "nop.extension.undertow.server.static-dir", String.class, null));

    @Description("Undertow服务是否启用对响应的压缩支持")
    IConfigReference<Boolean> CFG_SERVER_COMPRESSION_ENABLED = //
            varRef(s_loc, "nop.extension.undertow.server.compression.enabled", Boolean.class, false);
    @Description("Undertow服务仅针对Content-Length大于指定值的响应进行压缩，默认为2048（即，2KB）")
    IConfigReference<Integer> CFG_SERVER_COMPRESSION_MIN_RESPONSE_SIZE = //
            varRef(s_loc, "nop.extension.undertow.server.compression.min-response-size", Integer.class, 2 * 1024);
    @Description("Undertow服务仅对指定的MIME类型的响应进行压缩，以逗号分隔不同的MIME类型")
    IConfigReference<Set> CFG_SERVER_COMPRESSION_MIME_TYPES = //
            varRef(s_loc,
                   "nop.extension.undertow.server.compression.mime-types",
                   Set.class,
                   Set.of("text/html",
                          "text/xml",
                          "text/plain",
                          "text/css",
                          "text/javascript",
                          "application/javascript",
                          "application/json",
                          "application/xml"));
}
