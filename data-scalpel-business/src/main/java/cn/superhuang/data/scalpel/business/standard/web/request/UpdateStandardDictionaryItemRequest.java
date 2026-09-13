package cn.superhuang.data.scalpel.business.standard.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "修改码表节点的码值、名称和说明；移动及启停使用独立命令")
public record UpdateStandardDictionaryItemRequest(
        @Schema(description = "客户端最近读取到的码表内容版本；与服务端当前版本不一致时返回 409，避免覆盖并发修改") @Min(1) int expectedVersion,
        @Schema(description = "节点业务码值；按码表 valueType 规范化后在整张码表内唯一。码表被模型字段或模板字段引用后不能修改任何节点 code") @NotBlank @Size(max = 256) String code,
        @Schema(description = "新的码值显示名称，最长 100 字符；保存时去除首尾空白") @NotBlank @Size(max = 100) String name,
        @Schema(description = "新的码值含义、统计口径或使用说明，最长 500 字符；为空或仅含空白时清除原说明") @Size(max = 500) String description
) {
}
