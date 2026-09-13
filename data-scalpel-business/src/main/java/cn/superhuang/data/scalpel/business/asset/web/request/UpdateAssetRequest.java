package cn.superhuang.data.scalpel.business.asset.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.asset.domain.AssetSensitivityLevel;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "完整替换资产的门户治理字段；不会修改来源资源、最近成功来源快照或发布状态。字符串会去除首尾空白，空白字符串按未设置处理。")

public record UpdateAssetRequest(
        @Schema(description = "ASSET 范围业务领域目录 UUID；必须指向现有目录。为空表示未分类，但未分类资产不能发布。")
        UUID directoryId,
        @Schema(description = "资产门户名称覆盖值，去除首尾空白后最长 100 个字符；为空或全空白时清除覆盖并使用来源名称。")
        @Size(max = 100) String portalName,
        @Schema(description = "资产门户摘要覆盖值，去除首尾空白后最长 1000 个字符；为空或全空白时清除覆盖并使用来源说明。")
        @Size(max = 1000) String portalSummary,
        @Schema(description = "用于门户检索和展示的标签。服务端去除每项首尾空白、忽略 null 或空白项并按首次出现顺序去重；规范化后最多 10 项，每项最长 30 个字符。null 或空列表均表示清除全部标签。")
        @Size(max = 10) List<@Size(max = 30) String> tags,
        @Schema(description = "资产业务负责人或责任部门名称，去除首尾空白后最长 100 个字符；为空时清除，但缺少负责人时不能发布。")
        @Size(max = 100) String ownerName,
        @Schema(description = "面向使用者的数据更新频率说明，去除首尾空白后最长 100 个字符；为空时清除，但缺少更新频率时不能发布。")
        @Size(max = 100) String updateFrequency,
        @Schema(description = "治理敏感级别；为空时清除，但缺少敏感级别时不能发布。PUBLIC、INTERNAL、SENSITIVE 均可出现在匿名门户，该值只用于醒目标识。")
        AssetSensitivityLevel sensitivityLevel,
        @Schema(description = "是否在资产门户中重点展示。")
        boolean featured
) {
}
