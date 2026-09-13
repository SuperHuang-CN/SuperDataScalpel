package cn.superhuang.data.scalpel.business.filedataset.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "修改文件数据集显示信息和完整解析选项；已有内容可能锁定解析语义。")

public record UpdateFileDatasetRequest(
        @Schema(description = "文件数据集显示名称。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "完整替换后的解析选项；kind 必须保持与数据集 type 对应，选项已锁定时不能改变解析语义。")
        @jakarta.validation.constraints.NotNull @Valid FileDatasetParsingOptionsRequest parsingOptions,
        @Schema(description = "文件内容、来源、更新方式或使用限制说明；传空值表示清除。")
        @Size(max = 1000) String description
) {
}
