package cn.superhuang.data.scalpel.business.task.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDataTaskRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 100) String name,
        UUID directoryId,
        @Size(max = 1000) String description
) {
}
