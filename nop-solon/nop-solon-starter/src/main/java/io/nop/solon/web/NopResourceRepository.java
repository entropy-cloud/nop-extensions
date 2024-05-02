package io.nop.solon.web;

import io.nop.commons.util.StringHelper;
import org.noear.solon.web.staticfiles.StaticRepository;

import java.io.IOException;
import java.net.URL;

// todo: 这个类可以不需要了，内置的 ClassPathStaticRepository 支持同等效果 //（Static 插件 原来是要 .js 存在时 .js.gz 才会生效；新版改进了）
//public class NopResourceRepository implements StaticRepository {
//    @Override
//    public URL find(String relativePath) throws Exception {
//        if (relativePath.endsWith(".js") || relativePath.endsWith(".css")) {
//            URL url = loadResource(relativePath + ".gz");
//            if (url != null) {
//                return new URL(StringHelper.removeTail(url.toString(), ".gz"));
//            }
//        }
//        return loadResource(relativePath);
//    }
//
//    URL loadResource(String path) throws IOException {
//        String fullPath = StringHelper.appendPath("META-INF/resources/", path);
//        return this.getClass().getClassLoader().getResource(fullPath);
//    }
//}
