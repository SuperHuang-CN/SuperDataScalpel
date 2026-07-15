package cn.superhuang.data.scalpel.engine.datasource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Encrypted JDBC connection snapshot owned by one Engine process. */
@Entity
@Table(
        name = "ds_engine_data_source",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_engine_data_source",
                columnNames = {"engine_code", "data_source_id"}
        )
)
public class EngineDataSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "engine_code", nullable = false, updatable = false, length = 64)
    private String engineCode;

    @Column(name = "data_source_id", nullable = false, updatable = false)
    private UUID dataSourceId;

    @Column(nullable = false)
    private long revision;

    @Column(name = "database_type", nullable = false, length = 64)
    private String databaseType;

    @Column(name = "snapshot_digest", nullable = false, length = 128)
    private String snapshotDigest;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "encrypted_snapshot_json", nullable = false)
    private String encryptedSnapshotJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EngineDataSource() {
    }

    public static EngineDataSource create(
            String engineCode,
            UUID dataSourceId,
            long revision,
            String databaseType,
            String snapshotDigest,
            String encryptedSnapshotJson
    ) {
        EngineDataSource dataSource = new EngineDataSource();
        dataSource.engineCode = engineCode;
        dataSource.dataSourceId = dataSourceId;
        dataSource.apply(revision, databaseType, snapshotDigest, encryptedSnapshotJson);
        return dataSource;
    }

    public void apply(long revision, String databaseType, String snapshotDigest, String encryptedSnapshotJson) {
        this.revision = revision;
        this.databaseType = databaseType;
        this.snapshotDigest = snapshotDigest;
        this.encryptedSnapshotJson = encryptedSnapshotJson;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void initializeTimes() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    public UUID getDataSourceId() {
        return dataSourceId;
    }

    public long getRevision() {
        return revision;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public String getSnapshotDigest() {
        return snapshotDigest;
    }

    public String getEncryptedSnapshotJson() {
        return encryptedSnapshotJson;
    }
}
