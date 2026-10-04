package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.contract.type.CrsReference;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves only explicit EPSG identifiers; coordinate names or extents are never guessed. */
final class FileDatasetCrsResolver {

    private static final Pattern EPSG_IDENTIFIER = Pattern.compile(
            "(?i)(?:AUTHORITY|ID)\\s*\\[\\s*[\"']EPSG[\"']\\s*,\\s*[\"']?(\\d+)[\"']?\\s*]"
    );

    private FileDatasetCrsResolver() {
    }

    static CrsReference requireEpsg(String wkt, Integer configuredEpsgCode, String sourceLabel) {
        Optional<CrsReference> detected = detectEpsg(wkt);
        if (configuredEpsgCode != null && configuredEpsgCode < 1) {
            throw new FileDatasetParsingException(sourceLabel + " 配置的 EPSG code 必须为正整数");
        }
        if (detected.isPresent()) {
            return detected.orElseThrow();
        }
        if (configuredEpsgCode != null) {
            return CrsReference.epsg(configuredEpsgCode);
        }
        throw new FileDatasetMissingCrsException(sourceLabel + " 的空间参考需要确认，请填写 EPSG 编码", wkt);
    }

    static Optional<CrsReference> detectEpsg(String wkt) {
        if (wkt == null || wkt.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = EPSG_IDENTIFIER.matcher(wkt);
        Integer code = null;
        while (matcher.find()) {
            try {
                int candidate = Integer.parseInt(matcher.group(1));
                if (candidate > 0) {
                    code = candidate;
                }
            } catch (NumberFormatException ignored) {
                // A malformed identifier is not a stable CRS identity.
            }
        }
        return code == null ? Optional.empty() : Optional.of(CrsReference.epsg(code));
    }
}
