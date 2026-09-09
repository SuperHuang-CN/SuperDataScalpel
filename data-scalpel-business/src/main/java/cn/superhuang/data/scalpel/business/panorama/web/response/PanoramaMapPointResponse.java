package cn.superhuang.data.scalpel.business.panorama.web.response;

import java.util.UUID;
import java.time.LocalDateTime;
public record PanoramaMapPointResponse(UUID id, String name, Double latitude, Double longitude,
                                      LocalDateTime captureTime, long contentVersion) {}
