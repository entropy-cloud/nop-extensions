# Nop Undertow 集成指南

## 准备工作

克隆本项目至本地并对其进行本地构建和发布包部署：

```bash
# 注意，请根据当前运行环境修改 JDK 17+ 的安装路径
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

git clone https://gitee.com/canonical-entropy/nop-extensions

cd nop-extensions
mvn clean install -DskipTests
```

> 需确保已本地构建 https://gitee.com/canonical-entropy/nop-entropy 项目。

也可以参考 https://repo.nop.crazydan.io/ 的说明配置并引入 Nop 仓库。

## 引入依赖

在项目的应用启动器所在项目中引入如下依赖：

```xml
  <dependency>
    <groupId>io.github.entropy-cloud.extensions</groupId>
    <artifactId>nop-undertow-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </dependency>
```

若需要通过 `nop-file` 提供文件上传能力，则还需引入以下依赖：

```xml
  <dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-biz-file-core</artifactId>
  </dependency>

  <!-- 本地存储方式 -->
  <dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-integration-file-local</artifactId>
  </dependency>
  <!-- OSS 存储方式 -->
  <dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-integration-oss</artifactId>
  </dependency>
```

> 本地存储和 OSS 存储可二选一，不需要同时引入。

## 应用配置

在 `src/main/resources/application.yaml` 中配置 Undertow 服务：

```yaml
nop:
  extension:
    undertow:
      server:
        port: 8088
        host: localhost
        compression:
          enabled: true
          # min-response-size: 2048
          mime-types: text/xml,text/plain,application/json,application/xml
```

> Undertow 的默认配置见 `io.nop.undertow.UndertowConfigs`。

- `nop.extension.undertow.server.port`：Web 服务的监听端口号，默认为 `8080`
- `nop.extension.undertow.server.host`：Web 服务的监听 IP 地址，默认为 `127.0.0.1`
- `nop.extension.undertow.server.compression.enabled`：
  用于控制是否启用对响应数据的 GZip 压缩，默认为 `false`
- `nop.extension.undertow.server.compression.min-response-size`：
  用于设置最小的响应数据字节数，只有超过该字节数的响应数据才会被压缩，默认为 `2048`
- `nop.extension.undertow.server.compression.mime-types`：
  用于设置哪些类型的响应数据可以被压缩，其为以逗号分隔的 MIME 类型值

若是启用了 `nop-file`，则需要添加 `nop-file` 相关的配置：

```yaml
# 本地存储方式
nop:
  file:
    store-dir: /tmp/nop-files

# OSS 存储方式
nop:
  file:
    store-impl: oss
  integration:
    # 完整的配置数据见 io.nop.integration.oss.OssConfig
    oss:
      enabled: true
      endpoint: http://localhost:9000
      access-key: nop
      secret-key: nop-test
```

## 添加 Web 启动器

和 Spring Boot 一样，每个可执行服务都需要定义一个启动器作为程序的执行入口。
与 `nop-undertow` 集成的项目则需要定义一个继承自 `io.nop.undertow.UndertowServer`
的启动器，如：

```java
public class DemoApplication extends UndertowServer {

  public static void main(String[] args) {
    start(args);
  }
}
```

在 `io.nop.undertow.UndertowServer#start` 中将会初始化
Nop 运行环境，并根据应用配置创建和启动 Undertow 服务。

在开发调试时，直接以 `DemoApplication#main` 为调试启动入口即可。

## 构建可执行 Jar 包

首先，在启动器所在项目的 `pom.xml` 中引入如下构建插件：

```xml
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
      </plugin>
    </plugins>

    <pluginManagement>
      <plugins>
        <!-- 可执行 jar 打包: https://maven.apache.org/plugins/maven-shade-plugin/examples/executable-jar.html -->
        <!--
        注意，该插件会把依赖全部解包并合并放在可执行的 jar 中，
        即 uberjar 打包模式，其比 Spring boot 的 nested-jars 打包模式的加载速度会更快一点，
        但是，同名类和资源文件会出现被覆盖的问题，因此，需尽量避免同名文件，
        或者通过该插件的 transformer 对同名文件做合并（https://juejin.cn/post/7065862239349112863）。
        在依赖较复杂，且可能存在多版本时，可以尝试采用插件 spring-boot-maven-plugin
        以 nested-jars 模式打包
        -->
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-shade-plugin</artifactId>
          <version>3.1.1</version>
          <executions>
            <execution>
              <phase>package</phase>
              <goals>
                <goal>shade</goal>
              </goals>
              <configuration>
                <finalName>${project.build.finalName}-exec</finalName>
                <transformers>
                  <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                    <mainClass>io.nop.undertow.UndertowStarter</mainClass>
                  </transformer>
                  <!--
                  META-INF/services/ 中会存在同名文件，需将其内容合并，以确保
                  Nop IocCoreInitializer 等能够被 ServiceLoader 加载:
                  https://maven.apache.org/plugins/maven-shade-plugin/examples/resource-transformers.html#ServicesResourceTransformer
                  -->
                  <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                </transformers>
                <!--
                修复运行 jar 时出现 "java.lang.SecurityException: Invalid signature file digest for Manifest main attributes" 异常的问题:
                https://stackoverflow.com/questions/34855649/invalid-signature-file-digest-for-manifest-main-attributes-exception-while-tryin#answer-64263739
                -->
                <filters>
                  <filter>
                    <artifact>*:*</artifact>
                    <excludes>
                      <exclude>META-INF/*.SF</exclude>
                      <exclude>META-INF/*.DSA</exclude>
                      <exclude>META-INF/*.RSA</exclude>
                    </excludes>
                  </filter>
                </filters>
              </configuration>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
```

> 若是应用的启动器包含其他处理逻辑，则需要将 `<mainClass>io.nop.undertow.UndertowStarter</mainClass>`
> 中的 `io.nop.undertow.UndertowStarter` 修改为自己的启动器 class。

然后，通过 Maven 打包项目：

```bash
# 注意，请根据当前运行环境修改 JDK 17+ 的安装路径
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

mvn clean package
```

完成后，会在项目的 `target` 目录下生成可执行包 `xxx-exec.jar`，
可在控制台中直接运行该 jar 包：

```bash
${JAVA_HOME}/bin/java -jar target/xxx-exec.jar
```
