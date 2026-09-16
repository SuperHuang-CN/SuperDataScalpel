package cn.superhuang.data.scalpel.business.ontology.repository;

import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeReference;
import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeReferenceKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BusinessObjectTypeReferenceRepository extends JpaRepository<BusinessObjectTypeReference, UUID> {
    void deleteAllByObjectTypeId(UUID objectTypeId);

    List<BusinessObjectTypeReference> findAllByReferenceKindAndResourceIdIn(
            BusinessObjectTypeReferenceKind referenceKind,
            Collection<UUID> resourceIds
    );

    List<BusinessObjectTypeReference> findAllByReferenceKindAndResourceId(
            BusinessObjectTypeReferenceKind referenceKind,
            UUID resourceId
    );
}
