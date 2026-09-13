package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("批处理模型输入配置；按数组顺序全量读取一个或多个已发布纳管模型，每个模型产生一张 BOUNDED 逻辑表。节点不保存筛选条件、物理位置、连接或字段快照，运行准备时按当前模型生成权威快照。")

public record ModelInputConfiguration(
        @JsonPropertyDescription("按顺序读取的模型列表；至少一项，同一模型 UUID 不能重复。模型可以来自不同 JDBC 数据源，每项以模型不可修改的全局唯一 code 作为下游逻辑表名。")
        List<ModelInputSelection> models
) {
    public ModelInputConfiguration {
        models = models == null ? List.of() : List.copyOf(models);
    }

    public ModelInputConfiguration(String modelId) {
        this(modelId == null ? List.of() : List.of(new ModelInputSelection(modelId)));
    }
}
