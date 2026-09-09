package cn.superhuang.data.scalpel.business.mcp.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ds_mcp_tool", indexes = @Index(name = "idx_ds_mcp_tool_server", columnList = "server_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_ds_mcp_tool_server_code", columnNames = {"server_id", "code"}))
public class McpTool extends BaseEntity {
    @Column(name = "server_id", nullable = false, updatable = false)
    private UUID serverId;
    @Column(nullable = false, length = 64)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 1000)
    private String description;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "input_schema_json", nullable = false)
    private String inputSchemaJson;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "output_schema_json")
    private String outputSchemaJson;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String script;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "examples_json")
    private String examplesJson;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private long revision;

    protected McpTool() {}
    public static McpTool create(UUID serverId, String code, String name, String description, String inputSchemaJson,
                                 String outputSchemaJson, String script, String examplesJson, boolean enabled, int sortOrder) {
        McpTool tool = new McpTool();
        tool.serverId = Objects.requireNonNull(serverId);
        tool.revision = 1;
        tool.apply(code, name, description, inputSchemaJson, outputSchemaJson, script, examplesJson, enabled, sortOrder);
        return tool;
    }
    public boolean update(String code, String name, String description, String inputSchemaJson, String outputSchemaJson,
                          String script, String examplesJson, boolean enabled, int sortOrder) {
        String old = snapshot();
        apply(code, name, description, inputSchemaJson, outputSchemaJson, script, examplesJson, enabled, sortOrder);
        boolean changed = !old.equals(snapshot());
        if (changed) revision++;
        return changed;
    }
    private void apply(String code, String name, String description, String inputSchemaJson, String outputSchemaJson,
                       String script, String examplesJson, boolean enabled, int sortOrder) {
        this.code = normalizeCode(code);
        this.name = required(name, "名称");
        this.description = optional(description);
        this.inputSchemaJson = required(inputSchemaJson, "Input Schema");
        this.outputSchemaJson = optional(outputSchemaJson);
        this.script = required(script, "Groovy 脚本");
        this.examplesJson = optional(examplesJson);
        this.enabled = enabled;
        this.sortOrder = sortOrder;
    }
    private String snapshot() { return String.join("\u0000", code, name, String.valueOf(description), inputSchemaJson,
            String.valueOf(outputSchemaJson), script, String.valueOf(examplesJson), String.valueOf(enabled), String.valueOf(sortOrder)); }
    private static String normalizeCode(String value) {
        String normalized = required(value, "Tool 编码").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_-]{1,63}")) throw new IllegalArgumentException("Tool 编码格式不正确");
        return normalized;
    }
    private static String required(String value, String label) { if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空"); return value.trim(); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public UUID getServerId() { return serverId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getInputSchemaJson() { return inputSchemaJson; }
    public String getOutputSchemaJson() { return outputSchemaJson; }
    public String getScript() { return script; }
    public String getExamplesJson() { return examplesJson; }
    public boolean isEnabled() { return enabled; }
    public int getSortOrder() { return sortOrder; }
    public long getRevision() { return revision; }
}
