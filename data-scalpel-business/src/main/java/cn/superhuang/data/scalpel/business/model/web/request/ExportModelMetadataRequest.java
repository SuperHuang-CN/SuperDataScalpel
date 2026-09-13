package cn.superhuang.data.scalpel.business.model.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "按请求顺序导出模型和字段平台元数据；不包含物理数据、连接配置、统计快照、生命周期状态或运行状态。导出文件是可编辑快照，不保证在另一目标存储或当前配置变化后仍可直接导入。")
public record ExportModelMetadataRequest(
        @Schema(description = "要导出的 MANAGED 模型 UUID 列表，1 到 200 个且不能重复；任一模型不存在、为 EXTERNAL，或其目录路径无法解析时拒绝整次导出。无字段的 MANAGED 草稿可以导出，但文件需补充字段后才能重新导入。") @NotEmpty @Size(max = 200) List<@NotNull UUID> modelIds
) {
}
