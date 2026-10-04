package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewViewport;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
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
public final class SpatialPreviewPngRenderer {

    private static final Color BLUE = new Color(22, 119, 255);
    private static final Color DARK_BLUE = new Color(9, 75, 160);
    private static final Color POLYGON_FILL = new Color(22, 119, 255, 82);
    private static final BasicStroke LINE_STROKE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke POLYGON_STROKE = new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final GeometryFactory WKB_GEOMETRY_FACTORY = new GeometryFactory(
            new PrecisionModel(), 0, PackedCoordinateSequenceFactory.DOUBLE_FACTORY);

    public RenderedSpatialPreview render(
            List<byte[]> wkbRows,
            SpatialPreviewViewport viewport,
            int maximumCoordinates,
            int databaseSkipped,
            boolean databaseTruncated
    ) {
        return render(wkbRows, viewport, maximumCoordinates, databaseSkipped, databaseTruncated, Long.MAX_VALUE);
    }

    public RenderedSpatialPreview render(Iterable<byte[]> wkbRows, SpatialPreviewViewport viewport,
            long maximumCoordinates, int databaseSkipped, boolean databaseTruncated, long deadline) {
        BufferedImage image = new BufferedImage(viewport.width(), viewport.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        int featureCount = 0;
        int skippedCount = databaseSkipped;
        long coordinateCount = 0;
        boolean truncated = databaseTruncated;
        WKBReader reader = new WKBReader(WKB_GEOMETRY_FACTORY);
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            for (byte[] wkb : wkbRows) {
                cn.superhuang.data.scalpel.business.spatialpreview.service.SpatialPreviewBudget.deadline(deadline);
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
        CoordinateSequence coordinate = point.getCoordinateSequence();
        double x = pixelX(coordinate.getX(0), viewport);
        double y = pixelY(coordinate.getY(0), viewport);
        graphics.setColor(BLUE);
        graphics.fill(new Ellipse2D.Double(x - 3d, y - 3d, 6d, 6d));
    }

    private static void drawLine(Graphics2D graphics, LineString line, SpatialPreviewViewport viewport) {
        Path2D path = new Path2D.Double();
        appendPath(path, line.getCoordinateSequence(), viewport, false);
        graphics.setColor(BLUE);
        graphics.setStroke(LINE_STROKE);
        graphics.draw(path);
    }

    private static void drawPolygon(Graphics2D graphics, Polygon polygon, SpatialPreviewViewport viewport) {
        Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        appendPath(path, polygon.getExteriorRing().getCoordinateSequence(), viewport, true);
        for (int index = 0; index < polygon.getNumInteriorRing(); index++) {
            appendPath(path, polygon.getInteriorRingN(index).getCoordinateSequence(), viewport, true);
        }
        graphics.setColor(POLYGON_FILL);
        graphics.fill(path);
        graphics.setColor(DARK_BLUE);
        graphics.setStroke(POLYGON_STROKE);
        graphics.draw(path);
    }

    private static void appendPath(Path2D result, CoordinateSequence coordinates,
            SpatialPreviewViewport viewport, boolean close) {
        int size = coordinates.size();
        if (size == 0) {
            return;
        }
        double lastX=pixelX(coordinates.getX(0), viewport), lastY=pixelY(coordinates.getY(0), viewport);
        result.moveTo(lastX, lastY);
        for (int index = 1; index < size; index++) {
            double x=pixelX(coordinates.getX(index), viewport), y=pixelY(coordinates.getY(index), viewport);
            if (index==size-1 || Math.abs(x-lastX)>=0.5 || Math.abs(y-lastY)>=0.5) {
                result.lineTo(x,y); lastX=x; lastY=y;
            }
        }
        if (close) {
            result.closePath();
        }
    }

    private static double pixelX(double x, SpatialPreviewViewport viewport) {
        return (x - viewport.minX()) / (viewport.maxX() - viewport.minX()) * viewport.width();
    }

    private static double pixelY(double y, SpatialPreviewViewport viewport) {
        return viewport.height()
                - (y - viewport.minY()) / (viewport.maxY() - viewport.minY()) * viewport.height();
    }

    public record RenderedSpatialPreview(byte[] png, int featureCount, int skippedCount, boolean truncated) {
    }
}
