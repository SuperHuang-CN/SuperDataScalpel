package cn.superhuang.data.scalpel.business.cartography.model;

import java.util.List;
import java.util.Map;

/** Stable built-in palettes. Symbols store materialized colors, so palettes are authoring tools only. */
public final class SpatialColorRamps {

    private static final Map<String, List<String>> RAMPS = Map.of(
            "DATASCALPEL_12", List.of(
                    "#4F6BFF", "#8B5CF6", "#14B8A6", "#F59E0B", "#EF4444", "#06B6D4",
                    "#84CC16", "#EC4899", "#6366F1", "#10B981", "#F97316", "#64748B"
            ),
            "BLUE_PURPLE", List.of(
                    "#F3F4FF", "#DDE2FF", "#C3CCFF", "#A5B3FF", "#8599FF", "#667DFF",
                    "#4F63E8", "#3F4FC0", "#303A8F"
            ),
            "BLUES", List.of(
                    "#F7FBFF", "#DEEBF7", "#C6DBEF", "#9ECAE1", "#6BAED6", "#4292C6",
                    "#2171B5", "#08519C", "#08306B"
            ),
            "GREENS", List.of(
                    "#F7FCF5", "#E5F5E0", "#C7E9C0", "#A1D99B", "#74C476", "#41AB5D",
                    "#238B45", "#006D2C", "#00441B"
            ),
            "YELLOW_RED", List.of(
                    "#FFFFCC", "#FFEDA0", "#FED976", "#FEB24C", "#FD8D3C", "#FC4E2A",
                    "#E31A1C", "#BD0026", "#800026"
            )
    );

    private SpatialColorRamps() {
    }

    public static boolean contains(String id) {
        return id != null && RAMPS.containsKey(id);
    }

    public static List<String> require(String id) {
        List<String> colors = RAMPS.get(id);
        if (colors == null) throw new IllegalArgumentException("不支持的内置色带：" + id);
        return colors;
    }
}
