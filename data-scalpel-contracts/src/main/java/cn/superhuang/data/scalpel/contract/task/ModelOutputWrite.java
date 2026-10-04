package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** One independently addressable model sink in a model output node. */
@JsonClassDescription("模型输出节点中的一次独立写入；按目标模型当前发布 Schema 显式 Cast 并投影字段。未映射来源字段忽略，节点不会修改模型定义、自动增加字段或检查物理表 Schema 是否与模型完全一致。")
public record ModelOutputWrite(
        @JsonPropertyDescription("本次写入的稳定 UUID 字符串，在所属节点内唯一；用于运行指标、诊断和流式查询/Checkpoint 身份，编辑其他字段时不应重新生成。")
        String writeId,
        @JsonPropertyDescription("要写出的上游 Canvas 逻辑表名；必须引用进入输出节点前已经存在的表。")
        String sourceTableName,
        @JsonPropertyDescription("接收写入的纳管模型 UUID 字符串；必须是已发布模型，关联数据源已启用且具有 STORAGE 用途。APPEND/UPSERT 可写 MANAGED 或 EXTERNAL，OVERWRITE 只允许 MANAGED。")
        String targetModelId,
        @JsonPropertyDescription("必填写入模式。APPEND 追加；OVERWRITE 仅限批处理 MANAGED 模型，未配置 batchWrite 时先清空再写入，配置后按其范围单事务删除并插入；UPSERT 在支持的目标数据库按模型字段顺序声明的完整主键匹配。")
        JdbcWriteMode writeMode,
        @JsonPropertyDescription("来源字段到模型字段的显式映射，至少一项。目标字段不能重复、自增或生成；目标中不可空且无默认值的可写字段必须全部覆盖。UPSERT 时必须覆盖完整模型主键。")
        List<JdbcColumnMapping> columnMappings,
        @JsonPropertyDescription("批处理原子提交选项；缺失保持旧 DIRECT 行为。条件使用模型目标字段，EXTERNAL 仍不允许任何 OVERWRITE。")
        BatchWriteOptions batchWrite
) {
    public ModelOutputWrite(String writeId, String sourceTableName, String targetModelId,
                            JdbcWriteMode writeMode, List<JdbcColumnMapping> columnMappings) {
        this(writeId, sourceTableName, targetModelId, writeMode, columnMappings, null);
    }
    public ModelOutputWrite {
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
    }
}
