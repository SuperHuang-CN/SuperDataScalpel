package cn.superhuang.data.scalpel.business.panorama.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** Whitelisted file metadata; aircraft heading and altitude sources retain their original meaning. */
@Schema(description = "从当前 JPEG 的 EXIF/XMP 中提取并持久化的白名单元数据；未声明、无效或不可信的值为空，warnings 说明可手工补录的非阻断问题。")
public record PanoramaMetadataResponse(
        @Schema(description = "从文件提取的照片本地拍摄时间，不含时区；无法解析时为空。")
        LocalDateTime captureTime,
        @Schema(description = "从文件提取并规范化的 ZoneOffset，例如 +08:00 或 Z；未声明或无效时为空。")
        String captureOffset,
        @Schema(description = "拍摄时间来源，例如 EXIF 或 XMP；未解析到时间时为空")
        String timeSource,
        @Schema(description = "自动采用的 WGS84 纬度，范围 -90 到 90；文件明确声明非 WGS84、坐标无效或缺失时为空。")
        Double latitude,
        @Schema(description = "自动采用的 WGS84 经度，范围 -180 到 180；与 latitude 同时有值或同时为空。")
        Double longitude,
        @Schema(description = "坐标来源，例如 EXIF、XMP_EXIF 或 DJI_XMP；未采用坐标时为空")
        String locationSource,
        @Schema(description = "文件声明的坐标基准原文；未声明时为空。仅空值或规范化后为 WGS84 的坐标可自动采用。")
        String coordinateDatum,
        @Schema(description = "相机厂商。")
        String manufacturer,
        @Schema(description = "相机模型。")
        String cameraModel,
        @Schema(description = "EXIF 海拔，单位米；海平面以下为负值，未声明时为空")
        Double exifAltitude,
        @Schema(description = "DJI XMP 声明的绝对高度，单位米；未声明时为空")
        Double djiAbsoluteAltitude,
        @Schema(description = "DJI XMP 声明的相对起飞点高度，单位米；未声明时为空")
        Double djiRelativeAltitude,
        @Schema(description = "文件声明的 GPano ProjectionType；未声明时为空，声明时必须为 equirectangular，否则上传或处理失败。")
        String projection,
        @Schema(description = "GPano PoseHeadingDegrees 声明的全景正北方向角原值，单位度；未声明时为空，当前仅展示而不改变图片。")
        Double panoramaHeading,
        @Schema(description = "DJI FlightYawDegree 声明的飞行器偏航角原值，单位度；仅展示，不用于推断全景正北。")
        Double aircraftYaw,
        @Schema(description = "EXIF/XMP 读取失败、时间缺少时区、坐标不可采用或关键元数据缺失等非阻断告警；没有告警时为空数组。")
        List<String> warnings
) {
    public static PanoramaMetadataResponse empty(String warning) {
        return new PanoramaMetadataResponse(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, List.of(warning));
    }
}
