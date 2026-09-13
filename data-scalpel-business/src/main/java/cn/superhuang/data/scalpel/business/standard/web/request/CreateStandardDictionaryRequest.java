package cn.superhuang.data.scalpel.business.standard.web.request;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "创建一张全局树形码表；初始启用且内容版本从 1 开始")
public record CreateStandardDictionaryRequest(
        @Schema(description = "全局唯一的码表编码；服务端保存为大写，创建后被字段引用时不可修改", example = "ADMIN_REGION")
        @NotBlank
        @Pattern(
                regexp = "[A-Za-z][A-Za-z0-9_]{0,63}",
                message = "码表编码只能包含字母、数字和下划线，且必须以字母开头"
        )
        String code,
        @Schema(description = "码表显示名称，最长 100 字符；保存时去除首尾空白") @NotBlank @Size(max = 100) String name,
        @Schema(description = "所有节点 code 的逻辑数据类型；仅允许 STRING、INTEGER、LONG、DECIMAL 或 BOOLEAN，并决定服务端的规范化与字段绑定兼容校验") @NotNull PlatformDataType valueType,
        @Schema(description = "码表业务口径、适用范围或维护说明，最长 500 字符；为空或仅含空白时保存为 null") @Size(max = 500) String description
) {
}
