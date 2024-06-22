package io.nop.solon.demo;

import io.nop.dao.seq.UuidSequenceGenerator;
import org.noear.solon.annotation.Component;
import org.noear.solon.aot.RuntimeNativeMetadata;
import org.noear.solon.aot.RuntimeNativeRegistrar;
import org.noear.solon.aot.hint.MemberCategory;
import org.noear.solon.core.AppContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * @author wyl
 * @date 2024年06月21日 22:35
 */
@Component
public class MyRuntimeNativeRegistrar implements RuntimeNativeRegistrar {

    @Override
    public void register(AppContext context, RuntimeNativeMetadata metadata) {
        metadata.registerReflection(LinkedHashMap.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        // metadata.registerReflection(ArrayList.class, MemberCategory.DECLARED_FIELDS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS);
        metadata.registerReflection(UuidSequenceGenerator.class, MemberCategory.DECLARED_FIELDS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS);

        metadata.registerResourceInclude("nop-vfs-index.txt");

        metadata.registerResourceInclude("_vfs/.*");
        metadata.registerResourceInclude("application.yaml");
        metadata.registerResourceInclude("bootstrap.yaml");

    }

}
