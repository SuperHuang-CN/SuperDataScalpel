package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.ModelFieldTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ModelFieldTemplateResponse(
        UUID id,
        String code,
        String name,
        String category,
        String description,
        int sortOrder,
        boolean enabled,
        int version,
        int fieldCount,
        List<ModelFieldTemplateFieldResponse> fields,
        Instant createdAt,
        Instant updatedAt
) {
    public static ModelFieldTemplateResponse from(
            ModelFieldTemplate template,
            List<ModelFieldTemplateFieldResponse> fields
    ) {
        return new ModelFieldTemplateResponse(
                template.getId(), template.getCode(), template.getName(), template.getCategory(),
                template.getDescription(), template.getSortOrder(), template.isEnabled(), template.getVersion(),
                fields.size(),
                List.copyOf(fields), template.getCreatedAt(), template.getUpdatedAt()
        );
    }
}
