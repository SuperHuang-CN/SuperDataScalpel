package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.contract.execution.CanvasTrialSpec;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CanvasTrialRunRequest(
        @Min(1) int baseDefinitionVersion,
        @NotNull @Valid CanvasDefinition definition,
        @NotBlank @Size(max = 100) String targetNodeId,
        @NotBlank @Size(max = 255) String tableName,
        @NotNull @Size(min = 1, max = CanvasTrialSpec.MAX_COLUMNS)
        List<@NotBlank @Size(max = 255) String> columnNames
) {
    public CanvasTrialRunRequest {
        columnNames = columnNames == null ? null : List.copyOf(columnNames);
    }
}
