package cn.superhuang.data.scalpel.business.standard.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "码表 Excel 在一个管理库事务中整批提交后的结果；文件中缺失的现有码表和节点不会删除，其名称、父节点、启停状态和说明不会被文件覆盖，但同级顺序压实可能间接调整缺失节点的 sortOrder")
public record StandardDictionaryImportResultResponse(
        @Schema(description = "本次新建的码表数量；每张新码表的初始内容版本为 1") int createdDictionaryCount,
        @Schema(description = "本次基础信息、启停状态或节点树实际变化的现有码表数量；每张只递增一次内容版本") int updatedDictionaryCount,
        @Schema(description = "本次新建的码表节点数量") int createdItemCount,
        @Schema(description = "工作簿中 action=UPDATE 的现有节点行数量；不包含因同级顺序压实而被间接调整 sortOrder 的未声明节点") int updatedItemCount,
        @Schema(description = "本次工作簿涉及的全部码表 UUID，包含新建、更新和未变化的码表，顺序与预览一致") List<UUID> dictionaryIds
) {
    public StandardDictionaryImportResultResponse {
        dictionaryIds = List.copyOf(dictionaryIds);
    }
}
