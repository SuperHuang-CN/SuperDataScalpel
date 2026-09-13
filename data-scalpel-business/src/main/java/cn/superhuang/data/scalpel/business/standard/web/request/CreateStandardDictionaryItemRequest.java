package cn.superhuang.data.scalpel.business.standard.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "在码表的任意层级创建一个码值节点")
public record CreateStandardDictionaryItemRequest(
        @Schema(description = "客户端最近读取到的码表内容版本；与服务端当前版本不一致时返回 409，避免覆盖并发修改") @Min(1) int expectedVersion,
        @Schema(description = "父节点 UUID；为空表示创建一个根节点，非空时必须指向同一码表中的现有节点") UUID parentId,
        @Schema(description = "新节点在目标同级列表中的零基位置；为空表示追加到末尾，插入后服务端压实同级 sortOrder") @Min(0) Integer targetIndex,
        @Schema(description = "节点业务码值，最长 256 字符且在整张码表内唯一；STRING 去除首尾空白并保留大小写，INTEGER/LONG 保存标准十进制，DECIMAL 拒绝科学计数法并去除无意义尾零，BOOLEAN 规范化为 true 或 false") @NotBlank @Size(max = 256) String code,
        @Schema(description = "码值显示名称，最长 100 字符；保存时去除首尾空白") @NotBlank @Size(max = 100) String name,
        @Schema(description = "节点自身是否启用；实际可用还要求码表及全部祖先均启用") @NotNull Boolean enabled,
        @Schema(description = "码值含义、统计口径或使用说明，最长 500 字符；为空或仅含空白时保存为 null") @Size(max = 500) String description
) {
}
