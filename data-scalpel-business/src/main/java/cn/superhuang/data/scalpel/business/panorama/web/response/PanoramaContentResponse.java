package cn.superhuang.data.scalpel.business.panorama.web.response;

import java.util.UUID;
public record PanoramaContentResponse(UUID id, String originalFilename, long byteSize, int width, int height,
                                      String sha256, PanoramaMetadataResponse metadata) {}
