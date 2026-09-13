package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.Map;

/** Completion data consumed by the shared API Studio script workbench. */
@JsonClassDescription("API Studio 脚本工作台的补全上下文；描述可用变量、受控成员、语法边界和当前数据源的安全元数据。")
public record ScriptCompletionResponse(
        @JsonPropertyDescription("脚本编辑器可补全的类及其成员说明。")
        Map<String, List<ScriptCompletionMethod>> clazzs,
        @JsonPropertyDescription("脚本运行上下文中可引用的变量及类型说明。")
        Map<String, String> variables,
        @JsonPropertyDescription("脚本语法、限制和安全边界说明。")
        Map<String, String> syntax,
        @JsonPropertyDescription("脚本可访问的数据源及安全数据库元数据说明。")
        Map<String, List<Map<String, Object>>> dbInfos,
        @JsonPropertyDescription("当前补全上下文对应的数据源 UUID 字符串；未选择数据源时为空。")
        String dataSourceId
) {

    @JsonClassDescription("脚本编辑器可补全的一个受控成员及其调用签名和用途。")
    public record ScriptCompletionMethod(
            @JsonPropertyDescription("可补全成员的种类，例如方法或属性，由脚本工作台用于选择展示和插入形式。")
            String type,
            @JsonPropertyDescription("脚本中引用该变量的名称。")
            String varName,
            @JsonPropertyDescription("调用成员后的结果类型名称。")
            String resultType,
            @JsonPropertyDescription("成员调用参数签名文本，用于编辑器展示或插入调用形式。")
            String params,
            @JsonPropertyDescription("面向脚本编写者的成员用途说明。")
            String docs
    ) {
    }
}
