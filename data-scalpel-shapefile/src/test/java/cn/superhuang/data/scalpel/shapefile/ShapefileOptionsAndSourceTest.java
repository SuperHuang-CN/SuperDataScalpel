package cn.superhuang.data.scalpel.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.testutil.TestShapefileBuilder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapefileOptionsAndSourceTest {
    private static final ShapefileEnvelope ENVELOPE =
            new ShapefileEnvelope(0, 0, 1, 1, 0, 0, 0, 0);

    @TempDir
    Path temporaryDirectory;

    @Test
    void appliesCharsetOverrideCpgLanguageDriverAndFallbackInOrder() throws Exception {
        Path overridden = fixture("override", StandardCharsets.UTF_8, "道路", "definitely-not-a-charset", 0x4D);
        ShapefileOpenOptions overrideOptions = new ShapefileOpenOptions(
                ShapefileReadLimits.defaults(), StandardCharsets.UTF_8, Charset.forName("GB18030"), false);
        assertDecoded(overridden, overrideOptions, StandardCharsets.UTF_8, "道路");

        Path cpg = fixture("cpg", StandardCharsets.UTF_8, "道路", "65001\n", 0x4D);
        assertDecoded(cpg, ShapefileOpenOptions.defaults(), StandardCharsets.UTF_8, "道路");

        Path languageDriver = fixture("ldid", Charset.forName("GBK"), "道路", null, 0x4D);
        assertDecoded(languageDriver, ShapefileOpenOptions.defaults(), Charset.forName("GBK"), "道路");

        Path fallback = fixture("fallback", StandardCharsets.ISO_8859_1, "café", null, 0x00);
        ShapefileOpenOptions fallbackOptions = new ShapefileOpenOptions(
                ShapefileReadLimits.defaults(), null, StandardCharsets.ISO_8859_1, false);
        assertDecoded(fallback, fallbackOptions, StandardCharsets.ISO_8859_1, "café");
    }

    @Test
    void rejectsEmptyOrMalformedEncodingMetadata() throws Exception {
        Path empty = fixture("empty-cpg", StandardCharsets.UTF_8, "x", " \n", 0);
        assertCode(ShapefileErrorCode.INVALID_ENCODING, () -> ShapefileDataset.open(empty));

        Path malformedCpg = fixture("bad-cpg", StandardCharsets.UTF_8, "x", "UTF-8", 0);
        Files.write(temporaryDirectory.resolve("bad-cpg.cpg"), new byte[] {(byte) 0xC3});
        assertCode(ShapefileErrorCode.INVALID_ENCODING, () -> ShapefileDataset.open(malformedCpg));

        Path malformedPrj = fixture("bad-prj", StandardCharsets.UTF_8, "x", null, 0);
        Files.write(temporaryDirectory.resolve("bad-prj.prj"), new byte[] {(byte) 0xC3});
        assertCode(ShapefileErrorCode.INVALID_ENCODING, () -> ShapefileDataset.open(malformedPrj));
    }

    @Test
    void rejectsLinksByDefaultAndAllowsThemExplicitly() throws Exception {
        Path shp = fixture("links", StandardCharsets.UTF_8, "x", null, 0);
        Path shx = temporaryDirectory.resolve("links.shx");
        Path backing = temporaryDirectory.resolve("index-backing.bin");
        Files.move(shx, backing);
        try {
            Files.createSymbolicLink(shx, backing.getFileName());
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            Assumptions.abort("Symbolic links are unavailable: " + exception.getMessage());
        }

        assertCode(ShapefileErrorCode.INVALID_SOURCE, () -> ShapefileDataset.open(shp));
        ShapefileOpenOptions options = new ShapefileOpenOptions(
                ShapefileReadLimits.defaults(), null, Charset.forName("GB18030"), true);
        try (ShapefileDataset dataset = ShapefileDataset.open(shp, options)) {
            assertEquals(1, dataset.schema().recordCount());
        }
    }

    @Test
    void rejectsWrongSourceKindsAndAcceptsAsciiCaseInsensitiveExtensionWhenRepresentable() throws Exception {
        assertCode(ShapefileErrorCode.INVALID_SOURCE, () -> ShapefileDataset.open(temporaryDirectory));

        Path shp = fixture("kind", StandardCharsets.UTF_8, "x", null, 0);
        Path dbf = temporaryDirectory.resolve("kind.dbf");
        Files.delete(dbf);
        Files.createDirectory(dbf);
        assertCode(ShapefileErrorCode.INVALID_SOURCE, () -> ShapefileDataset.open(shp));

        Path caseShp = fixture("case", StandardCharsets.UTF_8, "x", null, 0);
        Path uppercaseRequest = temporaryDirectory.resolve("case.SHP");
        Assumptions.assumeTrue(
                Files.exists(uppercaseRequest),
                "Case-sensitive filesystem cannot resolve a differently-cased source path");
        try (ShapefileDataset dataset = ShapefileDataset.open(uppercaseRequest)) {
            assertEquals(caseShp.toRealPath(), uppercaseRequest.toRealPath());
            assertEquals(1, dataset.schema().recordCount());
        }
    }

    @Test
    void configuredLimitsCanOnlyTightenBuiltInSafetyCaps() {
        ShapefileReadLimits defaults = ShapefileReadLimits.defaults();
        assertThrows(IllegalArgumentException.class, () -> new ShapefileReadLimits(
                defaults.maxComponentFileBytes() + 1,
                defaults.maxFields(),
                defaults.maxRecordBytes(),
                defaults.maxMetadataBytes(),
                defaults.maxParts(),
                defaults.maxGeometryPoints(),
                defaults.maxIndexRecords(),
                defaults.maxFeaturesPerCursor()));
        assertThrows(IllegalArgumentException.class, () -> ShapefileReadOptions.limit(0));
    }

    private Path fixture(
            String name,
            Charset charset,
            String value,
            String cpg,
            int languageDriverId) throws Exception {
        TestShapefileBuilder builder = new TestShapefileBuilder(
                temporaryDirectory, name, ShapefileShapeType.POINT, ENVELOPE)
                .field("Name", 'C', 20, 0)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 0, 0, null, null), false, value)
                .dbfCharset(charset)
                .languageDriverId(languageDriverId);
        if (cpg != null) {
            builder.cpg(cpg);
        }
        return builder.write();
    }

    private static void assertDecoded(
            Path shp,
            ShapefileOpenOptions options,
            Charset expectedCharset,
            String expectedValue) {
        try (ShapefileDataset dataset = ShapefileDataset.open(shp, options);
                ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(1))) {
            assertEquals(expectedCharset, dataset.schema().dbfCharset());
            assertEquals(expectedValue, cursor.next().attribute("Name"));
        }
    }

    private static void assertCode(
            ShapefileErrorCode expected,
            org.junit.jupiter.api.function.Executable operation) {
        assertEquals(expected, assertThrows(ShapefileException.class, operation).errorCode());
    }
}
