package io.nop.solon;

import io.nop.solon.service.SolonGraphQLWebService;
import io.nop.solon.service.SolonInitializer;
import io.nop.solon.web.SolonHttpServerFilter;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;
import org.noear.solon.web.staticfiles.StaticMappings;
import org.noear.solon.web.staticfiles.repository.ClassPathStaticRepository;

//todo: Solon Plugin 是编码风格的 spi（这也是提高启动速度的重要原因之一）
public class SolonAutoIntegration implements Plugin {
    @Override
    public void start(AppContext context) throws Throwable {
        //todo:在插件内扫描自己的组件（不需要外面管了）
        //context.beanScan(SolonAutoIntegration.class);

        //todo:如果比较少的类，用 beanMake 性能更好 (beanScan 需要先扫描 .class 文件资源，再转换成 clz；而且扫一片)
        context.beanMake(SolonGraphQLWebService.class);
        context.beanMake(SolonInitializer.class);
        context.beanMake(SolonHttpServerFilter.class);


        //todo: 登记静态资源 （Static 插件 原来是要 .js 存在时 .js.gz 才会生效；新版改进了）
//        NopResourceRepository repository = new NopResourceRepository();
        //StaticMappings.add("/", repository);
        StaticMappings.add("/", new ClassPathStaticRepository("META-INF/resources/"));
    }
}
