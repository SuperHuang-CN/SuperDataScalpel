package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Controls whether a simple processor replaces its source logical table or appends a new one. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "mode")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProcessorOutput.ReplaceSource.class, name = "REPLACE_SOURCE"),
        @JsonSubTypes.Type(value = ProcessorOutput.CreateNewTable.class, name = "CREATE_NEW_TABLE")
})
@JsonClassDescription("简单转换操作的逻辑表输出方式，以 mode 判别结构：REPLACE_SOURCE 在输入 Map 原位置替换来源表；CREATE_NEW_TABLE 保留来源并在 Map 末尾追加新表。除沿用被替换来源自身的名称外，生成名称不得与输入或同节点其他输出冲突。")
public sealed interface ProcessorOutput permits ProcessorOutput.ReplaceSource, ProcessorOutput.CreateNewTable {

    String outputTableName();

    @JsonClassDescription("用处理结果替换来源逻辑表的 Map 项；除 RENAME 外不允许修改逻辑表名。")
    record ReplaceSource(
            @JsonPropertyDescription("可选的新逻辑表名；NULL 表示沿用来源表名。只有 RENAME 处理器允许提供新名称，其他简单处理器必须为 NULL；新名称不得与输入表或同节点其他输出冲突。")
            String outputTableName
    ) implements ProcessorOutput {
    }

    @JsonClassDescription("保留来源逻辑表，并把处理结果作为新逻辑表追加到输出 Map。")
    record CreateNewTable(
            @JsonPropertyDescription("必填的新 Canvas 逻辑表名；不得与任何输入表或同节点其他输出重名，后续节点通过该名称引用处理结果。")
            String outputTableName
    ) implements ProcessorOutput {
    }
}
