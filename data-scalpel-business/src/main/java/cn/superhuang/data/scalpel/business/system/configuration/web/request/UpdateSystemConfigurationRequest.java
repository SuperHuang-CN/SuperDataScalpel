package cn.superhuang.data.scalpel.business.system.configuration.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for updating the only editable property of a system configuration. */
@Schema(description = "修改一个数据库中已有且公开的系统配置值；不能新增配置或修改配置键、名称、类型和说明。当前代码声明为 internal 的配置不能通过此接口访问。")
public record UpdateSystemConfigurationRequest(
        @Schema(description = "新配置值，最长 4000 字符且不能为 null、空串或纯空白。普通 STRING 原样保存；INTEGER 必须是不带空白的 Java 32 位有符号十进制整数，保存为规范十进制文本；BOOLEAN 仅接受不带空白且不区分大小写的 true 或 false，保存为小写。panorama.map 虽声明为 STRING，但必须提交恰好包含 url、attribution、maxZoom 三个属性的受控 JSON 字符串。")
        @NotBlank(message = "配置值不能为空")
        @Size(max = 4000, message = "配置值不能超过 4000 个字符")
        String configValue
) {
}
