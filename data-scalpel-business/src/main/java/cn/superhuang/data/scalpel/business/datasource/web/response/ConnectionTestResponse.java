package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.ConnectionCheck;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "数据源连接测试结果；业务级失败通常仍以本结构返回 success=false。测试不会保存连接配置，但 S3 测试会对远端 Bucket 执行临时对象写入、列出和删除")
public record ConnectionTestResponse(
        @Schema(description = "是否完成当前数据源类型定义的探测；各类型探测范围不同，不代表后续所有业务操作均可成功") boolean success,
        @Schema(description = "稳定结果或诊断码") String code,
        @Schema(description = "可安全展示的测试结果说明") String message,
        @Schema(description = "连接测试总耗时，单位毫秒") long elapsedMs,
        @Schema(description = "探测到的产品名称；JDBC 为数据库产品，Kafka、S3、HTTP API 和空间服务返回对应产品标识，失败时为空") String databaseProduct,
        @Schema(description = "探测到的产品版本或目标摘要；JDBC 为数据库版本，Kafka 为 Cluster ID，S3 为 Bucket，HTTP API 为 HTTP 状态，空间服务为协议版本，失败时为空") String databaseVersion,
        @Schema(description = "执行探测的驱动或客户端名称；失败时为空") String driverName,
        @Schema(description = "失败诊断；success=true 时为空") ConnectionTestDiagnosticResponse diagnostic
) {
    public static ConnectionTestResponse from(ConnectionCheck check) {
        return new ConnectionTestResponse(
                check.success(),
                check.code(),
                check.message(),
                check.elapsedMs(),
                check.databaseProduct(),
                check.databaseVersion(),
                check.driverName(),
                null
        );
    }

    public static ConnectionTestResponse failed(
            String code,
            String message,
            long elapsedMs,
            ConnectionTestDiagnosticResponse diagnostic
    ) {
        return new ConnectionTestResponse(false, code, message, elapsedMs, null, null, null, diagnostic);
    }
}
