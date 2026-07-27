package cn.superhuang.data.scalpel.dispatcher.backend.kubernetes;

import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;

public final class KubernetesNames {
    public static final String MANAGED = "cn.superhuang.datascalpel/managed";
    public static final String ENGINE_ID = "cn.superhuang.datascalpel/engine-id";
    public static final String EXECUTION_ID = "cn.superhuang.datascalpel/execution-id";
    public static final String RUN_ID = "cn.superhuang.datascalpel/run-id";
    public static final String ATTEMPT = "cn.superhuang.datascalpel/attempt";

    private KubernetesNames() { }

    public static String application(ExecutionIdentity identity) { return "ds-" + compact(identity); }

    public static String driverPod(ExecutionIdentity identity) { return application(identity) + "-driver"; }

    public static String secret(ExecutionIdentity identity) { return application(identity) + "-launch"; }

    public static String selector(ExecutionIdentity identity) {
        return String.join(",",
                MANAGED + "=true", ENGINE_ID + "=" + identity.engineId(),
                EXECUTION_ID + "=" + identity.executionId());
    }

    private static String compact(ExecutionIdentity identity) {
        return identity.executionId().toString().replace("-", "");
    }
}
