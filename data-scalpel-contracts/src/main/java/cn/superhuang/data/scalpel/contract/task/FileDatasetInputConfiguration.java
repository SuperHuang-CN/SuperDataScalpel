package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("批处理文件数据集输入配置；从一个来源文件数据集中选择一张或多张已完成 Schema 解析的逻辑表，运行时严格按 Manifest 中的字段快照读取，不根据实际文件重新推断 Schema。")

public record FileDatasetInputConfiguration(
        @JsonPropertyDescription("来源文件数据集 UUID 字符串；草稿可为空，编译前必须存在且其来源文件已 READY。Canvas 不保存对象 Key、物化位置、解析参数或存储凭据。")
        String fileDatasetId,
        @JsonPropertyDescription("从该数据集中选择的逻辑表；至少一项，同一表 UUID 不能重复且必须属于 fileDatasetId。每项按自身稳定 code 产生一张 BOUNDED Canvas 表。")
        List<FileDatasetInputTableSelection> tables
) {
    public FileDatasetInputConfiguration {
        tables = tables == null ? List.of() : List.copyOf(tables);
    }

    public FileDatasetInputConfiguration(String fileDatasetTableId) {
        this("", fileDatasetTableId == null
                ? List.of() : List.of(new FileDatasetInputTableSelection(fileDatasetTableId)));
    }

    public String fileDatasetTableId() {
        return tables.isEmpty() ? null : tables.getFirst().fileDatasetTableId();
    }
}
