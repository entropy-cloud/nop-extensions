package io.nop.solon.service;

import io.nop.core.initialize.CoreInitialization;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.bean.LifecycleBean;

@Component(index = -1)
public class SolonInitializer implements LifecycleBean {
    @Inject
    AppContext appContext;

    @Override
    public void start() throws Throwable {
        io.nop.api.core.ioc.BeanContainer.registerInstance(new SolonBeanContainer(appContext));
        CoreInitialization.initialize();
    }

    @Override
    public void stop() throws Throwable {
        CoreInitialization.destroy();
    }
}
