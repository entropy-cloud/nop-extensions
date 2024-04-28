package io.nop.solon.web;

import io.nop.commons.util.StringHelper;
import org.noear.solon.web.staticfiles.StaticRepository;

import java.io.IOException;
import java.net.URL;

public class NopResourceRepository implements StaticRepository {
    @Override
    public URL find(String relativePath) throws Exception {
        if (relativePath.endsWith(".js") || relativePath.endsWith(".css")) {
            URL url = loadResource(relativePath + ".gz");
            if (url != null) {
                return new URL(StringHelper.removeTail(url.toString(), ".gz"));
            }
        }
        return loadResource(relativePath);
    }

    URL loadResource(String path) throws IOException {
        String fullPath = StringHelper.appendPath("META-INF/resources/", path);
        return this.getClass().getClassLoader().getResource(fullPath);
    }
}
