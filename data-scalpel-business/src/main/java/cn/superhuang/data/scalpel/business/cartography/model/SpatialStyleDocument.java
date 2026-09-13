package cn.superhuang.data.scalpel.business.cartography.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Framework-free, JSON-serializable source document for an editable spatial style. */
@Schema(description = "空间数据服务的可编辑制图样式文档。服务端会按模型 Geometry 和字段能力校验并补齐默认值；GENERIC Geometry 不能使用该文档，只能上传 SLD。")
public record SpatialStyleDocument(
        @Schema(description = "样式协议版本；必须为 4。") int schemaVersion,
        @Schema(description = "必填要素渲染器；通过 type 区分 SINGLE_SYMBOL、UNIQUE_VALUE 和 CLASS_BREAKS。") Renderer renderer,
        @Schema(description = "标注配置；省略时生成 enabled=false 的默认配置。") Labeling labeling,
        @Schema(description = "样式生效的比例尺分母范围；省略时不限制。启用标注时必须与标注自身的比例尺范围存在交集。") ScaleRange scaleRange) {
    public static final int CURRENT_SCHEMA_VERSION = 4;

    public SpatialStyleDocument {
        labeling = labeling == null ? Labeling.disabled() : labeling;
        scaleRange = scaleRange == null ? ScaleRange.unlimited() : scaleRange;
    }

    public static SpatialStyleDocument defaults(GeometryFamily family) {
        return new SpatialStyleDocument(CURRENT_SCHEMA_VERSION,
                new SingleSymbolRenderer(SpatialSymbol.defaults(family)), Labeling.disabled(), ScaleRange.unlimited());
    }

    @Schema(description = "图层几何族：POINT 点、LINE 线、POLYGON 面、GENERIC 通用 Geometry；GENERIC 只支持上传 SLD。")
    public enum GeometryFamily { POINT, LINE, POLYGON, GENERIC }
    @Schema(description = "样式表达值类型：STRING 字符串、NUMBER 数值、BOOLEAN 布尔值。")
    public enum ValueType { STRING, NUMBER, BOOLEAN }
    @Schema(description = "分级方法说明：EQUAL_INTERVAL 等距、QUANTILE 分位数、MANUAL 手工。样式文档仍须提交已经计算好的 breaks 和对应规则。")
    public enum ClassificationMethod { EQUAL_INTERVAL, QUANTILE, MANUAL }
    @Schema(description = "数值分级视觉通道：COLOR 改变颜色，SIZE 改变点大小，WIDTH 改变线宽。点支持 COLOR/SIZE，线支持 COLOR/WIDTH，面只支持 COLOR。")
    public enum ClassBreakVisualChannel { COLOR, SIZE, WIDTH }
    @Schema(description = "唯一值空值处理：OTHER 把空值交给其他值规则，SEPARATE 使用独立 nullRule。")
    public enum NullHandling { OTHER, SEPARATE }
    @Schema(description = "点标记形状：CIRCLE 圆、SQUARE 方形、TRIANGLE 三角形、STAR 星形。")
    public enum MarkerShape { CIRCLE, SQUARE, TRIANGLE, STAR }
    @Schema(description = "线型：SOLID 实线、DASHED 虚线、DOTTED 点线。")
    public enum LinePattern { SOLID, DASHED, DOTTED }
    @Schema(description = "面图案：DIAGONAL 斜线、CROSS 交叉线、DOT 点。")
    public enum FillPattern { DIAGONAL, CROSS, DOT }
    @Schema(description = "点标注相对点符号的位置：TOP、BOTTOM、LEFT 或 RIGHT。")
    public enum PointLabelPosition { TOP, BOTTOM, LEFT, RIGHT }
    @Schema(description = "线标注放置方式：FOLLOW_LINE 沿线、HORIZONTAL 水平。")
    public enum LineLabelPlacement { FOLLOW_LINE, HORIZONTAL }

    @Schema(description = "OGC 比例尺分母范围；两个边界均为空表示不限制。")
    public record ScaleRange(
            @Schema(description = "最小比例尺分母；必须是有限正数，为空表示不设下界。") Double minScaleDenominator,
            @Schema(description = "最大比例尺分母；必须是有限正数，为空表示不设上界；两个边界同时存在时必须大于 minScaleDenominator。") Double maxScaleDenominator) {
        public static ScaleRange unlimited() { return new ScaleRange(null, null); }
        public ScaleRange intersect(ScaleRange other) {
            Double min = minScaleDenominator == null ? other.minScaleDenominator : minScaleDenominator;
            Double max = maxScaleDenominator == null ? other.maxScaleDenominator : maxScaleDenominator;
            if (minScaleDenominator != null && other.minScaleDenominator != null) min = Math.max(minScaleDenominator, other.minScaleDenominator);
            if (maxScaleDenominator != null && other.maxScaleDenominator != null) max = Math.min(maxScaleDenominator, other.maxScaleDenominator);
            return new ScaleRange(min, max);
        }
    }

    @Schema(description = "线符号外侧衬线。")
    public record LineCasing(
            @Schema(description = "衬线颜色，使用 #RRGGBB；省略时为 #FFFFFF。") String color,
            @Schema(description = "衬线不透明度，范围 0 到 1；省略时为 1。") Double opacity,
            @Schema(description = "衬线单侧宽度，范围 0.1 到 10 像素；省略时为 1。") Double width) {}
    @Schema(description = "面填充图案。")
    public record PolygonPattern(
            @Schema(description = "图案类型：DIAGONAL 斜线、CROSS 交叉线或 DOT 点；省略时为 DIAGONAL。") FillPattern type,
            @Schema(description = "图案颜色，使用 #RRGGBB；省略时为 #3F51C6。") String color,
            @Schema(description = "图案不透明度，范围 0 到 1；省略时为 0.8。") Double opacity,
            @Schema(description = "图案元素间距，范围 6 到 48 像素；省略时为 12。") Double spacing,
            @Schema(description = "线型图案的描边宽度，范围 0.5 到 4 像素；省略时为 1。") Double strokeWidth,
            @Schema(description = "点型图案的点直径，范围 1 到 8 像素；省略时为 2，DOT 图案中不得大于 spacing。") Double dotSize) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SingleSymbolRenderer.class, name = "SINGLE_SYMBOL"),
            @JsonSubTypes.Type(value = UniqueValueRenderer.class, name = "UNIQUE_VALUE"),
            @JsonSubTypes.Type(value = ClassBreaksRenderer.class, name = "CLASS_BREAKS")
    })
    @Schema(description = "空间图层的要素渲染策略；type 决定使用统一符号、按离散值分类或按数值区间分级。")
    public sealed interface Renderer permits SingleSymbolRenderer, UniqueValueRenderer, ClassBreaksRenderer {}

    @JsonTypeName("SINGLE_SYMBOL")
    @Schema(description = "所有非空要素使用同一符号的渲染器。")
    public record SingleSymbolRenderer(
            @Schema(description = "与图层几何类型一致的点、线或面符号；省略时生成该 Geometry 的默认符号。") SpatialSymbol symbol) implements Renderer {}

    @JsonTypeName("UNIQUE_VALUE")
    @Schema(description = "按一个模型字段的离散值选择符号的渲染器；可分别处理空值和未匹配值。")
    public record UniqueValueRenderer(
            @Schema(description = "用于分类的模型字段编码；字段必须存在且支持唯一值分类。") String fieldCode,
            @Schema(description = "分类值类型，必填且必须与字段能力返回的 valueType 一致。") ValueType valueType,
            @Schema(description = "内置色带；省略或 id 为空时使用 DATASCALPEL_12。支持 DATASCALPEL_12、BLUE_PURPLE、BLUES、GREENS、YELLOW_RED。") ColorRamp colorRamp,
            @Schema(description = "按字段规范化值精确匹配的规则，必须包含 1 到 50 条；值和规则 UUID 均不得重复，顺序用于图例。") List<UniqueValueRule> uniqueValueRules,
            @Schema(description = "空值处理；省略时为 OTHER。SEPARATE 会使用 nullRule，OTHER 会丢弃提交的 nullRule。") NullHandling nullHandling,
            @Schema(description = "空值独立规则；nullHandling=SEPARATE 时使用，省略会生成默认“空值”规则；其他模式下最终为空。") StyleRule nullRule,
            @Schema(description = "没有命中唯一值规则时使用的其他值规则；省略时没有兜底符号。") StyleRule elseRule
    ) implements Renderer {
        public UniqueValueRenderer { uniqueValueRules = immutable(uniqueValueRules); }
    }

    @JsonTypeName("CLASS_BREAKS")
    @Schema(description = "按一个数值模型字段的分级区间选择颜色、点大小或线宽的渲染器。")
    public record ClassBreaksRenderer(
            @Schema(description = "用于数值分级的模型字段编码；字段必须存在且支持数值分级。") String fieldCode,
            @Schema(description = "必填分级方法：EQUAL_INTERVAL 等距、QUANTILE 分位数或 MANUAL 手工断点；该字段记录方法，服务端仍使用请求中的 breaks，不在保存时重新计算。") ClassificationMethod classificationMethod,
            @Schema(description = "分级作用的视觉通道；省略时为 COLOR。点支持 COLOR/SIZE，线支持 COLOR/WIDTH，面只支持 COLOR。") ClassBreakVisualChannel visualChannel,
            @Schema(description = "仅 COLOR 通道使用的内置色带；省略或 id 为空时为 BLUE_PURPLE，非 COLOR 通道最终清空。") ColorRamp colorRamp,
            @Schema(description = "仅 SIZE/WIDTH 通道使用。SIZE 范围必须在 2 到 64 像素内，省略时 6 到 24；WIDTH 必须在 0.1 到 20 像素内，省略时 1 到 8；minimum 必须小于 maximum。") NumericRange sizeRange,
            @Schema(description = "0 到 8 个严格递增的普通十进制断点字符串；禁止科学计数法，服务端规范化尾零。N 个断点形成 N+1 个区间。") List<String> breaks,
            @Schema(description = "必须包含 breaks.size+1 条样式规则且最多 9 条；SIZE/WIDTH 通道会按规则顺序在 sizeRange 内等距覆盖符号大小或线宽。") List<StyleRule> classBreakRules,
            @Schema(description = "数值字段为空时使用的可选规则。") StyleRule nullRule
    ) implements Renderer {
        public ClassBreaksRenderer {
            breaks = immutable(breaks);
            classBreakRules = immutable(classBreakRules);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = PointSymbol.class, name = "POINT"),
            @JsonSubTypes.Type(value = LineSymbol.class, name = "LINE"),
            @JsonSubTypes.Type(value = PolygonSymbol.class, name = "POLYGON")
    })
    @Schema(description = "与图层 Geometry 类型匹配的制图符号；type 决定点标记、线样式或面填充结构。")
    public sealed interface SpatialSymbol permits PointSymbol, LineSymbol, PolygonSymbol {
        static SpatialSymbol defaults(GeometryFamily family) {
            return switch (family) {
                case POINT -> PointSymbol.defaults();
                case LINE -> LineSymbol.defaults();
                case POLYGON -> PolygonSymbol.defaults();
                case GENERIC -> throw new IllegalArgumentException("通用 Geometry 不支持可视化制图");
            };
        }
    }

    @JsonTypeName("POINT")
    @Schema(description = "点要素标记符号。")
    public record PointSymbol(
            @Schema(description = "标记形状；省略时为 CIRCLE。") MarkerShape shape,
            @Schema(description = "标记大小，范围 2 到 64 像素；省略时为 10。") Double size,
            @Schema(description = "填充颜色，使用 #RRGGBB；省略时为 #4F6BFF。") String fillColor,
            @Schema(description = "填充不透明度，范围 0 到 1；省略时为 0.85。") Double fillOpacity,
            @Schema(description = "轮廓颜色，使用 #RRGGBB；省略时为 #FFFFFF。") String outlineColor,
            @Schema(description = "轮廓不透明度，范围 0 到 1；省略时为 1。") Double outlineOpacity,
            @Schema(description = "轮廓宽度，范围 0 到 20 像素；省略时为 1。") Double outlineWidth)
            implements SpatialSymbol {
        public static PointSymbol defaults() {
            return new PointSymbol(MarkerShape.CIRCLE, 10d, "#4F6BFF", 0.85d, "#FFFFFF", 1d, 1d);
        }
    }

    @JsonTypeName("LINE")
    @Schema(description = "线要素符号。")
    public record LineSymbol(
            @Schema(description = "线颜色，使用 #RRGGBB；省略时为 #4F6BFF。") String color,
            @Schema(description = "线不透明度，范围 0 到 1；省略时为 0.9。") Double opacity,
            @Schema(description = "线宽，范围 0.1 到 20 像素；省略时为 2.5。") Double width,
            @Schema(description = "线型；省略时为 SOLID。") LinePattern pattern,
            @Schema(description = "可选外侧衬线。") LineCasing casing)
            implements SpatialSymbol {
        public static LineSymbol defaults() {
            return new LineSymbol("#4F6BFF", 0.9d, 2.5d, LinePattern.SOLID, null);
        }
    }

    @JsonTypeName("POLYGON")
    @Schema(description = "面要素填充和轮廓符号。")
    public record PolygonSymbol(
            @Schema(description = "填充颜色，使用 #RRGGBB；省略时为 #6F7DFF。") String fillColor,
            @Schema(description = "填充不透明度，范围 0 到 1；省略时为 0.35。") Double fillOpacity,
            @Schema(description = "轮廓颜色，使用 #RRGGBB；省略时为 #3F51C6。") String outlineColor,
            @Schema(description = "轮廓不透明度，范围 0 到 1；省略时为 1。") Double outlineOpacity,
            @Schema(description = "轮廓宽度，范围 0 到 20 像素；省略时为 1.5。") Double outlineWidth,
            @Schema(description = "轮廓线型；省略时为 SOLID。") LinePattern outlinePattern,
            @Schema(description = "可选面填充图案。") PolygonPattern pattern)
            implements SpatialSymbol {
        public static PolygonSymbol defaults() {
            return new PolygonSymbol("#6F7DFF", 0.35d, "#3F51C6", 1d, 1.5d, LinePattern.SOLID, null);
        }
    }

    @Schema(description = "用于符号尺寸插值的数值范围。")
    public record NumericRange(
            @Schema(description = "最小符号尺寸；范围取决于视觉通道，省略时使用通道默认值。") Double minimum,
            @Schema(description = "最大符号尺寸；范围取决于视觉通道，省略时使用通道默认值且必须大于 minimum。") Double maximum) {
        public static NumericRange pointSizeDefaults() { return new NumericRange(6d, 24d); }
        public static NumericRange lineWidthDefaults() { return new NumericRange(1d, 8d); }
    }

    @Schema(description = "内置色带选择。")
    public record ColorRamp(
            @Schema(description = "内置色带稳定 id：DATASCALPEL_12、BLUE_PURPLE、BLUES、GREENS 或 YELLOW_RED。") String id,
            @Schema(description = "是否反转色带颜色顺序。") boolean reversed) {}
    @Schema(description = "唯一值渲染中的一条精确匹配规则。")
    public record UniqueValueRule(
            @Schema(description = "文档内唯一的规则 UUID；省略时服务端生成。") String id,
            @Schema(description = "字段匹配值，最长 512 字符且规则间唯一。STRING 保留原文，BOOLEAN 规范化为 true/false，NUMBER 禁止科学计数法并规范化为普通十进制；空值应使用 nullRule。") String value,
            @Schema(description = "图例中显示的规则名称，最长 100 字符；省略或空白时使用规范化 value。") String label,
            @Schema(description = "命中该值时使用且必须符合图层 Geometry 的符号；省略时生成默认符号。") SpatialSymbol symbol) {}
    @Schema(description = "没有显式字段匹配值的通用样式规则。")
    public record StyleRule(
            @Schema(description = "文档内唯一的规则 UUID；省略时服务端生成。") String id,
            @Schema(description = "图例中显示的规则名称，最长 100 字符；省略时按所在位置生成“空值”“其他”或区间名称。") String label,
            @Schema(description = "规则使用且必须符合图层 Geometry 的符号；省略时生成默认符号。") SpatialSymbol symbol) {}

    @Schema(description = "要素文字标注配置。")
    public record Labeling(
            @Schema(description = "是否启用标注。") boolean enabled,
            @Schema(description = "作为标注正文的模型字段编码；启用标注时必填。") String fieldCode,
            @Schema(description = "字号，范围 8 到 48 像素；省略时为 12。") Double fontSize,
            @Schema(description = "是否使用粗体；省略时为 false。") Boolean bold,
            @Schema(description = "文字颜色，使用 #RRGGBB；省略时为 #26324A。") String color,
            @Schema(description = "文字光晕颜色，使用 #RRGGBB；省略时为 #FFFFFF。") String haloColor,
            @Schema(description = "文字光晕宽度，范围 0 到 5 像素；省略时为 1.5。") Double haloWidth,
            @Schema(description = "标注字段值前添加的固定文本，最长 50 字符；省略时为空字符串。") String prefix,
            @Schema(description = "标注字段值后添加的固定文本，最长 50 字符；省略时为空字符串。") String suffix,
            @Schema(description = "数值标注保留的小数位，范围 0 到 6；只允许 NUMBER 类型标注字段，其他类型必须为空。") Integer decimalPlaces,
            @Schema(description = "点标注相对点符号的位置；省略时为 TOP。") PointLabelPosition pointPosition,
            @Schema(description = "点标注与符号的间距，范围 0 到 64 像素；省略时为 6。") Double pointOffset,
            @Schema(description = "线标注沿线或水平放置；省略时为 FOLLOW_LINE。") LineLabelPlacement linePlacement,
            @Schema(description = "长线要素上是否重复显示标注；省略时为 true。") Boolean repeat,
            @Schema(description = "重复线标注之间的间距，范围 50 到 2000 像素；省略时为 300。") Double repeatDistance,
            @Schema(description = "面标注是否要求文字适合落在面内；省略时为 true。") Boolean polygonFit,
            @Schema(description = "是否允许标注互相重叠；省略时为 false。") Boolean allowOverlap,
            @Schema(description = "标注独立生效的比例尺分母范围。") ScaleRange scaleRange) {
        public Labeling {
            prefix = prefix == null ? "" : prefix;
            suffix = suffix == null ? "" : suffix;
            pointPosition = pointPosition == null ? PointLabelPosition.TOP : pointPosition;
            pointOffset = pointOffset == null ? 6d : pointOffset;
            linePlacement = linePlacement == null ? LineLabelPlacement.FOLLOW_LINE : linePlacement;
            repeat = repeat == null ? true : repeat;
            repeatDistance = repeatDistance == null ? 300d : repeatDistance;
            polygonFit = polygonFit == null ? true : polygonFit;
            allowOverlap = allowOverlap == null ? false : allowOverlap;
            scaleRange = scaleRange == null ? ScaleRange.unlimited() : scaleRange;
        }
        public static Labeling disabled() {
            return new Labeling(false, null, 12d, false, "#26324A", "#FFFFFF", 1.5d,
                    "", "", null, PointLabelPosition.TOP, 6d, LineLabelPlacement.FOLLOW_LINE,
                    true, 300d, true, false, ScaleRange.unlimited());
        }
    }

    @Schema(description = "可用于空间样式的模型字段能力。")
    public record Field(
            @Schema(description = "模型字段编码。") String code,
            @Schema(description = "模型字段显示名称。") String name,
            @Schema(description = "平台字段类型名称。") String dataType,
            @Schema(description = "样式表达使用的值类型。") ValueType valueType,
            @Schema(description = "字段是否支持唯一值分类。") boolean uniqueValueSupported,
            @Schema(description = "字段是否支持数值分级。") boolean classBreaksSupported,
            @Schema(description = "字段是否支持文字标注。") boolean labelSupported) {}

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
