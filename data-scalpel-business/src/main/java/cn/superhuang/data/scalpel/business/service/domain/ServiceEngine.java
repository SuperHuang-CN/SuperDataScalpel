package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.net.URI;
import java.util.Locale;

/** Registered remote runtime that receives service deployment snapshots. */
@Entity
@Table(name = "ds_service_engine", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_service_engine_code", columnNames = "code"
))
public class ServiceEngine extends BaseEntity {

    @Column(nullable = false, updatable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "admin_url", nullable = false, length = 500)
    private String adminUrl;

    @Column(name = "public_url", nullable = false, length = 500)
    private String publicUrl;

    @Column(name = "management_token_ciphertext", nullable = false, length = 8192)
    private String managementTokenCiphertext;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 1000)
    private String description;

    protected ServiceEngine() {
    }

    private ServiceEngine(
            String code,
            String name,
            String adminUrl,
            String publicUrl,
            String managementTokenCiphertext,
            boolean enabled,
            String description
    ) {
        this.code = normalizeCode(code);
        update(name, adminUrl, publicUrl, managementTokenCiphertext, enabled, description);
    }

    public static ServiceEngine create(
            String code,
            String name,
            String adminUrl,
            String publicUrl,
            String managementTokenCiphertext,
            boolean enabled,
            String description
    ) {
        return new ServiceEngine(
                code, name, adminUrl, publicUrl, managementTokenCiphertext, enabled, description
        );
    }

    public void update(
            String name,
            String adminUrl,
            String publicUrl,
            String managementTokenCiphertext,
            boolean enabled,
            String description
    ) {
        this.name = required(name, "名称");
        this.adminUrl = normalizeAdminUrl(adminUrl);
        this.publicUrl = normalizeUrl(publicUrl, "公网地址");
        this.managementTokenCiphertext = required(managementTokenCiphertext, "Management Token 密文");
        this.enabled = enabled;
        this.description = optional(description);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAdminUrl() {
        return adminUrl;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public String getManagementTokenCiphertext() {
        return managementTokenCiphertext;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDescription() {
        return description;
    }

    private static String normalizeCode(String value) {
        return required(value, "编码").toLowerCase(Locale.ROOT);
    }

    public static String normalizeAdminUrl(String value) {
        return normalizeUrl(value, "管理地址");
    }

    private static String normalizeUrl(String value, String label) {
        String normalized = required(value, label).replaceFirst("/+$", "");
        try {
            URI uri = URI.create(normalized);
            if (uri.getScheme() == null || uri.getHost() == null || (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(label + "必须是 HTTP 或 HTTPS 地址", exception);
        }
        return normalized;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
