package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewViewport;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** Renders clipped EPSG:3857 WKB with the fixed MVP map style. */
final class SpatialPreviewPngRenderer {

    private static final Color BLUE = new Color(22, 119, 255);
    private static final Color DARK_BLUE = new Color(9, 75, 160);
    private static final Color POLYGON_FILL = new Color(22, 119, 255, 82);

    RenderedSpatialPreview render(
            List<byte[]> wkbRows,
            SpatialPreviewViewport viewport,
            int maximumCoordinates,
            int databaseSkipped,
            boolean databaseTruncated
    ) {
        BufferedImage image = new BufferedImage(viewport.width(), viewport.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        int featureCount = 0;
        int skippedCount = databaseSkipped;
        int coordinateCount = 0;
        boolean truncated = databaseTruncated;
        WKBReader reader = new WKBReader();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            for (byte[] wkb : wkbRows) {
                Geometry geometry;
                try {
                    geometry = reader.read(wkb);
                } catch (ParseException exception) {
                    skippedCount++;
                    continue;
                }
                if (geometry == null || geometry.isEmpty()) {
                    skippedCount++;
                    continue;
                }
                int geometryCoordinates = geometry.getNumPoints();
                if ((long) coordinateCount + geometryCoordinates > maximumCoordinates) {
                    truncated = true;
                    break;
                }
                draw(graphics, geometry, viewport);
                coordinateCount += geometryCoordinates;
                featureCount++;
            }
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("当前 JDK 无法生成 PNG 图片");
            }
            return new RenderedSpatialPreview(
                    output.toByteArray(), featureCount, skippedCount, truncated
            );
        } catch (IOException exception) {
            throw new IllegalStateException("生成空间预览 PNG 失败", exception);
        }
    }

    private static void draw(Graphics2D graphics, Geometry geometry, SpatialPreviewViewport viewport) {
        if (geometry instanceof Point point) {
            drawPoint(graphics, point, viewport);
        } else if (geometry instanceof Polygon polygon) {
            drawPolygon(graphics, polygon, viewport);
        } else if (geometry instanceof LineString lineString) {
            drawLine(graphics, lineString, viewport);
        } else if (geometry instanceof GeometryCollection collection) {
            for (int index = 0; index < collection.getNumGeometries(); index++) {
                draw(graphics, collection.getGeometryN(index), viewport);
            }
        }
    }

    private static void drawPoint(Graphics2D graphics, Point point, SpatialPreviewViewport viewport) {
        Coordinate coordinate = point.getCoordinate();
        double x = pixelX(coordinate.x, viewport);
        double y = pixelY(coordinate.y, viewport);
        graphics.setColor(BLUE);
        graphics.fill(new Ellipse2D.Double(x - 3d, y - 3d, 6d, 6d));
    }

    private static void drawLine(Graphics2D graphics, LineString line, SpatialPreviewViewport viewport) {
        Path2D path = path(line.getCoordinates(), viewport, false);
        graphics.setColor(BLUE);
        graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(path);
    }

    private static void drawPolygon(Graphics2D graphics, Polygon polygon, SpatialPreviewViewport viewport) {
        Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        path.append(path(polygon.getExteriorRing().getCoordinates(), viewport, true), false);
        for (int index = 0; index < polygon.getNumInteriorRing(); index++) {
            path.append(path(polygon.getInteriorRingN(index).getCoordinates(), viewport, true), false);
        }
        graphics.setColor(POLYGON_FILL);
        graphics.fill(path);
        graphics.setColor(DARK_BLUE);
        graphics.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(path);
    }

    private static Path2D path(Coordinate[] coordinates, SpatialPreviewViewport viewport, boolean close) {
        Path2D result = new Path2D.Double();
        if (coordinates.length == 0) {
            return result;
        }
        result.moveTo(pixelX(coordinates[0].x, viewport), pixelY(coordinates[0].y, viewport));
        for (int index = 1; index < coordinates.length; index++) {
            result.lineTo(pixelX(coordinates[index].x, viewport), pixelY(coordinates[index].y, viewport));
        }
        if (close) {
            result.closePath();
        }
        return result;
    }

    private static double pixelX(double x, SpatialPreviewViewport viewport) {
        return (x - viewport.minX()) / (viewport.maxX() - viewport.minX()) * viewport.width();
    }

    private static double pixelY(double y, SpatialPreviewViewport viewport) {
        return viewport.height()
                - (y - viewport.minY()) / (viewport.maxY() - viewport.minY()) * viewport.height();
    }

    record RenderedSpatialPreview(byte[] png, int featureCount, int skippedCount, boolean truncated) {
    }
}
