package cn.superhuang.data.scalpel.business.panorama.web.response;

import java.time.LocalDateTime;
import java.util.List;

/** Whitelisted file metadata; aircraft heading and altitude sources retain their original meaning. */
public record PanoramaMetadataResponse(
        LocalDateTime captureTime, String captureOffset, String timeSource,
        Double latitude, Double longitude, String locationSource, String coordinateDatum,
        String manufacturer, String cameraModel, Double exifAltitude,
        Double djiAbsoluteAltitude, Double djiRelativeAltitude,
        String projection, Double panoramaHeading, Double aircraftYaw, List<String> warnings
) {
    public static PanoramaMetadataResponse empty(String warning) {
        return new PanoramaMetadataResponse(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, List.of(warning));
    }
}
