package cn.superhuang.data.scalpel.business.ontology.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** Projection of the resources referenced by the currently saved definition. */
@Entity
@Table(
        name = "ds_business_object_type_reference",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ds_business_object_reference",
                columnNames = {"object_type_id", "reference_path"}
        ),
        indexes = {
                @Index(name = "idx_ds_business_object_reference_resource", columnList = "reference_kind,resource_id"),
                @Index(name = "idx_ds_business_object_reference_owner", columnList = "object_type_id")
        }
)
public class BusinessObjectTypeReference extends BaseEntity {

    @Column(name = "object_type_id", nullable = false)
    private UUID objectTypeId;

    @Column(name = "reference_path", nullable = false, length = 180)
    private String referencePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_kind", nullable = false, length = 32)
    private BusinessObjectTypeReferenceKind referenceKind;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    protected BusinessObjectTypeReference() {
    }

    public static BusinessObjectTypeReference create(
            UUID objectTypeId,
            String referencePath,
            BusinessObjectTypeReferenceKind referenceKind,
            UUID resourceId
    ) {
        BusinessObjectTypeReference reference = new BusinessObjectTypeReference();
        reference.objectTypeId = objectTypeId;
        reference.referencePath = referencePath;
        reference.referenceKind = referenceKind;
        reference.resourceId = resourceId;
        return reference;
    }

    public UUID getObjectTypeId() {
        return objectTypeId;
    }

    public String getReferencePath() {
        return referencePath;
    }

    public BusinessObjectTypeReferenceKind getReferenceKind() {
        return referenceKind;
    }

    public UUID getResourceId() {
        return resourceId;
    }
}
