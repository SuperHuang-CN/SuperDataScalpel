package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;

@JsonClassDescription("Canvas 逻辑表中的一个有序字段定义；使用平台类型表达结构，并为几何字段携带专用空间类型信息。")
public record CanvasColumnSchema(
        @JsonPropertyDescription("逻辑或物理字段名，在所属 CanvasTableSchema 中唯一。")
        String name,
        @JsonPropertyDescription("字段的平台数据类型。")
        PlatformDataType fieldType,
        @JsonPropertyDescription("字符或二进制最大长度；不适用时为空。")
        Integer length,
        @JsonPropertyDescription("数值总有效位数；不适用时为空。")
        Integer precision,
        @JsonPropertyDescription("数值小数位数；不适用时为空。")
        Integer scale,
        @JsonPropertyDescription("结果是否允许为空。")
        boolean nullable,
        @JsonPropertyDescription("数据库或来源声明的默认值表达式；没有默认值时为空，不保证可由 Canvas 直接执行。")
        String defaultValue,
        @JsonPropertyDescription("字段是否由数据库自动递增生成。")
        boolean autoIncrement,
        @JsonPropertyDescription("字段是否为数据库生成列。")
        boolean generated,
        @JsonPropertyDescription("数据库字段或表的注释；未提供时为空。")
        String comment,
        @JsonPropertyDescription("GEOMETRY 字段的几何类型、坐标维度和坐标参考系定义；非几何字段必须为空。")
        GeometryTypeDefinition geometry
) {
    public CanvasColumnSchema(
            String name,
            PlatformDataType fieldType,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable,
            String defaultValue,
            boolean autoIncrement,
            boolean generated,
            String comment
    ) {
        this(
                name, fieldType, length, precision, scale, nullable, defaultValue,
                autoIncrement, generated, comment, null
        );
    }

    public CanvasColumnSchema {
        if (fieldType == PlatformDataType.GEOMETRY) {
            if (geometry == null) {
                throw new IllegalArgumentException("GEOMETRY requires a geometry definition");
            }
            if (length != null || precision != null || scale != null) {
                throw new IllegalArgumentException("GEOMETRY does not accept scalar type parameters");
            }
        } else if (geometry != null) {
            throw new IllegalArgumentException("Only GEOMETRY accepts a geometry definition");
        }
    }
}
