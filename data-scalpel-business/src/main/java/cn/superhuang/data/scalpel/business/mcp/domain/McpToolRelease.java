package cn.superhuang.data.scalpel.business.mcp.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "ds_mcp_tool_release", indexes = @Index(name = "idx_ds_mcp_tool_release", columnList = "release_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_ds_mcp_tool_release_code", columnNames = {"release_id", "code"}))
public class McpToolRelease extends BaseEntity {
    @Column(name = "release_id", nullable = false, updatable = false)
    private UUID releaseId;
    @Column(name = "source_tool_id", nullable = false, updatable = false)
    private UUID sourceToolId;
    @Column(nullable = false, length = 64, updatable = false)
    private String code;
    @Column(nullable = false, length = 100, updatable = false)
    private String name;
    @Column(length = 1000, updatable = false)
    private String description;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(name = "input_schema_json", nullable = false, updatable = false)
    private String inputSchemaJson;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(name = "output_schema_json", updatable = false)
    private String outputSchemaJson;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable = false, updatable = false)
    private String script;
    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    protected McpToolRelease() {}
    public static McpToolRelease from(UUID releaseId, McpTool tool) {
        McpToolRelease item = new McpToolRelease();
        item.releaseId = releaseId; item.sourceToolId = tool.getId(); item.code = tool.getCode(); item.name = tool.getName();
        item.description = tool.getDescription(); item.inputSchemaJson = tool.getInputSchemaJson();
        item.outputSchemaJson = tool.getOutputSchemaJson(); item.script = tool.getScript(); item.sortOrder = tool.getSortOrder();
        return item;
    }
    public UUID getReleaseId() { return releaseId; }
    public UUID getSourceToolId() { return sourceToolId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getInputSchemaJson() { return inputSchemaJson; }
    public String getOutputSchemaJson() { return outputSchemaJson; }
    public String getScript() { return script; }
    public int getSortOrder() { return sortOrder; }
}
