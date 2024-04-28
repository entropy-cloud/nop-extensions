package io.nop.solon.demo;

import org.noear.solon.Solon;
import org.noear.solon.annotation.Import;

@Import(scanPackages = "io.nop.solon")
public class SolonDemoMain {
    public static void main(String[] args) {
        Solon.start(SolonDemoMain.class, args);
    }
}
