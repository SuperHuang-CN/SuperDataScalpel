package cn.superhuang.data.scalpel.business.cartography.validation;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialColorRamps;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.ClassBreaksRenderer;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.Field;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.GeometryFamily;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.Labeling;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.LineSymbol;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.PointSymbol;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.PolygonSymbol;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.SingleSymbolRenderer;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.SpatialSymbol;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.StyleRule;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.UniqueValueRenderer;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument.UniqueValueRule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Normalizes and validates a V4 cartography document without depending on Spring or persistence. */
public final class SpatialStyleDocumentValidator {

    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");

    public SpatialStyleDocument validate(SpatialStyleDocument input, GeometryFamily family, List<Field> availableFields) {
        if (family == GeometryFamily.GENERIC) throw invalid("通用 Geometry 仅支持上传 SLD");
        if (input == null || input.renderer() == null) throw invalid("样式文档和 Renderer 不能为空");
        if (input.schemaVersion() != SpatialStyleDocument.CURRENT_SCHEMA_VERSION) {
            throw invalid("不支持的样式文档版本：" + input.schemaVersion());
        }
        Map<String, Field> fields = availableFields == null ? Map.of() : availableFields.stream()
                .collect(Collectors.toMap(Field::code, Function.identity(), (left, right) -> left));
        SpatialStyleDocument.Renderer renderer = switch (input.renderer()) {
            case SingleSymbolRenderer single -> single(single, family);
            case UniqueValueRenderer unique -> unique(unique, family, fields);
            case ClassBreaksRenderer classBreaks -> classBreaks(classBreaks, family, fields);
        };
        Labeling labeling = labeling(input.labeling(), fields);
        SpatialStyleDocument.ScaleRange scale = scaleRange(input.scaleRange());
        if (labeling.enabled()) scaleRange(scale.intersect(labeling.scaleRange()));
        return new SpatialStyleDocument(SpatialStyleDocument.CURRENT_SCHEMA_VERSION, renderer, labeling, scale);
    }

    private static SingleSymbolRenderer single(SingleSymbolRenderer renderer, GeometryFamily family) {
        return new SingleSymbolRenderer(symbol(renderer.symbol(), family));
    }

    private static UniqueValueRenderer unique(
            UniqueValueRenderer renderer, GeometryFamily family, Map<String, Field> fields
    ) {
        Field field = requireField(renderer.fieldCode(), fields, "唯一值字段");
        if (!field.uniqueValueSupported()) throw invalid("字段不支持唯一值分类：" + field.code());
        if (renderer.valueType() == null || renderer.valueType() != field.valueType()) {
            throw invalid("唯一值字段类型与 Renderer 不一致");
        }
        if (renderer.uniqueValueRules().isEmpty() || renderer.uniqueValueRules().size() > 50) {
            throw invalid("唯一值分类必须包含 1–50 条规则");
        }
        Set<String> values = new HashSet<>();
        Set<String> ids = new HashSet<>();
        List<UniqueValueRule> rules = new ArrayList<>();
        for (UniqueValueRule rule : renderer.uniqueValueRules()) {
            if (rule == null) throw invalid("唯一值规则不能为空");
            String value = value(rule.value(), renderer.valueType());
            if (!values.add(value)) throw invalid("唯一值规则存在重复值：" + value);
            rules.add(new UniqueValueRule(id(rule.id(), ids), value, label(rule.label(), value),
                    symbol(rule.symbol(), family)));
        }
        SpatialStyleDocument.NullHandling nullHandling = renderer.nullHandling() == null
                ? SpatialStyleDocument.NullHandling.OTHER : renderer.nullHandling();
        StyleRule nullRule = nullHandling == SpatialStyleDocument.NullHandling.SEPARATE
                ? rule(renderer.nullRule(), "空值", family, ids) : null;
        StyleRule elseRule = renderer.elseRule() == null ? null : rule(renderer.elseRule(), "其他", family, ids);
        return new UniqueValueRenderer(field.code(), field.valueType(),
                colorRamp(renderer.colorRamp(), "DATASCALPEL_12"), rules, nullHandling, nullRule, elseRule);
    }

