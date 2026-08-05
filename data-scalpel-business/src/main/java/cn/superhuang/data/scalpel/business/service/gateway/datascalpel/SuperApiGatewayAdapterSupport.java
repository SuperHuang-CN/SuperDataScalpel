package cn.superhuang.data.scalpel.business.service.gateway.datascalpel;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

final class SuperApiGatewayAdapterSupport {

    private SuperApiGatewayAdapterSupport() {
    }

    static boolean owned(String source, String externalId, UUID expectedId) {
        return SuperApiGatewayAdminClient.SOURCE.equals(source)
                && expectedId != null
                && expectedId.toString().equals(externalId);
    }

    static UUID externalUuid(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " 未配置");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(label + " 不是有效 UUID", exception);
        }
    }

    static int milliseconds(Duration duration, int minimum, int maximum, String label) {
        if (duration == null) {
            throw new IllegalArgumentException(label + " 未配置");
        }
        long value = duration.toMillis();
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    label + " 必须在 " + minimum + "ms 到 " + maximum + "ms 之间"
            );
        }
        return Math.toIntExact(value);
    }

    static boolean equalText(String first, String second) {
        return Objects.equals(normalize(first), normalize(second));
    }

    static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
