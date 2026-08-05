package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;
import java.util.List;

public record CreateDataModelRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @NotBlank @Size(max = 100) String name,
        UUID directoryId,
        UUID warehouseLayerId,
        @NotNull UUID storageDataSourceId,
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,127}", message = "物理表名只能包含字母、数字和下划线，且必须以字母开头")
        String physicalTableName,
        PhysicalTableMode physicalTableMode,
        @Size(max = 16) List<@Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "ClickHouse 排序键只能是字段编码") String> clickHouseOrderByColumns,
        @Size(max = 1000) String description
) {
}
