package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("一个逐行几何派生项。按 kind 从原始来源 Geometry 生成新 Geometry，不覆盖来源字段；NULL 输入返回 NULL。显式非 LEGACY 策略会拒绝无效几何和不受支持的维度组合，不执行修复或 CRS 转换；Empty 和点、线等退化结果仍作为结果保留。")
public record GeometryDerivation(
        @JsonPropertyDescription("派生项稳定 UUID，必须可解析为 UUID 且在本节点 derivations 内唯一；排序调整后保持不变，用于编辑和诊断定位。")
        String derivationId,
        @JsonPropertyDescription("派生函数：CENTROID 质心；POINT_ON_SURFACE 面内代表点；ENVELOPE 轴对齐包络；CONVEX_HULL 凸包；BOUNDARY 拓扑边界。点、共线或空输入可能产生低维或 Empty 结果。")
        GeometryDeriveKind kind,
        @JsonPropertyDescription("来源 Geometry 字段名，必须存在于 sourceTableName 对应的原始 Schema；不能引用本节点其他派生项的输出字段。")
        String sourceColumnName,
        @JsonPropertyDescription("追加的 Geometry 字段名，按大小写不敏感规则不能与来源字段或本节点其他输出字段重名。CENTROID、POINT_ON_SURFACE 声明 POINT，其余声明通用 GEOMETRY。")
        String outputColumnName,
        @JsonPropertyDescription("坐标处理策略。PRESERVE_DIMENSION 校验真实输入并保留受支持的 Z/M，其中 CENTROID、POINT_ON_SURFACE、ENVELOPE 仅支持 XY；OUTPUT_XY 校验输入并只把新结果降为 XY，原字段不变；LEGACY 或 null 使用旧 Sedona 表达式兼容路径。BOUNDARY 的显式策略不支持 GeometryCollection。")
        GeometryUnaryPolicy geometryPolicy
) {
    public GeometryDerivation(String derivationId, GeometryDeriveKind kind, String sourceColumnName, String outputColumnName) {
        this(derivationId, kind, sourceColumnName, outputColumnName, null);
    }
}
