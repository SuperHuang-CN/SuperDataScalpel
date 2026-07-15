package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

public record PlatformTypeCapabilityResponse(
        PlatformDataType type,
        boolean supported,
        String message,
        boolean lengthParameterSupported,
        boolean unboundedStringSupported
) {
}
