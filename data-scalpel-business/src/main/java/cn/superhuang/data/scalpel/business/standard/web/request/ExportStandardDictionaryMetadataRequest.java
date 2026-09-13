package cn.superhuang.data.scalpel.business.standard.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "导出指定码表及其完整节点树")
public record ExportStandardDictionaryMetadataRequest(
        @Schema(description = "要导出的现有码表 UUID 列表，不能为空、不能重复且最多 200 张；按请求顺序导出，每张码表的全部节点按树的前序顺序写入 Excel") @NotEmpty @Size(max = 200) List<UUID> dictionaryIds
) {
}
