package cn.superhuang.data.scalpel.business.metric.domain;
import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
import java.time.Instant;
@Entity
@Table(name="ds_metric_release", uniqueConstraints=@UniqueConstraint(name="uk_ds_metric_version",columnNames={"metric_id","version"}))
public class MetricRelease extends BaseEntity {
    @Column(name="metric_id",nullable=false,updatable=false)
    private UUID metricId;
    @Column(nullable=false,updatable=false)
    private int version;
    @cn.superhuang.data.scalpel.search.SearchExcluded
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable=false,updatable=false)
    private String definitionSnapshot;
    @cn.superhuang.data.scalpel.search.SearchExcluded
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable=false,updatable=false)
    private String referenceSnapshot;
    @Column(nullable=false,length=64,updatable=false)
    private String definitionFingerprint;
    @Column(nullable=false,updatable=false)
    private Instant publishedAt;
    @Column(length=100,updatable=false)
    private String publishedBy;
    @Column(length=2000,updatable=false)
    private String changeNote;
    protected MetricRelease() {}
    public UUID getMetricId() { return metricId; }
    public int getVersion() { return version; }
    public String getDefinitionSnapshot() { return definitionSnapshot; }
    public String getReferenceSnapshot() { return referenceSnapshot; }
    public String getDefinitionFingerprint() { return definitionFingerprint; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getPublishedBy() { return publishedBy; }
    public String getChangeNote() { return changeNote; }

    public static MetricRelease create(UUID id,int version,String definition,String references,String hash,String author,String note) {
        MetricRelease r=new MetricRelease();r.metricId=id;r.version=version;r.definitionSnapshot=definition;
        r.referenceSnapshot=references;r.definitionFingerprint=hash;r.publishedAt=Instant.now();r.publishedBy=author;r.changeNote=note;return r;
    }

}
