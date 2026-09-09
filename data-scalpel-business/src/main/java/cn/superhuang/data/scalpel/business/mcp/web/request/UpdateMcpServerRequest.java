package cn.superhuang.data.scalpel.business.mcp.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateMcpServerRequest(
        @NotBlank @Size(max=100) String name,
        UUID directoryId,
        @Size(max=1000) String description,
        @Size(max=20000) String instructions
) {}
