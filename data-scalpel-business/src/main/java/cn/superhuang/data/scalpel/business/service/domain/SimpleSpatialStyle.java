package cn.superhuang.data.scalpel.business.service.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.regex.Pattern;

/** One-symbol point, line or polygon style used by the V1 online cartography editor. */
public record SimpleSpatialStyle(
        SpatialMarkerShape markerShape,
        Double pointSize,
        String fillColor,
        Double fillOpacity,
        String strokeColor,
        Double strokeOpacity,
        Double strokeWidth,
        SpatialLinePattern linePattern
) {
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");

    public static SimpleSpatialStyle defaults(SpatialGeometryFamily family) {
        return switch (family) {
            case POINT -> new SimpleSpatialStyle(
                    SpatialMarkerShape.CIRCLE, 10d, "#4F6BFF", 0.85d,
                    "#FFFFFF", 1d, 1d, SpatialLinePattern.SOLID
            );
            case LINE -> new SimpleSpatialStyle(
                    null, null, null, null, "#4F6BFF", 0.9d, 2.5d, SpatialLinePattern.SOLID
            );
            case POLYGON -> new SimpleSpatialStyle(
                    null, null, "#6F7DFF", 0.35d, "#3F51C6", 1d, 1.5d,
                    SpatialLinePattern.SOLID
            );
            case GENERIC -> throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "通用 Geometry 第一版不支持简单制图，请上传 SLD"
            );
        };
    }

    public SimpleSpatialStyle normalized(SpatialGeometryFamily family) {
        if (family == SpatialGeometryFamily.GENERIC) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "通用 Geometry 第一版不支持简单制图，请上传 SLD");
        }
        SimpleSpatialStyle fallback = defaults(family);
        return switch (family) {
            case POINT -> new SimpleSpatialStyle(
                    markerShape == null ? fallback.markerShape : markerShape,
                    number(pointSize, fallback.pointSize, 2d, 64d, "点大小"),
                    color(fillColor, fallback.fillColor, "填充颜色"),
                    number(fillOpacity, fallback.fillOpacity, 0d, 1d, "填充透明度"),
                    color(strokeColor, fallback.strokeColor, "边框颜色"),
                    number(strokeOpacity, fallback.strokeOpacity, 0d, 1d, "边框透明度"),
                    number(strokeWidth, fallback.strokeWidth, 0d, 20d, "边框宽度"),
                    SpatialLinePattern.SOLID
            );
            case LINE -> new SimpleSpatialStyle(
                    null, null, null, null,
                    color(strokeColor, fallback.strokeColor, "线颜色"),
                    number(strokeOpacity, fallback.strokeOpacity, 0d, 1d, "线透明度"),
                    number(strokeWidth, fallback.strokeWidth, 0.1d, 20d, "线宽"),
                    linePattern == null ? fallback.linePattern : linePattern
            );
            case POLYGON -> new SimpleSpatialStyle(
                    null, null,
                    color(fillColor, fallback.fillColor, "填充颜色"),
                    number(fillOpacity, fallback.fillOpacity, 0d, 1d, "填充透明度"),
                    color(strokeColor, fallback.strokeColor, "边框颜色"),
                    number(strokeOpacity, fallback.strokeOpacity, 0d, 1d, "边框透明度"),
                    number(strokeWidth, fallback.strokeWidth, 0d, 20d, "边框宽度"),
                    linePattern == null ? fallback.linePattern : linePattern
            );
            case GENERIC -> throw new IllegalStateException("通用 Geometry 不支持简单制图");
        };
    }

    private static String color(String value, String fallback, String label) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!COLOR.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "必须是 #RRGGBB 格式");
        }
        return normalized;
    }

    private static double number(Double value, Double fallback, double minimum, double maximum, String label) {
        double normalized = value == null ? fallback : value;
        if (!Double.isFinite(normalized) || normalized < minimum || normalized > maximum) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    label + "必须在 " + minimum + "–" + maximum + " 范围内"
            );
        }
        return normalized;
    }
}
