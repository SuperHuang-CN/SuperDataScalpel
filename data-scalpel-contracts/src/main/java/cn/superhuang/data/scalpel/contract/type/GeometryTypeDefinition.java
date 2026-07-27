package cn.superhuang.data.scalpel.contract.type;

/** Geometry subtype together with its stable CRS and coordinate dimension. */
public record GeometryTypeDefinition(
        GeometryKind kind,
        CrsReference crs,
        CoordinateDimension dimension
) {

    public GeometryTypeDefinition {
        if (kind == null) {
            throw new IllegalArgumentException("Geometry kind is required");
        }
        if (crs == null) {
            throw new IllegalArgumentException("Geometry CRS is required");
        }
        if (dimension == null) {
            throw new IllegalArgumentException("Geometry coordinate dimension is required");
        }
    }
}
