package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionOption;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableDdlAtomicity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "目标方言明确允许的一种受控执行方案")
public record PhysicalTableChangeExecutionOptionResponse(
        @Schema(description = "IN_PLACE 原表修改或 REBUILD 受控重建") TableChangeExecutionMode mode,
        @Schema(description = "DDL 原子性：TRANSACTIONAL_BATCH 事务批次或 ATOMIC_SINGLE_STATEMENT 原子单语句") TableDdlAtomicity atomicity,
        @Schema(description = "方言生成并冻结的只读 SQL 语句；客户端不能修改，也不能提交自定义 SQL") List<String> statements
) {
    static PhysicalTableChangeExecutionOptionResponse from(TableChangeExecutionOption option) {
        return new PhysicalTableChangeExecutionOptionResponse(option.mode(), option.atomicity(), option.statements());
    }
}
