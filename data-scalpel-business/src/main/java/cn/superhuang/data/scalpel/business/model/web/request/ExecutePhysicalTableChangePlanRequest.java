package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Explicit user choice of one dialect-rendered change execution option. */
@Schema(description = "显式选择 PLANNED 计划中由目标方言冻结的一种执行方式。执行会先将计划置为 APPLYING，在管理事务外运行受控 DDL，通过后再原子应用目标字段快照并推进模型 schemaVersion。")
public record ExecutePhysicalTableChangePlanRequest(
        @Schema(description = "执行方式：IN_PLACE 原表修改，REBUILD 受控重建；必须是变更计划返回的可选执行方式之一")
        @NotNull TableChangeExecutionMode executionMode
) {
}
