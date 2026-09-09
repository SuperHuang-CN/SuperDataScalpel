package cn.superhuang.data.scalpel.business.panorama;

import cn.superhuang.data.scalpel.business.panorama.service.PanoramaImageProcessor;
import cn.superhuang.data.scalpel.business.system.configuration.domain.PanoramaMapConfiguration;
import cn.superhuang.data.scalpel.web.error.CodedProblemException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;

class PanoramaImageProcessorTest {
    @TempDir Path work;
    private final PanoramaImageProcessor images = new PanoramaImageProcessor();

    @Test void missingMetadataDoesNotBlockPreviewOrAlterOriginal() throws Exception {
        Path original = jpeg(1200, 600);
        byte[] before = Files.readAllBytes(original);
        var size = images.inspect(original);
        var metadata = images.metadata(original, size);
        images.derivatives(original, work.resolve("preview.jpg"), work.resolve("thumb.jpg"), size);
        assertThat(metadata.captureTime()).isNull();
        assertThat(metadata.latitude()).isNull();
        assertThat(ImageIO.read(work.resolve("preview.jpg").toFile()).getWidth()).isEqualTo(1200);
        assertThat(ImageIO.read(work.resolve("thumb.jpg").toFile()).getWidth()).isEqualTo(512);
        assertThat(Files.readAllBytes(original)).isEqualTo(before);
    }

    @Test void largerOriginalProducesBoundedPreview() throws Exception {
        Path original = jpeg(5000, 2500);
        images.derivatives(original, work.resolve("preview.jpg"), work.resolve("thumb.jpg"), images.inspect(original));
        var preview = ImageIO.read(work.resolve("preview.jpg").toFile());
        assertThat(preview.getWidth()).isEqualTo(4096);
        assertThat(preview.getHeight()).isEqualTo(2048);
    }

    @Test void wrongRatioAndTruncatedJpegCannotBecomeUsable() throws Exception {
        assertThatThrownBy(() -> images.inspect(jpeg(600, 600))).isInstanceOf(CodedProblemException.class);
        Path original = jpeg(1200, 600);
        byte[] bytes = Files.readAllBytes(original);
        Files.write(original, Arrays.copyOf(bytes, bytes.length / 2));
        assertThatThrownBy(() -> images.derivatives(original, work.resolve("preview.jpg"), work.resolve("thumb.jpg"), new PanoramaImageProcessor.Dimensions(1200, 600)))
                .isInstanceOfAny(CodedProblemException.class, java.io.IOException.class);
    }

    @Test void xmpKeepsUnknownTimezoneAndDistinctAltitudeSemantics() throws Exception {
        Path file = xmp("exif:DateTimeOriginal=\"2026-08-12T09:34:56\" drone-dji:AbsoluteAltitude=\"123.4\" drone-dji:RelativeAltitude=\"40.5\" drone-dji:FlightYawDegree=\"72\"");
        var metadata = images.metadata(file, images.inspect(file));
        assertThat(metadata.captureTime()).isEqualTo(LocalDateTime.of(2026, 8, 12, 9, 34, 56));
        assertThat(metadata.captureOffset()).isNull();
        assertThat(metadata.djiAbsoluteAltitude()).isEqualTo(123.4);
        assertThat(metadata.djiRelativeAltitude()).isEqualTo(40.5);
        assertThat(metadata.aircraftYaw()).isEqualTo(72.0);
        assertThat(metadata.panoramaHeading()).isNull();
    }

    @Test void xmpPreservesExplicitOffsetAndRejectsCroppedOrOtherProjection() throws Exception {
        Path file = xmp("exif:DateTimeOriginal=\"2026-08-12T09:34:56+08:00\"");
        assertThat(images.metadata(file, images.inspect(file)).captureOffset()).isEqualTo("+08:00");
        for (String declaration : new String[]{"GPano:ProjectionType=\"cylindrical\"", "GPano:FullPanoWidthPixels=\"2400\"", "GPano:CroppedAreaLeftPixels=\"10\""}) {
            Path invalid = xmp(declaration);
            assertThatThrownBy(() -> images.metadata(invalid, images.inspect(invalid))).isInstanceOf(CodedProblemException.class);
        }
    }

    @Test void xmpCoordinatesPreserveHemisphereAndRespectExplicitDatum() throws Exception {
        Path file = xmp("exif:GPSLatitude=\"20,30S\" exif:GPSLongitude=\"120,15W\"");
        var metadata = images.metadata(file, images.inspect(file));
        assertThat(metadata.latitude()).isEqualTo(-20.5);
        assertThat(metadata.longitude()).isEqualTo(-120.25);
        assertThat(metadata.locationSource()).isEqualTo("XMP EXIF GPS");
        Path differentDatum = xmp("exif:GPSMapDatum=\"GCJ02\" drone-dji:GpsLatitude=\"20\" drone-dji:GpsLongitude=\"120\"");
        assertThat(images.metadata(differentDatum, images.inspect(differentDatum)).latitude()).isNull();
    }

    @Test void mapSettingsRejectUnsafeOrUnsupportedAddresses() {
        assertThat(PanoramaMapConfiguration.parse("{\"url\":\"\",\"attribution\":\"\",\"maxZoom\":18}").url()).isEmpty();
        for (String url : new String[]{"javascript:alert(1)", "https://tiles/{z}/{x}.png", "file:///{z}/{x}/{y}", "https://user:password@tiles/{z}/{x}/{y}"}) {
            assertThatThrownBy(() -> PanoramaMapConfiguration.parse("{\"url\":\"" + url + "\",\"attribution\":\"\",\"maxZoom\":18}"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private Path jpeg(int width, int height) throws Exception {
        Path file = Files.createTempFile(work, "sphere-", ".jpg");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try { ImageIO.write(image, "jpeg", file.toFile()); } finally { image.flush(); }
        return file;
    }

    private Path xmp(String attributes) throws Exception {
        Path file = jpeg(1200, 600);
        byte[] jpeg = Files.readAllBytes(file);
        String xml = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description rdf:about=\"\" xmlns:exif=\"http://ns.adobe.com/exif/1.0/\" xmlns:GPano=\"http://ns.google.com/photos/1.0/panorama/\" xmlns:drone-dji=\"http://www.dji.com/drone-dji/1.0/\" " + attributes + "/></rdf:RDF></x:xmpmeta>";
        byte[] payload = ("http://ns.adobe.com/xap/1.0/\0" + xml).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); out.write(0xff); out.write(0xe1); out.write((payload.length + 2) >> 8); out.write((payload.length + 2) & 255); out.write(payload); out.write(jpeg, 2, jpeg.length - 2);
        Files.write(file, out.toByteArray()); return file;
    }
}
