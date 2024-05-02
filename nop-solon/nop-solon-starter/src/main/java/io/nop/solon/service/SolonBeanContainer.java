package io.nop.solon.service;

import io.nop.api.core.ioc.IBeanContainer;
//todo: 打包时提示不存在（java11, java17） //换成了 org.noear.solon.lang.NonNull，不过它只是标注，不会验证
//import org.jetbrains.annotations.NotNull;
import org.noear.solon.lang.NonNull;
import org.noear.solon.core.BeanContainer;

import java.lang.annotation.Annotation;
import java.util.Map;

public class SolonBeanContainer implements IBeanContainer {
    private final BeanContainer beanContainer;

    public SolonBeanContainer(BeanContainer beanContainer) {
        this.beanContainer = beanContainer;
    }

    @Override
    public String getId() {
        return "solon";
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void restart() {

    }

    @Override
    public boolean containsBean(String name) {
        return beanContainer.hasWrap(name);
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @NonNull
    @Override
    public Object getBean(String name) {
        return beanContainer.getBean(name);
    }

    @Override
    public boolean containsBeanType(Class<?> aClass) {
        //todo:如果只查本类这个更适合
        return beanContainer.hasWrap(aClass);
        //todo: 如果还要查基类，那得是这个
        //return !beanContainer.getWrapsOfType(aClass).isEmpty();
    }

    @NonNull
    @Override
    public <T> T getBeanByType(Class<T> aClass) {
        return beanContainer.getBean(aClass);
    }

    @Override
    public <T> T tryGetBeanByType(Class<T> aClass) {
        try {
            return beanContainer.getBean(aClass);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> aClass) {
        return beanContainer.getBeansMapOfType(aClass);
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> aClass) {
        return null;
    }

    @Override
    public String getBeanScope(String name) {
        return null;
    }

    @Override
    public Class<?> getBeanClass(String name) {
        return beanContainer.getWrap(name).clz();
    }

    @Override
    public String findAutowireCandidate(Class<?> aClass) {
        return null;
    }
}
