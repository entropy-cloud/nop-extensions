package io.nop.solon;

import io.nop.solon.service.SolonGraphQLWebService;
import io.nop.solon.service.SolonInitializer;
import io.nop.solon.web.SolonHttpServerFilter;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;
import org.noear.solon.web.staticfiles.StaticMappings;
import org.noear.solon.web.staticfiles.repository.ClassPathStaticRepository;

/**
 * Solon Plugin 是编码风格的 spi（这也是提高启动速度的重要原因之一），所以这里避免类扫描，直接使用Plugin机制实现初始化
 */
public class SolonAutoIntegration implements Plugin {
    @Override
    public void start(AppContext context) throws Throwable {
        // 如果比较少的类，用 beanMake 性能更好 (beanScan 需要先扫描 .class 文件资源，再转换成 clz；而且扫一片)
        context.beanMake(SolonGraphQLWebService.class);
        context.beanMake(SolonInitializer.class);
        context.beanMake(SolonHttpServerFilter.class);

        StaticMappings.add("/", new ClassPathStaticRepository("META-INF/resources/"));
    }
}
