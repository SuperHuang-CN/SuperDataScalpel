package cn.superhuang.data.scalpel.business.metric.domain;
import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
import java.time.Instant;
@Entity
@Table(name="ds_data_metric", indexes=@Index(name="idx_ds_metric_directory",columnList="directory_id"))
public class DataMetric extends BaseEntity {
    @Column(nullable=false, unique=true, length=64, updatable=false)
    private String code;
    @Column(nullable=false,length=100)
    private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20)
    private MetricKind kind;
    @Column(name="directory_id")
    private UUID directoryId;
    @Column(length=100)
    private String ownerName;
    @Column(length=1000)
    private String summary;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20)
    private MetricStatus status;
    @cn.superhuang.data.scalpel.search.SearchExcluded
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable=false)
    private String draftDefinition;
    @Column(nullable=false,length=64)
    private String draftFingerprint;
    @Column(name="current_release_id")
    private UUID currentReleaseId;
    protected DataMetric() {}
    public String getCode() { return code; }
    public String getName() { return name; }
    public MetricKind getKind() { return kind; }
    public UUID getDirectoryId() { return directoryId; }
    public String getOwnerName() { return ownerName; }
    public String getSummary() { return summary; }
    public MetricStatus getStatus() { return status; }
    public String getDraftDefinition() { return draftDefinition; }
    public String getDraftFingerprint() { return draftFingerprint; }
    public UUID getCurrentReleaseId() { return currentReleaseId; }

    public static DataMetric create(String code) { DataMetric m=new DataMetric();m.code=code;m.status=MetricStatus.DRAFT;return m; }
    public void update(String name, MetricKind kind, UUID directoryId, String ownerName, String summary) {
        this.name=name;this.kind=kind;this.directoryId=directoryId;this.ownerName=ownerName;this.summary=summary;
    }
    public void saveDraft(String json,String hash) { draftDefinition=json;draftFingerprint=hash; }
    public void publish(UUID releaseId) { currentReleaseId=releaseId;status=MetricStatus.PUBLISHED; }
    public void disable() { status=MetricStatus.DISABLED; }

}
