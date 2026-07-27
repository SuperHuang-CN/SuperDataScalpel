package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDataTaskRequest(
        @NotBlank @Size(max = 100) String name,
        UUID directoryId,
        @NotNull TaskType type,
        @Size(max = 1000) String description,
        UUID computeEngineId
) {
    public CreateDataTaskRequest(String name, UUID directoryId, TaskType type, String description) {
        this(name, directoryId, type, description, null);
    }
}
