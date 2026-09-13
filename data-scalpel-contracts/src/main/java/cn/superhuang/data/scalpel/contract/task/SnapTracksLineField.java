package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("从匹配网络线投影到吸附结果的只读属性；不参与路网匹配。")
public record SnapTracksLineField(
        @JsonPropertyDescription("网络线来源字段名；不能是 Geometry 或连接字段。")
        String sourceColumnName,
        @JsonPropertyDescription("写入结果表的字段名；必须与点来源字段及其他结果字段唯一。")
        String outputColumnName
) {
}
