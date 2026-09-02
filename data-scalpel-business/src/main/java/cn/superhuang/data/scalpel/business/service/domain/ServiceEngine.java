package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** Registered remote runtime that receives service deployment snapshots. */
@Entity
@Table(name = "ds_service_engine", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_service_engine_code", columnNames = "code"
))
public class ServiceEngine extends BaseEntity {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    @Column(nullable = false, updatable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    @ColumnDefault("'DATASCALPEL'")
    private ServiceEngineType type = ServiceEngineType.DATASCALPEL;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "admin_url", nullable = false, length = 500)
    private String adminUrl;

    @Column(name = "runtime_url", nullable = false, length = 500)
    private String runtimeUrl;

    @Column(name = "management_token_ciphertext", length = 8192)
    private String managementTokenCiphertext;

    @Column(name = "geoserver_username", length = 200)
    private String geoServerUsername;

    @Column(name = "geoserver_password_ciphertext", length = 8192)
    private String geoServerPasswordCiphertext;

    @Column(name = "geoserver_workspace", length = 100)
    private String geoServerWorkspace;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 1000)
    private String description;

    protected ServiceEngine() {
    }

    private ServiceEngine(
            String code,
            ServiceEngineType type,
            String name,
            String adminUrl,
            String runtimeUrl,
            String managementTokenCiphertext,
            boolean enabled,
            String description
    ) {
        this.code = normalizeCode(code);
        this.type = type == null ? ServiceEngineType.DATASCALPEL : type;
        this.name = required(name, "名称");
        this.adminUrl = normalizeAdminUrl(adminUrl);
        this.runtimeUrl = normalizeRuntimeUrl(runtimeUrl);
        this.enabled = enabled;
        this.description = optional(description);
        if (this.type == ServiceEngineType.DATASCALPEL) {
            this.managementTokenCiphertext = required(managementTokenCiphertext, "Management Token 密文");
        }
    }

    public static ServiceEngine create(
            String code,
            String name,
            String adminUrl,
            String runtimeUrl,
            String managementTokenCiphertext,
            boolean enabled,
            String description
    ) {
        return new ServiceEngine(
                code, ServiceEngineType.DATASCALPEL, name, adminUrl, runtimeUrl,
                managementTokenCiphertext, enabled, description
        );
    }

    public static ServiceEngine createGeoServer(
            String code,
            String name,
            String adminUrl,
            String runtimeUrl,
            String username,
            String passwordCiphertext,
            String workspace,
            boolean enabled,
            String description
    ) {
        ServiceEngine engine = new ServiceEngine(
                code, ServiceEngineType.GEOSERVER, name, adminUrl, runtimeUrl,
                null, enabled, description
        );
        engine.geoServerUsername = required(username, "GeoServer 用户名");
        engine.geoServerPasswordCiphertext = required(passwordCiphertext, "GeoServer 密码密文");
        engine.geoServerWorkspace = normalizeWorkspace(workspace);
        return engine;
    }

    public void update(
            String name,
            String adminUrl,
            String runtimeUrl,
            String managementTokenCiphertext,
            boolean enabled,
            String description
    ) {
        update(name, adminUrl, runtimeUrl, managementTokenCiphertext, null, null, null, enabled, description);
    }

    public void update(
            String name,
            String adminUrl,
            String runtimeUrl,
            String managementTokenCiphertext,
            String geoServerUsername,
            String geoServerPasswordCiphertext,
            String geoServerWorkspace,
            boolean enabled,
            String description
    ) {
        this.name = required(name, "名称");
        this.adminUrl = normalizeAdminUrl(adminUrl);
        this.runtimeUrl = normalizeRuntimeUrl(runtimeUrl);
        if (getType() == ServiceEngineType.DATASCALPEL) {
            this.managementTokenCiphertext = required(managementTokenCiphertext, "Management Token 密文");
            this.geoServerUsername = null;
            this.geoServerPasswordCiphertext = null;
            this.geoServerWorkspace = null;
        } else {
            this.managementTokenCiphertext = null;
            this.geoServerUsername = required(geoServerUsername, "GeoServer 用户名");
            this.geoServerPasswordCiphertext = required(geoServerPasswordCiphertext, "GeoServer 密码密文");
            this.geoServerWorkspace = normalizeWorkspace(geoServerWorkspace);
        }
        this.enabled = enabled;
        this.description = optional(description);
    }

    public String getCode() {
        return code;
    }

    public ServiceEngineType getType() {
        return type == null ? ServiceEngineType.DATASCALPEL : type;
    }

    public boolean matchesCode(String candidate) {
        try {
            return code.equals(normalizeCode(candidate));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String getName() {
        return name;
    }

    public String getAdminUrl() {
        return adminUrl;
    }

    public String getRuntimeUrl() {
        return runtimeUrl;
    }

    public String getManagementTokenCiphertext() {
        return managementTokenCiphertext;
    }

    public String getGeoServerUsername() {
        return geoServerUsername;
    }

    public String getGeoServerPasswordCiphertext() {
        return geoServerPasswordCiphertext;
    }

    public String getGeoServerWorkspace() {
        return geoServerWorkspace;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDescription() {
        return description;
    }

    public static String normalizeCode(String value) {
        String normalized = required(value, "编码");
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("编码必须以字母开头，仅支持字母、数字和下划线，最长 64 位");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public static String normalizeAdminUrl(String value) {
        return normalizeUrl(value, "管理地址");
    }

    public static String normalizeRuntimeUrl(String value) {
        return normalizeUrl(value, "运行地址");
    }

    public static String normalizeWorkspace(String value) {
        String normalized = value == null || value.isBlank() ? "datascalpel" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_.-]{0,99}")) {
            throw new IllegalArgumentException("GeoServer Workspace 必须以字母开头，仅支持小写字母、数字、点、横线和下划线");
        }
        return normalized;
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
