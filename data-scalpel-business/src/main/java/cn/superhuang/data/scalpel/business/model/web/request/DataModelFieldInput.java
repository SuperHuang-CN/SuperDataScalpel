package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DataModelFieldInput(
        UUID id,
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "字段编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull PlatformDataType fieldType,
        @Min(1) Integer length,
        @Min(1) @Max(38) Integer precision,
        @Min(0) @Max(38) Integer scale,
        @Valid GeometryTypeDefinition geometry,
        @NotNull Boolean nullable,
        @NotNull Boolean primaryKey,
        @Min(0) int sortOrder,
        @Size(max = 500) String description,
        UUID standardDictionaryId
) {
}
