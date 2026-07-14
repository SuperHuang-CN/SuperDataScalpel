package cn.superhuang.data.scalpel.business.model.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateDataModelRequest(
        @NotBlank @Size(max = 100) String name,
        UUID directoryId,
        @NotNull UUID storageDataSourceId,
        @Size(max = 128) String catalogName,
        @Size(max = 128) String schemaName,
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,127}", message = "物理表名只能包含字母、数字和下划线，且必须以字母开头")
        String physicalTableName,
        @Size(max = 1000) String description
) {
}
