package cn.superhuang.data.scalpel.business.datasource.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Transient;
import org.hibernate.annotations.ColumnDefault;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** A reusable JDBC, Kafka, or S3 connection with one or more business purposes. */
@Entity
@Table(
        name = "ds_data_source",
        uniqueConstraints = @UniqueConstraint(name = "uk_ds_data_source_code", columnNames = "code")
)
public class DataSource extends BaseEntity {

    @Column(nullable = false, updatable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "directory_id")
    private UUID directoryId;

    @Column(name = "source_enabled", nullable = false)
    private boolean sourceEnabled;

    @Column(name = "storage_enabled", nullable = false)
    private boolean storageEnabled;

    @Column(name = "distribution_enabled", nullable = false)
    @ColumnDefault("false")
    private boolean distributionEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "database_type", nullable = false, length = 32)
    private DataSourceType type;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 1000)
    private String description;

    @Embedded
    private DataSourceConnection connection;

    protected DataSource() {
    }

    private DataSource(
            String code,
            String name,
            UUID directoryId,
            Set<DataSourcePurpose> purposes,
            DataSourceType type,
            boolean enabled,
            String description,
            DataSourceConnection connection
    ) {
        this.code = normalizeCode(code);
        update(name, directoryId, purposes, type, enabled, description, connection);
    }

    public static DataSource create(
            String code,
            String name,
            UUID directoryId,
            Set<DataSourcePurpose> purposes,
            DataSourceType type,
            boolean enabled,
            String description,
            DataSourceConnection connection
    ) {
        return new DataSource(code, name, directoryId, purposes, type, enabled, description, connection);
    }

    public void update(
            String name,
            UUID directoryId,
            Set<DataSourcePurpose> purposes,
            DataSourceType type,
            boolean enabled,
            String description,
            DataSourceConnection connection
    ) {
        applyPurposes(purposes);
        this.name = normalizeRequiredText(name);
        this.directoryId = directoryId;
        this.type = type;
        this.enabled = enabled;
        this.description = normalizeOptionalText(description);
        this.connection = connection;
    }

    @Transient
    public Set<DataSourcePurpose> getPurposes() {
        EnumSet<DataSourcePurpose> purposes = EnumSet.noneOf(DataSourcePurpose.class);
        if (sourceEnabled) {
            purposes.add(DataSourcePurpose.SOURCE);
        }
        if (storageEnabled) {
            purposes.add(DataSourcePurpose.STORAGE);
        }
        if (distributionEnabled) {
            purposes.add(DataSourcePurpose.DISTRIBUTION);
        }
        return Set.copyOf(purposes);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public UUID getDirectoryId() {
        return directoryId;
    }

    public boolean isSourceEnabled() {
        return sourceEnabled;
    }

    public boolean isStorageEnabled() {
        return storageEnabled;
    }

    public boolean isDistributionEnabled() {
        return distributionEnabled;
    }

    public DataSourceType getType() {
        return type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDescription() {
        return description;
    }

    public DataSourceConnection getConnection() {
        return connection;
    }

    private void applyPurposes(Set<DataSourcePurpose> purposes) {
        if (purposes == null || purposes.isEmpty()) {
            throw new IllegalArgumentException("数据源至少需要一个用途");
        }
        sourceEnabled = purposes.contains(DataSourcePurpose.SOURCE);
        storageEnabled = purposes.contains(DataSourcePurpose.STORAGE);
        distributionEnabled = purposes.contains(DataSourcePurpose.DISTRIBUTION);
    }

    private static String normalizeCode(String value) {
        return normalizeRequiredText(value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeRequiredText(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("字段不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

}
