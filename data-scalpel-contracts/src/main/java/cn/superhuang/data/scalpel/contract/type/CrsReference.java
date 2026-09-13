package cn.superhuang.data.scalpel.contract.type;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Locale;

/** Stable authority/code CRS identity, independent from a database-local SRID or SRS ID. */
@JsonClassDescription("跨数据库稳定表示坐标参考系的机构与代码组合，例如 EPSG:4326；不使用数据库本地 SRID。")
public record CrsReference(
        @JsonPropertyDescription("坐标参考系编码机构，大写字母开头，仅含大写字母、数字和下划线；通常为 EPSG。")
        String authority,
        @JsonPropertyDescription("该机构内的正整数坐标参考系编码，例如 WGS 84 为 EPSG:4326 中的 4326。")
        int code
) {

    public CrsReference {
        if (authority == null || authority.isBlank()) {
            throw new IllegalArgumentException("CRS authority is required");
        }
        authority = authority.trim().toUpperCase(Locale.ROOT);
        if (!authority.matches("[A-Z][A-Z0-9_]{0,15}")) {
            throw new IllegalArgumentException("Invalid CRS authority: " + authority);
        }
        if (code < 1) {
            throw new IllegalArgumentException("CRS code must be positive");
        }
    }

    public static CrsReference epsg(int code) {
        return new CrsReference("EPSG", code);
    }
}
