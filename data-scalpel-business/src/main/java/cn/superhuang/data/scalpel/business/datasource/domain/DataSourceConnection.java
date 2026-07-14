package cn.superhuang.data.scalpel.business.datasource.domain;

import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

import java.util.Map;

/**
 * Connection parameters stored with a data source.
 *
 * <p>This is intentionally an embedded value object rather than searchable root fields. In
 * particular, its password must never become part of the common entity-search DSL.</p>
 */
@Embeddable
public class DataSourceConnection {

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(name = "database_name", nullable = false, length = 128)
    private String databaseName;

    @Column(name = "schema_name", length = 128)
    private String schemaName;

    @Column(nullable = false, length = 128)
    private String username;

    @Column(name = "connection_password", length = 512)
    private String password;

    @Convert(converter = ConnectionOptionsConverter.class)
    @Column(name = "connection_options", length = 4000)
    private Map<String, String> options;

    protected DataSourceConnection() {
    }

    private DataSourceConnection(
            String host,
            Integer port,
            String databaseName,
            String schemaName,
            String username,
            String password,
            Map<String, String> options
    ) {
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.schemaName = schemaName;
        this.username = username;
        this.password = password;
        this.options = options == null ? null : immutableOptions(options);
    }

    public static DataSourceConnection create(
            String host,
            Integer port,
            String databaseName,
            String schemaName,
            String username,
            String password,
            Map<String, String> options
    ) {
        return new DataSourceConnection(host, port, databaseName, schemaName, username, password, options);
    }

    public void update(
            String host,
            Integer port,
            String databaseName,
            String schemaName,
            String username,
            String password,
            Map<String, String> options
    ) {
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.schemaName = schemaName;
        this.username = username;
        if (password != null) {
            this.password = password;
        }
        if (options != null) {
            this.options = immutableOptions(options);
        }
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getUsername() {
        return username;
    }

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }

    public Map<String, String> getOptions() {
        return options == null ? Map.of() : Map.copyOf(options);
    }

    /** Internal runtime snapshot. API responses use a separate DTO and never expose the password. */
    public JdbcConnectionConfig toJdbcConnectionConfig() {
        return new JdbcConnectionConfig(
                host,
                port,
                databaseName,
                schemaName,
                username,
                password,
                getOptions()
        );
    }

    String passwordValue() {
        return password;
    }

    Map<String, String> optionsValue() {
        return options;
    }

    private static Map<String, String> immutableOptions(Map<String, String> options) {
        return options == null || options.isEmpty() ? Map.of() : Map.copyOf(options);
    }
}
