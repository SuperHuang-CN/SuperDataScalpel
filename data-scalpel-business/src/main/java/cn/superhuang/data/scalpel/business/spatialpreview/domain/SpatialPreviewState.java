package cn.superhuang.data.scalpel.business.spatialpreview.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One shared generation per physical source and geometry field. All changes use a short locked transaction. */
@Entity
@Table(name = "ds_spatial_preview", uniqueConstraints = @UniqueConstraint(columnNames = "source_key"))
public class SpatialPreviewState extends BaseEntity {
    @Column(name="source_key", nullable=false, length=64) public String sourceKey;
    @Column(nullable=false, length=64) public String revision;
    @Column(nullable=false, length=24) public String state;
    @Column(nullable=false) public UUID generation;
    @Column(length=500) public String message;
    public Instant observedAt;
    public Instant expiresAt;
    public Instant leaseUntil;
    public long featureCount;
    public long emptyCount;
    public long dataBytes;
    @Column(length=300) public String bounds;
    @Column(length=64) public String dataHash;
    @Column(length=64) public String indexHash;
    @Column(length=64) public String imageHash;

    protected SpatialPreviewState() { }
    public SpatialPreviewState(String key, String revision) {
        this.sourceKey=key;
        reset(revision, "NOT_PREPARED", "正在准备预览");
    }
    public void reset(String revision, String state, String message) {
        this.revision=revision;
        this.state=state;
        this.message=message;
        generation=UUID.randomUUID();
        observedAt=null; expiresAt=null; leaseUntil=null; bounds=null;
        featureCount=0; emptyCount=0; dataBytes=0;
        dataHash=null; indexHash=null; imageHash=null;
    }
}
