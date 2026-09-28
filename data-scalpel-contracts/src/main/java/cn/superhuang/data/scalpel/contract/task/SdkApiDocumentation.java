package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("当前 TaskEngine 配套 SDK 的公开 Java API 说明，由 SDK 源码与 Javadoc 自动生成；只读，不启动语言服务或执行任务。")
public record SdkApiDocumentation(
        @JsonPropertyDescription("部署的 SDK 构建版本；SNAPSHOT 版本需结合 fingerprint 区分构建。") String version,
        @JsonPropertyDescription("版本及文档内容的 SHA-256 十六进制摘要，用于识别更新。") String fingerprint,
        @JsonPropertyDescription("SDK 公开类型，含公开嵌套类型；不含 Spark、JDK 或 Engine 内部 API。") List<ApiType> types
) {
    @JsonClassDescription("一个 SDK 公开类型及其声明的能力。")
    public record ApiType(
            @JsonPropertyDescription("Java 类型全限定名，唯一标识。") String name,
            @JsonPropertyDescription("类型短名称。") String simpleName,
            @JsonPropertyDescription("由 SDK 注释提供的中文用途分类。") String group,
            @JsonPropertyDescription("适用任务模式：BOTH 表示批流通用，BATCH 仅批处理，STREAMING 仅实时。") String mode,
            @JsonPropertyDescription("简短用途说明。") String summary,
            @JsonPropertyDescription("最短使用示例；空字符串表示未提供。示例绑定名需替换为实际任务资源。") String example,
            @JsonPropertyDescription("使用限制或副作用提示；没有时为空字符串。") String note,
            @JsonPropertyDescription("继承的接口全限定名；可链接到当前 SDK 中的接口。") List<String> parents,
            @JsonPropertyDescription("声明的公开方法、枚举常量及记录字段访问器；不重复展示 Object 通用方法。") List<ApiMember> members
    ) {}

    @JsonClassDescription("SDK 方法或常量的签名与说明；重载方法各占一条。")
    public record ApiMember(
            @JsonPropertyDescription("方法或常量名称。") String name,
            @JsonPropertyDescription("源码自动提取的签名，包含全部参数类型和名称；常量只有名称。") String signature,
            @JsonPropertyDescription("用途说明。") String summary,
            @JsonPropertyDescription("返回类型；方法无返回值时为 void，常量为所属类型。") String returnType,
            @JsonPropertyDescription("返回值含义，没有额外说明时为空字符串。") String returns,
            @JsonPropertyDescription("按声明顺序列出的参数；无参数或常量时为空数组。") List<ApiParameter> parameters,
            @JsonPropertyDescription("方法使用示例，没有时为空字符串。") String example,
            @JsonPropertyDescription("方法限制或副作用，没有时为空字符串。") String note,
            @JsonPropertyDescription("是否已废弃；废弃方法仍展示，避免隐藏历史 API。") boolean deprecated
    ) {}

    @JsonClassDescription("SDK 方法参数。")
    public record ApiParameter(
            @JsonPropertyDescription("源码中的参数名。") String name,
            @JsonPropertyDescription("Java 参数类型。") String type,
            @JsonPropertyDescription("参数用途、必要范围或默认行为。") String description
    ) {}
}
