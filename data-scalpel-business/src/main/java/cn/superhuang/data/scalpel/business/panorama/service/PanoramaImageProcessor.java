package cn.superhuang.data.scalpel.business.panorama.service;

import cn.superhuang.data.scalpel.business.panorama.web.response.PanoramaMetadataResponse;
import cn.superhuang.data.scalpel.web.error.CodedProblemException;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class PanoramaImageProcessor {
    public static final long MAX_BYTES = 100L * 1024 * 1024;
    private static final String GPANO = "http://ns.google.com/photos/1.0/panorama/";
    private static final String DJI = "http://www.dji.com/drone-dji/1.0/";
    private static final String XMP = "http://ns.adobe.com/xap/1.0/";
    private static final String EXIF = "http://ns.adobe.com/exif/1.0/";

    public Dimensions inspect(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            if (in.read() != 0xff || in.read() != 0xd8 || in.read() != 0xff) throw invalid("只接受 JPEG 全景成品");
        }
        try (var input = new FileImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("无法识别 JPEG 图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 32768 || width != 2L * height)
                    throw invalid("需要完整 2:1 球形全景，宽度不得超过 32768 像素");
                return new Dimensions(width, height);
            } finally { reader.dispose(); }
        }
    }

    public PanoramaMetadataResponse metadata(Path file, Dimensions size) {
        List<String> warnings = new ArrayList<>();
        Metadata metadata;
        try { metadata = JpegMetadataReader.readMetadata(file.toFile()); }
        catch (Exception e) { return PanoramaMetadataResponse.empty("文件元数据无法读取，可手工补录时间和位置"); }
        for (var directory : metadata.getDirectories()) {
            if (directory.hasErrors()) { warnings.add("部分文件元数据无法完整读取"); break; }
        }
        XmpDirectory xmp = metadata.getFirstDirectoryOfType(XmpDirectory.class);
        String projection = property(xmp, GPANO, "ProjectionType");
        if (projection != null && !projection.equalsIgnoreCase("equirectangular"))
            throw invalid("文件声明了不支持的全景投影");
        // Cropped-area declarations must describe this complete image, not a partial sphere.
        for (String field : List.of("FullPanoWidthPixels", "CroppedAreaImageWidthPixels", "FullPanoHeightPixels", "CroppedAreaImageHeightPixels", "CroppedAreaLeftPixels", "CroppedAreaTopPixels")) {
            String value = property(xmp, GPANO, field);
            if (value == null) continue;
            try {
                int expected = field.contains("Left") || field.contains("Top") ? 0 : field.contains("Width") ? size.width() : size.height();
                if (Integer.parseInt(value) != expected) throw invalid("不接受裁剪全景，需上传完整球形成品");
            } catch (NumberFormatException e) { throw invalid("全景范围声明无效"); }
        }
        ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        ExifIFD0Directory camera = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        LocalDateTime time = null;
        String offset = null, timeSource = null;
        if (exif != null) {
            String raw = exif.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            try {
                if (raw != null) { time = LocalDateTime.parse(raw.trim(), DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT)); timeSource = "EXIF DateTimeOriginal"; }
            } catch (RuntimeException e) { warnings.add("EXIF 拍摄时间无效"); }
            // EXIF 2.31 OffsetTimeOriginal; kept separate from the local wall time.
            offset = validOffset(exif.getString(0x9011));
        }
        if (time == null) {
            for (String raw : Arrays.asList(property(xmp, EXIF, "DateTimeOriginal"), property(xmp, XMP, "CreateDate"))) {
                if (raw == null) continue;
                try {
                    var parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(raw, OffsetDateTime::from, LocalDateTime::from);
                    if (parsed instanceof OffsetDateTime dated) { time = dated.toLocalDateTime(); offset = dated.getOffset().getId(); }
                    else { time = (LocalDateTime) parsed; offset = null; }
                    timeSource = "XMP";
                    break;
                } catch (RuntimeException e) { warnings.add("XMP 拍摄时间无效"); }
            }
        }
        if (time == null) { offset = null; warnings.add("未提取到拍摄时间"); }
        else if (offset == null) warnings.add("拍摄时间缺少时区，按照片本地时间保留");
        GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        Double lat = null, lon = null, altitude = null;
        String datum = gps == null ? null : gps.getString(GpsDirectory.TAG_MAP_DATUM);
        if (datum == null) datum = property(xmp, EXIF, "GPSMapDatum");
        String locationSource = null;
        boolean wgs84 = datum == null || datum.replaceAll("[\\s_-]", "").equalsIgnoreCase("WGS84");
        if (gps != null) {
            if (wgs84) {
                try {
                    var geo = gps.getGeoLocation();
                    if (geo != null && validLocation(geo.getLatitude(), geo.getLongitude())) { lat = geo.getLatitude(); lon = geo.getLongitude(); locationSource = "EXIF GPS"; }
                } catch (RuntimeException e) { warnings.add("GPS 坐标无法读取"); }
            } else warnings.add("文件声明非 WGS84 坐标，未自动采用位置");
            try {
                altitude = gps.getDoubleObject(GpsDirectory.TAG_ALTITUDE);
                if (altitude != null && !Double.isFinite(altitude)) { altitude = null; warnings.add("EXIF 高度无效"); }
                if (altitude != null && Integer.valueOf(1).equals(gps.getInteger(GpsDirectory.TAG_ALTITUDE_REF))) altitude = -altitude;
            } catch (RuntimeException e) { warnings.add("EXIF 高度无法读取"); }
        }
        if (lat == null && wgs84) {
            Double xmpLat = coordinate(property(xmp, EXIF, "GPSLatitude"));
            Double xmpLon = coordinate(property(xmp, EXIF, "GPSLongitude"));
            if (xmpLat != null && xmpLon != null && validLocation(xmpLat, xmpLon)) {
                lat = xmpLat; lon = xmpLon; locationSource = "XMP EXIF GPS";
            } else {
                xmpLat = number(property(xmp, DJI, "GpsLatitude"));
                xmpLon = number(property(xmp, DJI, "GpsLongitude"));
                if (xmpLon == null) xmpLon = number(property(xmp, DJI, "GpsLongtitude"));
                if (xmpLat != null && xmpLon != null && validLocation(xmpLat, xmpLon)) { lat = xmpLat; lon = xmpLon; locationSource = "DJI XMP GPS"; }
            }
        }
        if (!wgs84 && gps == null) warnings.add("文件声明非 WGS84 坐标，未自动采用位置");
        if (lat == null) warnings.add("未提取到可用 WGS84 位置");
        return new PanoramaMetadataResponse(time, offset, timeSource, lat, lon, locationSource, datum,
                camera == null ? null : camera.getString(ExifIFD0Directory.TAG_MAKE),
                camera == null ? null : camera.getString(ExifIFD0Directory.TAG_MODEL), altitude,
                number(property(xmp, DJI, "AbsoluteAltitude")), number(property(xmp, DJI, "RelativeAltitude")),
                projection, number(property(xmp, GPANO, "PoseHeadingDegrees")), number(property(xmp, DJI, "FlightYawDegree")), List.copyOf(warnings));
    }

    public void derivatives(Path original, Path preview, Path thumbnail, Dimensions dimensions) throws IOException {
        try (var input = new FileImageInputStream(original.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("无法解码 JPEG");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                var parameter = reader.getDefaultReadParam();
                int subsample = Math.max(1, (dimensions.width() + 4095) / 4096);
                parameter.setSourceSubsampling(subsample, subsample, 0, 0);
                reader.addIIOReadWarningListener((source, warning) -> { throw invalid("JPEG 解码发现损坏或截断，请重新导出成品"); });
                BufferedImage decoded = reader.read(0, parameter);
                if (decoded == null) throw invalid("无法解码 JPEG");
                try {
                    writeScaled(decoded, preview, Math.min(4096, dimensions.width()));
                    writeScaled(decoded, thumbnail, Math.min(512, dimensions.width()));
                } finally { decoded.flush(); }
            } finally { reader.dispose(); }
        }
    }
    private static void writeScaled(BufferedImage source, Path target, int width) throws IOException {
        BufferedImage scaled = new BufferedImage(width, width / 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, width / 2, null);
            if (!ImageIO.write(scaled, "jpeg", target.toFile())) throw new IOException("JPEG encoder unavailable");
        } finally { graphics.dispose(); scaled.flush(); }
    }
    private static String property(XmpDirectory xmp, String namespace, String field) {
        try { return xmp == null || xmp.getXMPMeta() == null ? null : xmp.getXMPMeta().getPropertyString(namespace, field); }
        catch (Exception e) { return null; }
    }
    private static Double coordinate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toUpperCase(Locale.ROOT);
        char hemisphere = value.charAt(value.length() - 1);
        boolean suffix = "NSEW".indexOf(hemisphere) >= 0;
        if (suffix) value = value.substring(0, value.length() - 1);
        try {
            String[] parts = value.split(",");
            if (parts.length > 3) return null;
            double degrees = Double.parseDouble(parts[0]);
            double angle = Math.abs(degrees);
            for (int i = 1; i < parts.length; i++) {
                double part = Double.parseDouble(parts[i]);
                if (part < 0 || part >= 60) return null;
                angle += part / Math.pow(60, i);
            }
            angle *= suffix ? (hemisphere == 'S' || hemisphere == 'W' ? -1 : 1) : (degrees < 0 ? -1 : 1);
            return Double.isFinite(angle) ? angle : null;
        } catch (RuntimeException e) { return null; }
    }
    private static Double number(String raw) {
        try { double value = Double.parseDouble(raw); return Double.isFinite(value) ? value : null; }
        catch (RuntimeException e) { return null; }
    }
    public static String validOffset(String value) {
        try { return value == null || value.isBlank() ? null : ZoneOffset.of(value.trim()).getId(); }
        catch (RuntimeException e) { return null; }
    }
    public static boolean validLocation(double lat, double lon) { return Double.isFinite(lat) && Double.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180; }
    public static CodedProblemException invalid(String message) { return new CodedProblemException(HttpStatus.BAD_REQUEST, "PANORAMA_INVALID_FILE", message); }
    public record Dimensions(int width, int height) {}
}
