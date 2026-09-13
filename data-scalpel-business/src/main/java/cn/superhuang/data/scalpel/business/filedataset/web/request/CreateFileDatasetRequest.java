package cn.superhuang.data.scalpel.business.filedataset.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "创建用于统一管理同类上传文件、逻辑表和解析规则的文件数据集。")

public record CreateFileDatasetRequest(
        @Schema(description = "文件数据集显示名称。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "所属目录 UUID；位于根目录时为空。")
        UUID directoryId,
        @Schema(description = "文件数据集的逻辑格式类型，决定允许上传的文件格式和 parsingOptions 结构。")
        @NotNull FileDatasetType type,
        @Schema(description = "解析选项；kind 必须与 type 对应，并按 CSV、文本、JSON、空间文件或电子表格格式提供相应字段。")
        @NotNull @Valid FileDatasetParsingOptionsRequest parsingOptions,
        @Schema(description = "文件内容、来源、更新方式或使用限制说明；未填写时为空。")
        @Size(max = 1000) String description
) {
}
