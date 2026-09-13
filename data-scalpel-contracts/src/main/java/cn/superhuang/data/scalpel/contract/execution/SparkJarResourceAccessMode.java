package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Spark JAR 资源绑定允许的访问方向：READ 只读；WRITE 只写；READ_WRITE 可读写。运行时凭据和 SDK 操作按该声明收敛。")
public enum SparkJarResourceAccessMode {
    READ,
    WRITE,
    READ_WRITE;

    public boolean canRead() {
        return this == READ || this == READ_WRITE;
    }

    public boolean canWrite() {
        return this == WRITE || this == READ_WRITE;
    }
}
