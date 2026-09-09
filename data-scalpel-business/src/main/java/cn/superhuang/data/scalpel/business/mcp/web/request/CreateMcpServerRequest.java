package cn.superhuang.data.scalpel.business.mcp.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateMcpServerRequest(
        @NotBlank @Size(max=64) @Pattern(regexp="[a-z][a-z0-9_-]{1,63}") String code,
        @NotBlank @Size(max=100) String name,
        UUID directoryId,
        @Size(max=1000) String description,
        @Size(max=20000) String instructions
) {}
