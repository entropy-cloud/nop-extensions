package io.nop.undertow;

import io.nop.boot.NopApplication;
import io.nop.undertow.web.UndertowHttpServerHandler;
import io.undertow.Undertow;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-05-06
 */
public abstract class UndertowServer {

    public static int start(String... args) {
        NopApplication app = new NopApplication();

        return app.run(args, () -> {
            // https://undertow.io/undertow-docs/undertow-docs-2.1.0/index.html#the-builder-api
            Undertow server = Undertow.builder()
                                      .addHttpListener(UndertowConfigs.CFG_SERVER_PORT.get(),
                                                       UndertowConfigs.CFG_SERVER_HOST.get())
                                      .setHandler(new UndertowHttpServerHandler())
                                      .build();
            server.start();

            return 0;
        });
    }
}
