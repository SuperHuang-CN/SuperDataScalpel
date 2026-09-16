package cn.superhuang.data.scalpel.business.ontology.service;

import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeReferenceKind;
import cn.superhuang.data.scalpel.business.ontology.repository.BusinessObjectTypeReferenceRepository;
import cn.superhuang.data.scalpel.business.ontology.repository.BusinessObjectTypeRepository;
import cn.superhuang.data.scalpel.web.error.CodedProblemException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.UUID;

/** Protects model fields used by current business-object definitions. */
@Service
public class BusinessObjectTypeReferenceGuard {

    private final BusinessObjectTypeReferenceRepository references;
    private final BusinessObjectTypeRepository objectTypes;
    private final DataModelRepository models;

    public BusinessObjectTypeReferenceGuard(
            BusinessObjectTypeReferenceRepository references,
            BusinessObjectTypeRepository objectTypes,
            DataModelRepository models
    ) {
        this.references = references;
        this.objectTypes = objectTypes;
        this.models = models;
    }

    @Transactional
    public void assertFieldsRemovable(UUID modelId, Collection<UUID> fieldIds) {
        if (fieldIds.isEmpty()) {
            return;
        }
        models.findByIdForUpdate(modelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模型不存在"));
        var typeIds = references.findAllByReferenceKindAndResourceIdIn(
                        BusinessObjectTypeReferenceKind.MODEL_FIELD, fieldIds)
                .stream().map(reference -> reference.getObjectTypeId()).distinct().toList();
        if (typeIds.isEmpty()) {
            return;
        }
        String names = String.join("、", objectTypes.findAllById(typeIds).stream()
                .map(type -> type.getName()).toList());
        throw new CodedProblemException(
                HttpStatus.CONFLICT,
                "MODEL_REFERENCED",
                "字段仍被业务对象类型引用，请先调整对象定义" + (names.isBlank() ? "" : "：" + names)
        );
    }
}
