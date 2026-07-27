package cn.superhuang.data.scalpel.shapefile;

/** Options for opening one bounded feature cursor. */
public record ShapefileReadOptions(int limit) {
    public ShapefileReadOptions {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    public static ShapefileReadOptions limit(int limit) {
        return new ShapefileReadOptions(limit);
    }
}
