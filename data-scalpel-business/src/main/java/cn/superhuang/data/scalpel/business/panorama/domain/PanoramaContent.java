package cn.superhuang.data.scalpel.business.panorama.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.search.SearchExcluded;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "ds_panorama_content")
public class PanoramaContent extends BaseEntity {
    @SearchExcluded
    private UUID panoramaId;
    @SearchExcluded
    private UUID replacementTargetId;
    @SearchExcluded @Column(nullable = false, unique = true)
    private UUID clientRequestId;
    @Column(nullable = false, length = 255)
    private String originalFilename;

    private long byteSize;

    private int width;

    private int height;
    @Column(length = 64)
    private String sha256;
    @SearchExcluded @Column(nullable = false)
    private String objectPrefix;
    @SearchExcluded @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String metadataJson;
    @SearchExcluded @Column(nullable = false)
    private boolean cleanupPending = true;
    @SearchExcluded
    private Instant cleanupAfter;
    public UUID getPanoramaId() { return panoramaId; }
    public void setPanoramaId(UUID value) { panoramaId = value; }
    public UUID getReplacementTargetId() { return replacementTargetId; }
    public void setReplacementTargetId(UUID value) { replacementTargetId = value; }
    public UUID getClientRequestId() { return clientRequestId; }
    public void setClientRequestId(UUID value) { clientRequestId = value; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { originalFilename = value; }
    public long getByteSize() { return byteSize; }
    public void setByteSize(long value) { byteSize = value; }
    public int getWidth() { return width; }
    public void setWidth(int value) { width = value; }
    public int getHeight() { return height; }
    public void setHeight(int value) { height = value; }
    public String getSha256() { return sha256; }
    public void setSha256(String value) { sha256 = value; }
    public String getObjectPrefix() { return objectPrefix; }
    public void setObjectPrefix(String value) { objectPrefix = value; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String value) { metadataJson = value; }
    public boolean getCleanupPending() { return cleanupPending; }
    public void setCleanupPending(boolean value) { cleanupPending = value; }
    public Instant getCleanupAfter() { return cleanupAfter; }
    public void setCleanupAfter(Instant value) { cleanupAfter = value; }
}
