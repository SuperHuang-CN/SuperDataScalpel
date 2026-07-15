package cn.superhuang.data.scalpel.business.datasource.domain;

import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

import java.util.Map;

/**
 * Generic persisted connection values. Their business meaning is interpreted from
 * {@link DataSourceType}: a JDBC target is a database/schema, Kafka uses an endpoint only, and
 * S3 uses the target and namespace as bucket and root prefix.
 *
 * <p>This is intentionally an embedded value object rather than searchable root fields. In
 * particular, its password must never become part of the common entity-search DSL.</p>
 */
@Embeddable
public class DataSourceConnection {

    @Column(name = "host", nullable = false, length = 500)
    private String endpoint;

    @Column(name = "port")
    private Integer port;

    @Column(name = "database_name", length = 128)
    private String target;

    @Column(name = "schema_name", length = 128)
    private String namespace;

    @Column(name = "username", length = 128)
    private String principal;

    @Column(name = "connection_password", length = 512)
    private String secret;

    @Convert(converter = ConnectionOptionsConverter.class)
    @Column(name = "connection_options", length = 4000)
    private Map<String, String> options;

    protected DataSourceConnection() {
    }

    private DataSourceConnection(
            String endpoint,
            Integer port,
            String target,
            String namespace,
            String principal,
            String secret,
            Map<String, String> options
    ) {
        this.endpoint = endpoint;
        this.port = port;
        this.target = target;
        this.namespace = namespace;
        this.principal = principal;
        this.secret = secret;
        this.options = options == null ? null : immutableOptions(options);
    }

    public static DataSourceConnection create(
            String endpoint,
            Integer port,
            String target,
            String namespace,
            String principal,
            String secret,
            Map<String, String> options
    ) {
        return new DataSourceConnection(endpoint, port, target, namespace, principal, secret, options);
    }

    public static DataSourceConnection jdbc(
            String host,
            Integer port,
            String databaseName,
            String schemaName,
            String username,
            String password,
            Map<String, String> options
    ) {
        return create(host, port, databaseName, schemaName, username, password, options);
    }

    /**
     * Persists a non-JDBC connection while remaining compatible with the original JDBC table
     * columns, whose port, database name and username were defined as non-null.
     *
     * <p>Zero and empty strings are persistence-only placeholders; typed response DTOs translate
     * them back to absent values and no runtime path uses them as JDBC settings.</p>
     */
    public static DataSourceConnection nonJdbc(
            String endpoint,
            String target,
            String namespace,
            String principal,
            String secret,
            Map<String, String> options
    ) {
        return create(endpoint, 0, target == null ? "" : target, namespace,
                principal == null ? "" : principal, secret, options);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getHost() {
        return endpoint;
    }

    public Integer getPort() {
        return port;
    }

    public String getDatabaseName() {
        return target;
    }

    public String getSchemaName() {
        return namespace;
    }

    public String getUsername() {
        return principal;
    }

    public String getTarget() {
        return blankToNull(target);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPrincipal() {
        return blankToNull(principal);
    }

    public boolean hasPassword() {
        return secret != null && !secret.isBlank();
    }

    /** Internal aggregate value used only to preserve a write-only secret during an update. */
    public String secretValue() {
        return secret;
    }

    public Map<String, String> getOptions() {
        return options == null ? Map.of() : Map.copyOf(options);
    }

    /** Internal runtime snapshot. API responses use a separate DTO and never expose the password. */
    public JdbcConnectionConfig toJdbcConnectionConfig() {
        return new JdbcConnectionConfig(
                endpoint,
                port,
                target,
                namespace,
                principal,
                secret,
                getOptions()
        );
    }

    private static Map<String, String> immutableOptions(Map<String, String> options) {
        return options == null || options.isEmpty() ? Map.of() : Map.copyOf(options);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
