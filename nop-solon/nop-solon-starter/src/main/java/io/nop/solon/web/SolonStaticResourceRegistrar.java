package io.nop.solon.web;

import org.noear.solon.annotation.Component;
import org.noear.solon.core.bean.LifecycleBean;
import org.noear.solon.web.staticfiles.StaticMappings;

@Component
public class SolonStaticResourceRegistrar implements LifecycleBean {
    @Override
    public void start() throws Throwable {
        NopResourceRepository repository = new NopResourceRepository();
        //StaticMappings.add("/js/", repository);
        StaticMappings.add("/", repository);
    }
}