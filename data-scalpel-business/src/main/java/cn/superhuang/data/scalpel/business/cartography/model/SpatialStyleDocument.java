package cn.superhuang.data.scalpel.business.cartography.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.List;

/** Framework-free, JSON-serializable source document for an editable spatial style. */
public record SpatialStyleDocument(int schemaVersion, Renderer renderer, Labeling labeling, ScaleRange scaleRange) {
    public static final int CURRENT_SCHEMA_VERSION = 4;

    public SpatialStyleDocument {
        labeling = labeling == null ? Labeling.disabled() : labeling;
        scaleRange = scaleRange == null ? ScaleRange.unlimited() : scaleRange;
    }

    public static SpatialStyleDocument defaults(GeometryFamily family) {
        return new SpatialStyleDocument(CURRENT_SCHEMA_VERSION,
                new SingleSymbolRenderer(SpatialSymbol.defaults(family)), Labeling.disabled(), ScaleRange.unlimited());
    }

    public enum GeometryFamily { POINT, LINE, POLYGON, GENERIC }
    public enum ValueType { STRING, NUMBER, BOOLEAN }
    public enum ClassificationMethod { EQUAL_INTERVAL, QUANTILE, MANUAL }
    public enum ClassBreakVisualChannel { COLOR, SIZE, WIDTH }
    public enum NullHandling { OTHER, SEPARATE }
    public enum MarkerShape { CIRCLE, SQUARE, TRIANGLE, STAR }
    public enum LinePattern { SOLID, DASHED, DOTTED }
    public enum FillPattern { DIAGONAL, CROSS, DOT }
    public enum PointLabelPosition { TOP, BOTTOM, LEFT, RIGHT }
    public enum LineLabelPlacement { FOLLOW_LINE, HORIZONTAL }

    public record ScaleRange(Double minScaleDenominator, Double maxScaleDenominator) {
        public static ScaleRange unlimited() { return new ScaleRange(null, null); }
        public ScaleRange intersect(ScaleRange other) {
            Double min = minScaleDenominator == null ? other.minScaleDenominator : minScaleDenominator;
            Double max = maxScaleDenominator == null ? other.maxScaleDenominator : maxScaleDenominator;
            if (minScaleDenominator != null && other.minScaleDenominator != null) min = Math.max(minScaleDenominator, other.minScaleDenominator);
            if (maxScaleDenominator != null && other.maxScaleDenominator != null) max = Math.min(maxScaleDenominator, other.maxScaleDenominator);
            return new ScaleRange(min, max);
        }
    }

    public record LineCasing(String color, Double opacity, Double width) {}
    public record PolygonPattern(FillPattern type, String color, Double opacity, Double spacing,
                                 Double strokeWidth, Double dotSize) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SingleSymbolRenderer.class, name = "SINGLE_SYMBOL"),
            @JsonSubTypes.Type(value = UniqueValueRenderer.class, name = "UNIQUE_VALUE"),
            @JsonSubTypes.Type(value = ClassBreaksRenderer.class, name = "CLASS_BREAKS")
    })
    public sealed interface Renderer permits SingleSymbolRenderer, UniqueValueRenderer, ClassBreaksRenderer {}

    @JsonTypeName("SINGLE_SYMBOL")
    public record SingleSymbolRenderer(SpatialSymbol symbol) implements Renderer {}

    @JsonTypeName("UNIQUE_VALUE")
    public record UniqueValueRenderer(
            String fieldCode,
            ValueType valueType,
            ColorRamp colorRamp,
            List<UniqueValueRule> uniqueValueRules,
            NullHandling nullHandling,
            StyleRule nullRule,
            StyleRule elseRule
    ) implements Renderer {
        public UniqueValueRenderer { uniqueValueRules = immutable(uniqueValueRules); }
    }

    @JsonTypeName("CLASS_BREAKS")
    public record ClassBreaksRenderer(
            String fieldCode,
            ClassificationMethod classificationMethod,
            ClassBreakVisualChannel visualChannel,
            ColorRamp colorRamp,
            NumericRange sizeRange,
            List<String> breaks,
            List<StyleRule> classBreakRules,
            StyleRule nullRule
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
    public record PointSymbol(MarkerShape shape, Double size, String fillColor, Double fillOpacity,
                              String outlineColor, Double outlineOpacity, Double outlineWidth)
            implements SpatialSymbol {
        public static PointSymbol defaults() {
            return new PointSymbol(MarkerShape.CIRCLE, 10d, "#4F6BFF", 0.85d, "#FFFFFF", 1d, 1d);
        }
    }

    @JsonTypeName("LINE")
    public record LineSymbol(String color, Double opacity, Double width, LinePattern pattern, LineCasing casing)
            implements SpatialSymbol {
        public static LineSymbol defaults() {
            return new LineSymbol("#4F6BFF", 0.9d, 2.5d, LinePattern.SOLID, null);
        }
    }

    @JsonTypeName("POLYGON")
    public record PolygonSymbol(String fillColor, Double fillOpacity, String outlineColor,
                                Double outlineOpacity, Double outlineWidth, LinePattern outlinePattern, PolygonPattern pattern)
            implements SpatialSymbol {
        public static PolygonSymbol defaults() {
            return new PolygonSymbol("#6F7DFF", 0.35d, "#3F51C6", 1d, 1.5d, LinePattern.SOLID, null);
        }
    }

    public record NumericRange(Double minimum, Double maximum) {
        public static NumericRange pointSizeDefaults() { return new NumericRange(6d, 24d); }
        public static NumericRange lineWidthDefaults() { return new NumericRange(1d, 8d); }
    }

    public record ColorRamp(String id, boolean reversed) {}
    public record UniqueValueRule(String id, String value, String label, SpatialSymbol symbol) {}
    public record StyleRule(String id, String label, SpatialSymbol symbol) {}

    public record Labeling(boolean enabled, String fieldCode, Double fontSize, Boolean bold,
                           String color, String haloColor, Double haloWidth,
                           String prefix, String suffix, Integer decimalPlaces,
                           PointLabelPosition pointPosition, Double pointOffset,
                           LineLabelPlacement linePlacement, Boolean repeat, Double repeatDistance,
                           Boolean polygonFit, Boolean allowOverlap, ScaleRange scaleRange) {
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

    public record Field(String code, String name, String dataType, ValueType valueType,
                        boolean uniqueValueSupported, boolean classBreaksSupported, boolean labelSupported) {}

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
