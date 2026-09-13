package cn.superhuang.data.scalpel.business.standard.web.request;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "按内容版本修改码表基础信息")
public record UpdateStandardDictionaryRequest(
        @Schema(description = "客户端最近读取到的码表内容版本；与服务端当前版本不一致时返回 409，避免覆盖并发修改") @Min(1) int expectedVersion,
        @Schema(description = "全局唯一的码表编码；服务端保存为大写，码表被字段引用后不可修改")
        @NotBlank
        @Pattern(
                regexp = "[A-Za-z][A-Za-z0-9_]{0,63}",
                message = "码表编码只能包含字母、数字和下划线，且必须以字母开头"
        )
        String code,
        @Schema(description = "新的码表显示名称，最长 100 字符；保存时去除首尾空白") @NotBlank @Size(max = 100) String name,
        @Schema(description = "所有节点 code 的逻辑数据类型；仅允许 STRING、INTEGER、LONG、DECIMAL 或 BOOLEAN，码表被模型字段或模板字段引用后不能修改") @NotNull PlatformDataType valueType,
        @Schema(description = "新的码表业务口径、适用范围或维护说明，最长 500 字符；为空或仅含空白时清除原说明") @Size(max = 500) String description
) {
}
