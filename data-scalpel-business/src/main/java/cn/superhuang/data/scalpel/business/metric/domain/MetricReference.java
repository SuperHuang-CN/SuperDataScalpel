package cn.superhuang.data.scalpel.business.metric.domain;
import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
import java.time.Instant;
@Entity
@Table(name="ds_metric_reference", uniqueConstraints=@UniqueConstraint(name="uk_ds_metric_reference",columnNames={"metric_id","scope","reference_path"}), indexes={@Index(name="idx_ds_metric_resource",columnList="resource_kind,resource_id,scope"),@Index(name="idx_ds_metric_parent",columnList="parent_model_id")})
public class MetricReference extends BaseEntity {
    @Column(name="metric_id",nullable=false)
    private UUID metricId;
    @Column(nullable=false,length=16)
    private String scope;
    @Column(name="reference_path",nullable=false,length=160)
    private String referencePath;
    @Enumerated(EnumType.STRING) @Column(name="resource_kind",nullable=false,length=32)
    private MetricDefinition.ResourceKind resourceKind;
    @Column(name="resource_id",nullable=false)
    private UUID resourceId;
    @Column(name="parent_model_id")
    private UUID parentModelId;
    @Column
    private Integer targetVersion;
    @Column(name="result_binding",nullable=false)
    private boolean resultBinding;
    protected MetricReference() {}
    public UUID getMetricId() { return metricId; }
    public String getScope() { return scope; }
    public String getReferencePath() { return referencePath; }
    public MetricDefinition.ResourceKind getResourceKind() { return resourceKind; }
    public UUID getResourceId() { return resourceId; }
    public UUID getParentModelId() { return parentModelId; }
    public Integer getTargetVersion() { return targetVersion; }
    public boolean getResultBinding() { return resultBinding; }

    public static MetricReference create(UUID metricId,String scope,String path,MetricDefinition.ResourceKind kind,UUID resourceId,UUID parentModelId,Integer targetVersion,boolean result) {
        MetricReference r=new MetricReference();r.metricId=metricId;r.scope=scope;r.referencePath=path;r.resourceKind=kind;
        r.resourceId=resourceId;r.parentModelId=parentModelId;r.targetVersion=targetVersion;r.resultBinding=result;return r;
    }

}
