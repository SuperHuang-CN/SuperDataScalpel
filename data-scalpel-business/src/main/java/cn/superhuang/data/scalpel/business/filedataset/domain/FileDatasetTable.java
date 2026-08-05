package cn.superhuang.data.scalpel.business.filedataset.domain;

import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** Stable logical table whose current data is composed from one or more table sources. */
@Entity
@Table(
        name = "ds_file_dataset_table",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_file_dataset_table_code",
                columnNames = {"file_dataset_id", "table_code"}
        )
)
public class FileDatasetTable extends BaseEntity {

    @Column(name = "file_dataset_id", nullable = false, updatable = false)
    private UUID fileDatasetId;

    @Column(name = "table_code", nullable = false, length = 128)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 32)
    private FileDatasetParseStatus parseStatus;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "parsed_metadata")
    private String parsedMetadata;

    @Column(name = "current_load_job_id")
    private UUID currentLoadJobId;

    @Column(name = "spatial_crs_authority", length = 16)
    private String spatialCrsAuthority;

    @Column(name = "spatial_crs_code")
    private Integer spatialCrsCode;

    protected FileDatasetTable() {
    }

    private FileDatasetTable(UUID fileDatasetId, String code, String name) {
        if (fileDatasetId == null) {
            throw new IllegalArgumentException("文件数据集不能为空");
        }
        this.fileDatasetId = fileDatasetId;
        this.code = required(code, "表代码不能为空");
        this.name = required(name, "表名称不能为空");
        this.parseStatus = FileDatasetParseStatus.QUEUED;
    }

    public static FileDatasetTable create(UUID fileDatasetId, String code, String name) {
        return new FileDatasetTable(fileDatasetId, code, name);
    }

    public void rename(String name) {
        this.name = required(name, "表名称不能为空");
    }

    public void queueInitialLoad(UUID jobId) {
        if (parseStatus == FileDatasetParseStatus.READY || parseStatus == FileDatasetParseStatus.SCHEMA_READY) {
            throw new IllegalStateException("已有数据的逻辑表不能重新执行初始装载");
        }
        beginLoad(jobId);
        this.parsedMetadata = null;
        this.parseStatus = FileDatasetParseStatus.QUEUED;
    }

    public void startLoad(UUID jobId) {
        requireCurrentLoad(jobId);
        if (parseStatus == FileDatasetParseStatus.QUEUED) {
            parseStatus = FileDatasetParseStatus.PARSING;
        }
    }

    public void requeueLoad(UUID jobId) {
        requireCurrentLoad(jobId);
        if (parseStatus == FileDatasetParseStatus.PARSING) {
            parseStatus = FileDatasetParseStatus.QUEUED;
        }
    }

    public void completeInitialLoad(
            UUID jobId,
            String parsedMetadata,
            boolean previewSupported
    ) {
        requireCurrentLoad(jobId);
        this.parsedMetadata = required(parsedMetadata, "解析元数据不能为空");
        this.parseStatus = previewSupported
                ? FileDatasetParseStatus.READY : FileDatasetParseStatus.SCHEMA_READY;
        this.currentLoadJobId = null;
    }

    public void beginDataChange(UUID jobId) {
        if (parseStatus != FileDatasetParseStatus.READY
                && parseStatus != FileDatasetParseStatus.SCHEMA_READY) {
            throw new IllegalStateException("只有已经就绪的逻辑表才能追加或覆盖数据");
        }
        beginLoad(jobId);
    }

    public void completeDataChange(UUID jobId, String parsedMetadata, boolean previewSupported) {
        requireCurrentLoad(jobId);
        this.parsedMetadata = required(parsedMetadata, "解析元数据不能为空");
        this.parseStatus = previewSupported
                ? FileDatasetParseStatus.READY : FileDatasetParseStatus.SCHEMA_READY;
        this.currentLoadJobId = null;
    }

    public void clearCurrentLoad(UUID jobId) {
        requireCurrentLoad(jobId);
        this.currentLoadJobId = null;
    }

    public void replaceSpatialSchema(
            String parsedMetadata,
            boolean previewSupported,
            CrsReference spatialReference
    ) {
        if (!hasData()) {
            throw new IllegalStateException("只有已经就绪的逻辑表才能重新确认空间 Schema");
        }
        if (currentLoadJobId != null) {
            throw new IllegalStateException("逻辑表存在正在执行的数据装载");
        }
        this.parsedMetadata = required(parsedMetadata, "解析元数据不能为空");
        this.parseStatus = previewSupported
                ? FileDatasetParseStatus.READY : FileDatasetParseStatus.SCHEMA_READY;
        this.spatialCrsAuthority = spatialReference.authority();
        this.spatialCrsCode = spatialReference.code();
    }

    public void handoffCurrentLoad(UUID currentJobId, UUID nextJobId) {
        requireCurrentLoad(currentJobId);
        if (nextJobId == null) {
            throw new IllegalArgumentException("后续表装载任务不能为空");
        }
        this.currentLoadJobId = nextJobId;
        if (parseStatus == FileDatasetParseStatus.PARSING) {
            parseStatus = FileDatasetParseStatus.QUEUED;
        }
    }

    public UUID getFileDatasetId() {
        return fileDatasetId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public FileDatasetParseStatus getParseStatus() {
        return parseStatus;
    }

    public String getParsedMetadata() {
        return parsedMetadata;
    }

    public UUID getCurrentLoadJobId() {
        return currentLoadJobId;
    }

    public CrsReference getSpatialReferenceOverride() {
        return spatialCrsAuthority == null || spatialCrsCode == null
                ? null : new CrsReference(spatialCrsAuthority, spatialCrsCode);
    }

    public boolean hasData() {
        return parseStatus == FileDatasetParseStatus.READY
                || parseStatus == FileDatasetParseStatus.SCHEMA_READY;
    }

    private void beginLoad(UUID jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("表装载任务不能为空");
        }
        if (currentLoadJobId != null) {
            throw new IllegalStateException("逻辑表已经存在正在执行的数据装载");
        }
        currentLoadJobId = jobId;
    }

    private void requireCurrentLoad(UUID jobId) {
        if (jobId == null || !jobId.equals(currentLoadJobId)) {
            throw new IllegalStateException("表数据装载任务已经变化");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
