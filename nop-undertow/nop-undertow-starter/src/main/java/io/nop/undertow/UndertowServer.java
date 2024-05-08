package io.nop.undertow;

import io.nop.boot.NopApplication;
import io.nop.undertow.web.UndertowHttpServerHandler;
import io.undertow.Undertow;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-05-06
 */
public class UndertowServer {

    public static int start(String... args) {
        // TODO 可配置项：端口、IP、是否启用资源压缩、Nop File 支持
        // https://undertow.io/undertow-docs/undertow-docs-2.1.0/index.html#the-builder-api
        Undertow server = Undertow.builder()
                                  .addHttpListener(8080, "localhost")
                                  .setHandler(new UndertowHttpServerHandler())
                                  .build();

        NopApplication app = new NopApplication();

        return app.run(args, () -> {
            server.start();
            return 0;
        });
    }
}
