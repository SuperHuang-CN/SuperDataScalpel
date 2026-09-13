package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("一元 Geometry 的有效性和坐标维度策略。PRESERVE_DIMENSION 校验输入且保留函数能够可靠支持的 XY/XYZ/XYM/XYZM；OUTPUT_XY 校验输入并仅将新结果显式降为 XY；LEGACY 使用旧 Sedona 表达式。字段缺失或为 null 也按 LEGACY 兼容行为处理。显式检查不会自动修复无效几何或转换 CRS。")
public enum GeometryUnaryPolicy {
    PRESERVE_DIMENSION,
    OUTPUT_XY,
    LEGACY
}