    private static ClassBreaksRenderer classBreaks(
            ClassBreaksRenderer renderer, GeometryFamily family, Map<String, Field> fields
    ) {
        Field field = requireField(renderer.fieldCode(), fields, "分级字段");
        if (!field.classBreaksSupported()) throw invalid("字段不支持数值分级：" + field.code());
        if (renderer.classificationMethod() == null) throw invalid("分级方法不能为空");
        SpatialStyleDocument.ClassBreakVisualChannel channel = validateChannel(renderer.visualChannel(), family);
        if (renderer.breaks().size() > 8) throw invalid("数值分级最多支持 9 级");
        List<String> breaks = new ArrayList<>();
        BigDecimal previous = null;
        for (String raw : renderer.breaks()) {
            BigDecimal current = decimal(raw, "分级断点");
            if (previous != null && current.compareTo(previous) <= 0) throw invalid("分级断点必须严格递增");
            breaks.add(current.stripTrailingZeros().toPlainString());
            previous = current;
        }
        if (renderer.classBreakRules().size() != breaks.size() + 1 || renderer.classBreakRules().size() > 9) {
            throw invalid("分级规则数量必须比分级断点多 1");
        }
        List<StyleRule> rules = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < renderer.classBreakRules().size(); index++) {
            rules.add(rule(renderer.classBreakRules().get(index), classLabel(breaks, index), family, ids));
        }
        StyleRule nullRule = renderer.nullRule() == null ? null : rule(renderer.nullRule(), "空值", family, ids);
        SpatialStyleDocument.ColorRamp ramp = channel == SpatialStyleDocument.ClassBreakVisualChannel.COLOR
                ? colorRamp(renderer.colorRamp(), "BLUE_PURPLE") : null;
        SpatialStyleDocument.NumericRange range = switch (channel) {
            case SIZE -> numericRange(renderer.sizeRange(), 2d, 64d,
                    SpatialStyleDocument.NumericRange.pointSizeDefaults(), "点大小");
            case WIDTH -> numericRange(renderer.sizeRange(), 0.1d, 20d,
                    SpatialStyleDocument.NumericRange.lineWidthDefaults(), "线宽");
            case COLOR -> null;
        };
        return new ClassBreaksRenderer(field.code(), renderer.classificationMethod(), channel, ramp, range,
                breaks, materializeMagnitude(rules, channel, range), nullRule);
    }

    private static List<StyleRule> materializeMagnitude(
            List<StyleRule> rules,
            SpatialStyleDocument.ClassBreakVisualChannel channel,
            SpatialStyleDocument.NumericRange range
    ) {
        if (channel == SpatialStyleDocument.ClassBreakVisualChannel.COLOR) return rules;
        List<StyleRule> materialized = new ArrayList<>(rules.size());
        for (int index = 0; index < rules.size(); index++) {
            StyleRule rule = rules.get(index);
            double magnitude = rules.size() == 1
                    ? (range.minimum() + range.maximum()) / 2d
                    : range.minimum() + (range.maximum() - range.minimum()) * index / (rules.size() - 1d);
            SpatialSymbol symbol = switch (channel) {
                case SIZE -> {
                    PointSymbol point = (PointSymbol) rule.symbol();
                    yield new PointSymbol(point.shape(), magnitude, point.fillColor(), point.fillOpacity(),
                            point.outlineColor(), point.outlineOpacity(), point.outlineWidth());
                }
                case WIDTH -> {
                    LineSymbol line = (LineSymbol) rule.symbol();
                    yield new LineSymbol(line.color(), line.opacity(), magnitude, line.pattern(), line.casing());
                }
                case COLOR -> throw new IllegalStateException("颜色分级不应物化尺寸");
            };
            materialized.add(new StyleRule(rule.id(), rule.label(), symbol));
        }
        return materialized;
    }

    private static SpatialStyleDocument.ClassBreakVisualChannel validateChannel(
            SpatialStyleDocument.ClassBreakVisualChannel channel, GeometryFamily family
    ) {
        SpatialStyleDocument.ClassBreakVisualChannel resolved = channel == null
                ? SpatialStyleDocument.ClassBreakVisualChannel.COLOR : channel;
        boolean valid = switch (family) {
            case POINT -> resolved == SpatialStyleDocument.ClassBreakVisualChannel.COLOR
                    || resolved == SpatialStyleDocument.ClassBreakVisualChannel.SIZE;
            case LINE -> resolved == SpatialStyleDocument.ClassBreakVisualChannel.COLOR
                    || resolved == SpatialStyleDocument.ClassBreakVisualChannel.WIDTH;
            case POLYGON -> resolved == SpatialStyleDocument.ClassBreakVisualChannel.COLOR;
            case GENERIC -> false;
        };
        if (!valid) throw invalid("当前 Geometry 不支持分级表达方式：" + resolved);
        return resolved;
    }

    private static SpatialStyleDocument.NumericRange numericRange(
            SpatialStyleDocument.NumericRange input, double minimum, double maximum,
            SpatialStyleDocument.NumericRange fallback, String label
    ) {
        double low = number(input == null ? null : input.minimum(), fallback.minimum(), minimum, maximum,
                label + "最小值");
        double high = number(input == null ? null : input.maximum(), fallback.maximum(), minimum, maximum,
                label + "最大值");
        if (low >= high) throw invalid(label + "最小值必须小于最大值");
        return new SpatialStyleDocument.NumericRange(low, high);
    }

    private static Labeling labeling(Labeling input, Map<String, Field> fields) {
        if (input == null) return Labeling.disabled();
        Field field = input.enabled() ? requireField(input.fieldCode(), fields, "标注字段")
                : input.fieldCode() == null ? null : fields.get(input.fieldCode());
        if (field != null && !field.labelSupported()) throw invalid("字段不支持文字标注：" + field.code());
        if (input.prefix().length() > 50 || input.suffix().length() > 50) throw invalid("标注前后缀不能超过 50 个字符");
        if (input.decimalPlaces() != null && (input.decimalPlaces() < 0 || input.decimalPlaces() > 6
                || field == null || field.valueType() != SpatialStyleDocument.ValueType.NUMBER)) {
            throw invalid("只有数值标注字段可设置 0–6 位小数");
        }
        return new Labeling(input.enabled(), field == null ? input.fieldCode() : field.code(), number(input.fontSize(), 12d, 8d, 48d, "标注字号"),
                Boolean.TRUE.equals(input.bold()), color(input.color(), "#26324A", "标注颜色"),
                color(input.haloColor(), "#FFFFFF", "标注描边颜色"),
                number(input.haloWidth(), 1.5d, 0d, 5d, "标注描边宽度"),
                input.prefix(), input.suffix(), input.decimalPlaces(), input.pointPosition(),
                number(input.pointOffset(), 6d, 0d, 64d, "点标注偏移"), input.linePlacement(), input.repeat(),
                number(input.repeatDistance(), 300d, 50d, 2000d, "重复标注间距"), input.polygonFit(),
                input.allowOverlap(), scaleRange(input.scaleRange()));
    }

    private static SpatialStyleDocument.ScaleRange scaleRange(SpatialStyleDocument.ScaleRange input) {
        if (input == null) return SpatialStyleDocument.ScaleRange.unlimited();
        Double min = input.minScaleDenominator(), max = input.maxScaleDenominator();
        if (min != null && (!Double.isFinite(min) || min <= 0)
                || max != null && (!Double.isFinite(max) || max <= 0)) throw invalid("比例尺分母必须为有限正数");
        if (min != null && max != null && min >= max) throw invalid("比例尺最小分母必须小于最大分母，整体与标注范围必须有交集");
        return input;
    }

    private static SpatialSymbol symbol(SpatialSymbol input, GeometryFamily family) {
        if (input == null) return SpatialSymbol.defaults(family);
        return switch (family) {
            case POINT -> {
                if (!(input instanceof PointSymbol point)) throw invalid("点图层只能使用 PointSymbol");
                yield point(point);
            }
            case LINE -> {
                if (!(input instanceof LineSymbol line)) throw invalid("线图层只能使用 LineSymbol");
                yield line(line);
            }
            case POLYGON -> {
                if (!(input instanceof PolygonSymbol polygon)) throw invalid("面图层只能使用 PolygonSymbol");
                yield polygon(polygon);
            }
            case GENERIC -> throw invalid("通用 Geometry 不支持可视化制图");
        };
    }

    private static PointSymbol point(PointSymbol source) {
        PointSymbol fallback = PointSymbol.defaults();
        return new PointSymbol(source.shape() == null ? fallback.shape() : source.shape(),
                number(source.size(), fallback.size(), 2d, 64d, "点大小"),
                color(source.fillColor(), fallback.fillColor(), "填充颜色"),
                number(source.fillOpacity(), fallback.fillOpacity(), 0d, 1d, "填充透明度"),
                color(source.outlineColor(), fallback.outlineColor(), "边框颜色"),
                number(source.outlineOpacity(), fallback.outlineOpacity(), 0d, 1d, "边框透明度"),
                number(source.outlineWidth(), fallback.outlineWidth(), 0d, 20d, "边框宽度"));
    }

    private static LineSymbol line(LineSymbol source) {
        LineSymbol fallback = LineSymbol.defaults();
        return new LineSymbol(color(source.color(), fallback.color(), "线颜色"),
                number(source.opacity(), fallback.opacity(), 0d, 1d, "线透明度"),
                number(source.width(), fallback.width(), 0.1d, 20d, "线宽"),
                source.pattern() == null ? fallback.pattern() : source.pattern(), casing(source.casing()));
    }

    private static PolygonSymbol polygon(PolygonSymbol source) {
        PolygonSymbol fallback = PolygonSymbol.defaults();
        return new PolygonSymbol(color(source.fillColor(), fallback.fillColor(), "填充颜色"),
                number(source.fillOpacity(), fallback.fillOpacity(), 0d, 1d, "填充透明度"),
                color(source.outlineColor(), fallback.outlineColor(), "边框颜色"),
                number(source.outlineOpacity(), fallback.outlineOpacity(), 0d, 1d, "边框透明度"),
                number(source.outlineWidth(), fallback.outlineWidth(), 0d, 20d, "边框宽度"),
                source.outlinePattern() == null ? fallback.outlinePattern() : source.outlinePattern(), pattern(source.pattern()));
    }

    private static SpatialStyleDocument.LineCasing casing(SpatialStyleDocument.LineCasing input) {
        if (input == null) return null;
        return new SpatialStyleDocument.LineCasing(color(input.color(), "#FFFFFF", "外描边颜色"),
                number(input.opacity(), 1d, 0d, 1d, "外描边透明度"), number(input.width(), 1d, 0.1d, 10d, "外描边单侧宽度"));
    }

    private static SpatialStyleDocument.PolygonPattern pattern(SpatialStyleDocument.PolygonPattern input) {
        if (input == null) return null;
        double spacing = number(input.spacing(), 12d, 6d, 48d, "图案平铺单元");
        double dot = number(input.dotSize(), 2d, 1d, 8d, "图案点径");
        SpatialStyleDocument.FillPattern type = input.type() == null ? SpatialStyleDocument.FillPattern.DIAGONAL : input.type();
        if (type == SpatialStyleDocument.FillPattern.DOT && dot > spacing) throw invalid("图案点径不能大于平铺单元");
        return new SpatialStyleDocument.PolygonPattern(type, color(input.color(), "#3F51C6", "图案颜色"),
                number(input.opacity(), 0.8d, 0d, 1d, "图案透明度"), spacing,
                number(input.strokeWidth(), 1d, 0.5d, 4d, "图案线宽"), dot);
    }

    private static StyleRule rule(StyleRule rule, String fallbackLabel, GeometryFamily family, Set<String> ids) {
        if (rule == null) return new StyleRule(id(null, ids), fallbackLabel, SpatialSymbol.defaults(family));
        return new StyleRule(id(rule.id(), ids), label(rule.label(), fallbackLabel), symbol(rule.symbol(), family));
    }

    private static Field requireField(String code, Map<String, Field> fields, String label) {
        if (code == null || code.isBlank()) throw invalid(label + "不能为空");
        Field field = fields.get(code.trim());
        if (field == null) throw invalid(label + "不存在或已失效：" + code.trim());
        return field;
    }

    private static String value(String raw, SpatialStyleDocument.ValueType type) {
        if (raw == null) throw invalid("唯一值不能为空，空值请使用空值规则");
        if (raw.length() > 512) throw invalid("唯一值文本不能超过 512 个字符");
        return switch (type) {
            case STRING -> raw;
            case BOOLEAN -> {
                String normalized = raw.trim().toLowerCase(Locale.ROOT);
                if (!"true".equals(normalized) && !"false".equals(normalized)) {
                    throw invalid("布尔唯一值必须是 true 或 false");
                }
                yield normalized;
            }
            case NUMBER -> decimal(raw, "数值唯一值").stripTrailingZeros().toPlainString();
        };
    }

    private static BigDecimal decimal(String raw, String label) {
        try {
            if (raw == null || raw.isBlank()) throw new NumberFormatException();
            if (raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) throw invalid(label + "不能使用科学计数法");
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException exception) {
            throw invalid(label + "必须是有限数字");
        }
    }

    private static String classLabel(List<String> breaks, int index) {
        if (breaks.isEmpty()) return "全部数值";
        if (index == 0) return "≤ " + breaks.getFirst();
        if (index == breaks.size()) return "> " + breaks.getLast();
        return breaks.get(index - 1) + " – " + breaks.get(index);
    }

    private static String id(String value, Set<String> ids) {
        String normalized;
        try {
            normalized = value == null || value.isBlank() ? UUID.randomUUID().toString()
                    : UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException exception) {
            throw invalid("规则 ID 必须是 UUID");
        }
        if (!ids.add(normalized)) throw invalid("规则 ID 不能重复：" + normalized);
        return normalized;
    }

    private static SpatialStyleDocument.ColorRamp colorRamp(SpatialStyleDocument.ColorRamp value, String fallbackId) {
        String id = value == null || value.id() == null || value.id().isBlank() ? fallbackId : value.id().trim();
        if (!SpatialColorRamps.contains(id)) throw invalid("不支持的内置色带：" + id);
        return new SpatialStyleDocument.ColorRamp(id, value != null && value.reversed());
    }

    private static String label(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        if (normalized.length() > 100) throw invalid("规则名称不能超过 100 个字符");
        return normalized;
    }

    private static String color(String value, String fallback, String label) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!COLOR.matcher(normalized).matches()) throw invalid(label + "必须是 #RRGGBB 格式");
        return normalized;
    }

    private static double number(Double value, Double fallback, double minimum, double maximum, String label) {
        double normalized = value == null ? fallback : value;
        if (!Double.isFinite(normalized) || normalized < minimum || normalized > maximum) {
            throw invalid(label + "必须在 " + minimum + "–" + maximum + " 范围内");
        }
        return normalized;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
