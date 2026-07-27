package cn.superhuang.data.scalpel.shapefile.model;

/** Bounding ranges exactly as declared by a Shapefile header or geometry record. */
public record ShapefileEnvelope(
        double xmin,
        double ymin,
        double xmax,
        double ymax,
        double zmin,
        double zmax,
        double mmin,
        double mmax) {
}
