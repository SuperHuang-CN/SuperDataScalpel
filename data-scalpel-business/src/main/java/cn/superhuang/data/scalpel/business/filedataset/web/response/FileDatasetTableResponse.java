package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetParsedMetadata;
import cn.superhuang.data.scalpel.contract.type.CrsReference;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(description = "由一个或多个兼容文件来源组成的逻辑表、解析状态和格式专属元数据。")

public record FileDatasetTableResponse(
        @Schema(description = "文件数据集逻辑表 UUID。")
        UUID id,
        @Schema(description = "该表所属文件数据集的 UUID。")
        UUID fileDatasetId,
        @Schema(description = "逻辑表在所属文件数据集内的稳定技术编码。")
        String code,
        @Schema(description = "逻辑表显示名称；首尾无空白、内部连续空白已转换为下划线，并在所属文件数据集内忽略大小写唯一。")
        String name,
        @Schema(description = "表的解析状态：QUEUED 等待解析，PARSING 正在解析，SCHEMA_READY 仅 Schema 可用，READY 同时支持数据预览。")
        FileDatasetParseStatus parseStatus,
        @Schema(description = "当前启用并按顺序合并到该逻辑表的文件来源数量。")
        long sourceCount,
        @Schema(description = "所有当前来源的 parser-reported rowCount 合计。流式完整校验及带可靠元数据的格式通常为精确总数；Excel、GDB 等有界抽样解析器可能只报告最多 1000 条样本数，不能统一视为精确 COUNT。")
        long totalRowCount,
        @Schema(description = "当前正在改变表来源或重新解析 Schema 的作业 UUID；没有在途加载时为空。")
        UUID currentLoadJobId,
        @Schema(description = "最近一次成功初始加载、追加、覆盖或空间参考重解析保留的样本行数，最多 1000；多来源表中不表示所有当前来源的合计样本数。")
        int sampledRecordCount,
        @Schema(description = "最近一次成功解析结果是否存在未保留的记录；多来源表中只描述最近一次加载结果，不汇总其他来源。")
        boolean truncated,
        @Schema(description = "解析器对最近一次生效 Schema 的预览能力标记。READY 时为 true、SCHEMA_READY 时为 false；QUEUED/PARSING 初始状态下该投影目前也可能为 true，调用预览前仍必须确认 parseStatus=READY。")
        boolean previewSupported,
        @Schema(description = "最近一次成功解析结果的格式专属元数据，例如几何字段、坐标系、GeoParquet 版本、预览不可用原因或 Parquet 创建器；键集合随格式变化，多来源表中不汇总每个来源的元数据。")
        Map<String, Object> sourceMetadata,
        @Schema(description = "管理员为该表显式指定的坐标参考系；为空时使用文件自身元数据推断的坐标系。")
        CrsReference spatialReferenceOverride,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static FileDatasetTableResponse from(
            FileDatasetTable table,
            FileDatasetParsedMetadata metadata,
            long sourceCount,
            long totalRowCount
    ) {
        return new FileDatasetTableResponse(
                table.getId(), table.getFileDatasetId(), table.getCode(), table.getName(),
                table.getParseStatus(), sourceCount, totalRowCount, table.getCurrentLoadJobId(),
                metadata.sampledRecordCount(), metadata.truncated(),
                table.getParseStatus() != FileDatasetParseStatus.SCHEMA_READY,
                metadata.sourceMetadata(), table.getSpatialReferenceOverride(),
                table.getCreatedAt(), table.getUpdatedAt()
        );
    }
}
