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
@Table(name = "ds_panorama")
public class Panorama extends BaseEntity {
    @SearchExcluded @Column(unique = true)
    private UUID creationRequestId;
    public UUID getCreationRequestId() { return creationRequestId; }
    public void setCreationRequestId(UUID value) { creationRequestId = value; }
    @Column(nullable = false, length = 255)
    private String name;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String description;

    private UUID directoryId;

    private LocalDateTime captureTime;
    @Column(length = 10)
    private String captureOffset;

    private Double latitude;

    private Double longitude;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PanoramaValueMode timeMode = PanoramaValueMode.AUTO;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PanoramaValueMode locationMode = PanoramaValueMode.AUTO;
    @SearchExcluded
    private UUID currentContentId;
    @SearchExcluded
    private UUID candidateContentId;
    @Column(nullable = false)
    private long contentVersion = 0;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PanoramaProcessingStatus processingStatus = PanoramaProcessingStatus.QUEUED;
    @SearchExcluded @Column(length = 500)
    private String processingError;
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public UUID getDirectoryId() { return directoryId; }
    public void setDirectoryId(UUID value) { directoryId = value; }
    public LocalDateTime getCaptureTime() { return captureTime; }
    public void setCaptureTime(LocalDateTime value) { captureTime = value; }
    public String getCaptureOffset() { return captureOffset; }
    public void setCaptureOffset(String value) { captureOffset = value; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double value) { latitude = value; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double value) { longitude = value; }
    public PanoramaValueMode getTimeMode() { return timeMode; }
    public void setTimeMode(PanoramaValueMode value) { timeMode = value; }
    public PanoramaValueMode getLocationMode() { return locationMode; }
    public void setLocationMode(PanoramaValueMode value) { locationMode = value; }
    public UUID getCurrentContentId() { return currentContentId; }
    public void setCurrentContentId(UUID value) { currentContentId = value; }
    public UUID getCandidateContentId() { return candidateContentId; }
    public void setCandidateContentId(UUID value) { candidateContentId = value; }
    public long getContentVersion() { return contentVersion; }
    public void setContentVersion(long value) { contentVersion = value; }
    public PanoramaProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(PanoramaProcessingStatus value) { processingStatus = value; }
    public String getProcessingError() { return processingError; }
    public void setProcessingError(String value) { processingError = value; }
}
