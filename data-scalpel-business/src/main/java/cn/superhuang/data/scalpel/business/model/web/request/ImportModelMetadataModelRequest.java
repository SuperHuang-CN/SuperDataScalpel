package cn.superhuang.data.scalpel.business.model.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ImportModelMetadataModelRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String directoryPath,
        UUID warehouseLayerId,
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,127}", message = "物理表名只能包含字母、数字和下划线，且必须以字母开头")
        String physicalTableName,
        @Size(max = 16) List<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "ClickHouse 排序键只能是字段编码") String> clickHouseOrderByColumns,
        @Size(max = 1000) String description,
        @NotEmpty @Size(max = 500) List<@Valid DataModelFieldInput> fields
) {

    public ImportModelMetadataModelRequest {
        clickHouseOrderByColumns = clickHouseOrderByColumns == null
                ? List.of() : List.copyOf(clickHouseOrderByColumns);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
