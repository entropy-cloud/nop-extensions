package io.nop.solon.demo;

import org.noear.solon.Solon;

//todo:由插件的自动集成处理（用户体验，会更清爽）see: SolonAutoIntegration
//@Import(scanPackages = "io.nop.solon")
public class SolonDemoMain {
    public static void main(String[] args) {
        Solon.start(SolonDemoMain.class, args);
    }
}
