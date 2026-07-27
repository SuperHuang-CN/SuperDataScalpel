package cn.superhuang.data.scalpel.business.service.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.contract.service.SqlServiceParameterDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** One ordered named parameter belonging to a SQL data service. */
@Entity
@Table(name = "ds_sql_data_service_parameter", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ds_sql_service_parameter_name", columnNames = {"data_service_id", "name"}),
        @UniqueConstraint(name = "uk_ds_sql_service_parameter_order", columnNames = {"data_service_id", "sort_order"})
})
public class SqlDataServiceParameter extends BaseEntity {

    @Column(name = "data_service_id", nullable = false, updatable = false)
    private UUID dataServiceId;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "parameter_type", nullable = false, length = 32)
    private PlatformDataType type;

    @Column(name = "type_length")
    private Integer length;

    @Column(name = "numeric_precision")
    private Integer precision;

    @Column(name = "numeric_scale")
    private Integer scale;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(length = 500)
    private String description;

    protected SqlDataServiceParameter() {
    }

    public static SqlDataServiceParameter create(
            UUID dataServiceId,
            SqlServiceParameterDefinition definition,
            int sortOrder
    ) {
        SqlDataServiceParameter parameter = new SqlDataServiceParameter();
        parameter.dataServiceId = dataServiceId;
        parameter.name = definition.name();
        parameter.type = definition.typeDefinition().type();
        parameter.length = definition.typeDefinition().length();
        parameter.precision = definition.typeDefinition().precision();
        parameter.scale = definition.typeDefinition().scale();
        parameter.required = definition.required();
        parameter.sortOrder = sortOrder;
        parameter.description = definition.description();
        return parameter;
    }

    public SqlServiceParameterDefinition toDefinition() {
        return new SqlServiceParameterDefinition(
                name, new PlatformTypeDefinition(type, length, precision, scale), required, description
        );
    }

    public UUID getDataServiceId() {
        return dataServiceId;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
