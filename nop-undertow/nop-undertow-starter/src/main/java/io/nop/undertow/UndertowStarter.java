package io.nop.undertow;

/**
 * 可执行 Jar 的入口类
 * <p/>
 * 其作为通用的 jar 入口，方便统一配置打包插件。
 * 为方便在 IDE 中调试服务，一般建议在服务模块中新建一个继承自
 * {@link  UndertowServer} 的 XxxStarter 类
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2024-05-06
 */
public class UndertowStarter extends UndertowServer {

    public static void main(String[] args) {
        start(args);
    }
}
