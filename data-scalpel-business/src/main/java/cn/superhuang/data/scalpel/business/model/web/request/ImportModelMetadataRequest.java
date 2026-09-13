package cn.superhuang.data.scalpel.business.model.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "把整份已校对内容创建为 MANAGED + DRAFT 模型。服务端先在管理事务外逐个实时确认目标表不存在，再在一个管理库事务中重校验目录和保存全部模型；任一失败不保存部分模型。不执行 DDL，也不保存或校验原 Excel 文件身份。")
public record ImportModelMetadataRequest(
        @Schema(description = "本批次统一使用的已启用 STORAGE JDBC 数据源 UUID；必须支持受管建表，TDengine 当前不支持。") @NotNull UUID targetStorageDataSourceId,
        @Schema(description = "已校对模型列表，1 到 200 个；模型编码和目标物理表名在批内分别不能重复，服务端重新校验目录、分层、编码、物理位置、字段和码表。") @NotEmpty @Size(max = 200) List<@Valid ImportModelMetadataModelRequest> models
) {

    public ImportModelMetadataRequest {
        models = models == null ? List.of() : List.copyOf(models);
    }
}
