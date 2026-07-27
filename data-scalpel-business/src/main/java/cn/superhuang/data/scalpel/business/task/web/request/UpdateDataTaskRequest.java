package cn.superhuang.data.scalpel.business.task.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateDataTaskRequest(
        @NotBlank @Size(max = 100) String name,
        UUID directoryId,
        @Size(max = 1000) String description,
        UUID computeEngineId
) {
    public UpdateDataTaskRequest(String name, UUID directoryId, String description) {
        this(name, directoryId, description, null);
    }
}
