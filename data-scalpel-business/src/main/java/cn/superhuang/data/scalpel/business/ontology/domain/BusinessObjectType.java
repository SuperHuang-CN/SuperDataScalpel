package cn.superhuang.data.scalpel.business.ontology.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.search.SearchExcluded;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** Metadata for one business-facing object type. Source business data never lives in this entity. */
@Entity
@Table(name = "ds_business_object_type", indexes = @Index(name = "idx_ds_business_object_directory", columnList = "directory_id"))
public class BusinessObjectType extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64, updatable = false)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "directory_id")
    private UUID directoryId;

    @Column(length = 100)
    private String ownerName;

    @Column(length = 1000)
    private String summary;

    @Column(nullable = false)
    private boolean enabled;

    @SearchExcluded
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String definition;

    protected BusinessObjectType() {
    }

    public static BusinessObjectType create(String code, String definition) {
        BusinessObjectType type = new BusinessObjectType();
        type.code = code;
        type.enabled = true;
        type.definition = definition;
        return type;
    }

    public void update(String name, UUID directoryId, String ownerName, String summary) {
        this.name = name;
        this.directoryId = directoryId;
        this.ownerName = ownerName;
        this.summary = summary;
    }

    public void saveDefinition(String definition) {
        this.definition = definition;
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public UUID getDirectoryId() {
        return directoryId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDefinition() {
        return definition;
    }
}
