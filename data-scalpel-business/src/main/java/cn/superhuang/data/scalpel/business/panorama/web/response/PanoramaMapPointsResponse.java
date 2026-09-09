package cn.superhuang.data.scalpel.business.panorama.web.response;

import java.util.List;
public record PanoramaMapPointsResponse(List<PanoramaMapPointResponse> points, long totalElements, boolean truncated) {}
