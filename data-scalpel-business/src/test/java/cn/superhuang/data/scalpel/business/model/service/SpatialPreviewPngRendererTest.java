package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewViewport;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.WKTReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpatialPreviewPngRendererTest {
    private static final SpatialPreviewViewport VIEWPORT = new SpatialPreviewViewport(0, 0, 100, 100, 400, 400);

    @Test
    void multipleRingsPreserveHolesAndSeparateIsland() throws Exception {
        var image = render("MULTIPOLYGON (((10 10, 90 10, 90 90, 10 90, 10 10),"
                + "(30 30, 70 30, 70 70, 30 70, 30 30)),"
                + "((45 45, 55 45, 55 55, 45 55, 45 45)))");

        assertEquals(82, alpha(image, 80, 200), "The polygon shell keeps its translucent fill");
        assertEquals(0, image.getRGB(160, 200), "A hole remains transparent");
        assertEquals(82, alpha(image, 200, 200), "A separate island inside the hole is visible");
        assertEquals(0, image.getRGB(20, 200), "The background outside the feature stays transparent");
        assertTrue(alpha(image, 40, 200) > 82, "The shell retains its darker boundary stroke");
    }

    @Test
    void mixedGeometryKeepsSeparatePathsAndFinalLineVertices() throws Exception {
        var image = render("GEOMETRYCOLLECTION (POINT (50 50),"
                + "MULTILINESTRING ((5 5, 5.01 5.01, 30 5), (70 95, 95 95)),"
                + "MULTIPOINT ((10 50), (90 50)))");

        assertEquals(255, alpha(image, 200, 200), "Collection points are still rendered");
        assertEquals(255, alpha(image, 40, 200));
        assertEquals(255, alpha(image, 360, 200));
        assertEquals(255, alpha(image, 80, 380), "Subpixel vertices retain the visible line");
        assertTrue(alpha(image, 120, 380) > 0, "The last vertex is retained");
        assertEquals(255, alpha(image, 320, 20));
        assertEquals(0, image.getRGB(200, 100), "Separate line paths are not connected");
        assertEquals(0, image.getRGB(140, 380));
    }

    private BufferedImage render(String... geometries) throws Exception {
        var reader = new WKTReader();
        var writer = new WKBWriter();
        List<byte[]> rows = new ArrayList<>();
        for (String geometry : geometries) rows.add(writer.write(reader.read(geometry)));
        var result = new SpatialPreviewPngRenderer().render(rows, VIEWPORT, 10_000, 0, false);
        assertEquals(geometries.length, result.featureCount());
        assertEquals(0, result.skippedCount());
        assertFalse(result.truncated());
        return ImageIO.read(new ByteArrayInputStream(result.png()));
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
