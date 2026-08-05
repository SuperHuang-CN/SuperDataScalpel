package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;
import java.util.UUID;

/** Mutable design-time Groovy service definition. */
@Entity
@Table(name = "ds_script_data_service_definition", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_script_service_definition_service", columnNames = "data_service_id"
))
public class ScriptDataServiceDefinition extends BaseEntity {

    @Column(name = "data_service_id", nullable = false, updatable = false)
    private UUID dataServiceId;

    @Column(name = "data_source_id", nullable = false)
    private UUID dataSourceId;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "script", nullable = false)
    private String script;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "examples_json")
    private String examplesJson;

    @Column(nullable = false)
    private int version;

    protected ScriptDataServiceDefinition() {
    }

    public static ScriptDataServiceDefinition create(UUID dataServiceId, UUID dataSourceId, String script) {
        return create(dataServiceId, dataSourceId, script, null);
    }

    public static ScriptDataServiceDefinition create(
            UUID dataServiceId,
            UUID dataSourceId,
            String script,
            String examplesJson
    ) {
        ScriptDataServiceDefinition definition = new ScriptDataServiceDefinition();
        definition.dataServiceId = require(dataServiceId, "数据服务");
        definition.version = 1;
        definition.apply(dataSourceId, script, examplesJson);
        return definition;
    }

    public void update(UUID dataSourceId, String script) {
        update(dataSourceId, script, examplesJson);
    }

    public void update(UUID dataSourceId, String script, String examplesJson) {
        UUID previousDataSourceId = this.dataSourceId;
        String previousScript = this.script;
        String previousExamplesJson = this.examplesJson;
        apply(dataSourceId, script, examplesJson);
        if (!this.dataSourceId.equals(previousDataSourceId)
                || !this.script.equals(previousScript)
                || !Objects.equals(this.examplesJson, previousExamplesJson)) {
            if (version == Integer.MAX_VALUE) {
                throw new IllegalStateException("数据服务定义版本已达到最大值");
            }
            version++;
        }
    }

    private void apply(UUID dataSourceId, String script, String examplesJson) {
        this.dataSourceId = require(dataSourceId, "数据源");
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("脚本不能为空");
        }
        if (script.length() > 500_000) {
            throw new IllegalArgumentException("脚本长度不能超过 500000 字符");
        }
        this.script = script;
        this.examplesJson = examplesJson;
    }

    public UUID getDataServiceId() {
        return dataServiceId;
    }

    public UUID getDataSourceId() {
        return dataSourceId;
    }

    public String getScript() {
        return script;
    }

    public String getExamplesJson() {
        return examplesJson;
    }

    public int getVersion() {
        return version;
    }

    private static UUID require(UUID value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value;
    }
}
