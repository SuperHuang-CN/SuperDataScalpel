package cn.superhuang.data.scalpel.dialect.api;

import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewColumn;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewData;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewLimits;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewMetadata;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewViewport;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

/** Narrow, read-only capability for rendering PostGIS-backed model fields outside ordinary data queries. */
public interface SpatialPreviewDialect {

    SpatialPreviewMetadata inspectSpatialPreview(
            Connection connection,
            TableIdentifier table,
            List<SpatialPreviewColumn> columns,
            Duration timeout
    ) throws SQLException;

    SpatialPreviewData readSpatialPreview(
            Connection connection,
            TableIdentifier table,
            SpatialPreviewColumn column,
            SpatialPreviewViewport viewport,
            SpatialPreviewLimits limits,
            Duration timeout
    ) throws SQLException;
}
