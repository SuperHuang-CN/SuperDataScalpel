package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** Mutable design-time SQL query service definition. */
@Entity
@Table(name = "ds_sql_data_service_definition", uniqueConstraints = @UniqueConstraint(
        name = "uk_ds_sql_service_definition_service", columnNames = "data_service_id"
))
public class SqlDataServiceDefinition extends BaseEntity {

    @Column(name = "data_service_id", nullable = false, updatable = false)
    private UUID dataServiceId;

    @Column(name = "data_source_id", nullable = false)
    private UUID dataSourceId;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "sql_text", nullable = false)
    private String sqlText;

    @Column(nullable = false)
    private int version;

    protected SqlDataServiceDefinition() {
    }

    public static SqlDataServiceDefinition create(UUID dataServiceId, UUID dataSourceId, String sqlText) {
        SqlDataServiceDefinition definition = new SqlDataServiceDefinition();
        definition.dataServiceId = require(dataServiceId, "数据服务");
        definition.version = 1;
        definition.apply(dataSourceId, sqlText);
        return definition;
    }

    public void update(UUID dataSourceId, String sqlText, boolean relatedDefinitionChanged) {
        UUID previousDataSourceId = this.dataSourceId;
        String previousSql = this.sqlText;
        apply(dataSourceId, sqlText);
        if (relatedDefinitionChanged || !this.dataSourceId.equals(previousDataSourceId) || !this.sqlText.equals(previousSql)) {
            if (version == Integer.MAX_VALUE) throw new IllegalStateException("数据服务定义版本已达到最大值");
            version++;
        }
    }

    private void apply(UUID dataSourceId, String sqlText) {
        this.dataSourceId = require(dataSourceId, "数据源");
        if (sqlText == null || sqlText.isBlank()) throw new IllegalArgumentException("SQL 不能为空");
        if (sqlText.length() > 100_000) throw new IllegalArgumentException("SQL 长度不能超过 100000 字符");
        this.sqlText = sqlText.trim();
    }

    public UUID getDataServiceId() {
        return dataServiceId;
    }

    public UUID getDataSourceId() {
        return dataSourceId;
    }

    public String getSqlText() {
        return sqlText;
    }

    public int getVersion() {
        return version;
    }

    private static UUID require(UUID value, String label) {
        if (value == null) throw new IllegalArgumentException(label + "不能为空");
        return value;
    }
}
