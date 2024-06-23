package io.nop.solon.demo;

import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import org.noear.solon.Solon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolonDemoMain {
    static final Logger LOG = LoggerFactory.getLogger(SolonDemoMain.class);

    public static void main(String[] args) {
        Solon.start(SolonDemoMain.class, args);

        IResource resource = new ClassPathResource("classpath:logback.xml");
        if (resource.exists()) {
            System.out.println("classpath:logback.xml");
        }

        resource = new ClassPathResource("classpath:_vfs/nop/demo/report/base/01-档案式报表.xpt.xlsx");
        if (resource.exists()) {
            System.out.println("report-file-exists:" + resource.length());
        }
        System.out.println("logLevel:" + LOG.isInfoEnabled());
        LOG.info("result");
    }
}
