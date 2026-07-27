package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;

import java.util.List;

public record PlatformTypeCapabilityResponse(
        PlatformDataType type,
        boolean supported,
        String message,
        boolean lengthParameterSupported,
        boolean unboundedStringSupported,
        List<GeometryKind> geometryKinds,
        List<CoordinateDimension> coordinateDimensions,
        List<String> crsAuthorities
) {
    public PlatformTypeCapabilityResponse {
        geometryKinds = geometryKinds == null ? List.of() : List.copyOf(geometryKinds);
        coordinateDimensions = coordinateDimensions == null ? List.of() : List.copyOf(coordinateDimensions);
        crsAuthorities = crsAuthorities == null ? List.of() : List.copyOf(crsAuthorities);
    }
}
