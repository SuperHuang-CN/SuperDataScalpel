package cn.superhuang.data.scalpel.business.system.access.web.request;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record UpdateSystemRolePermissionsRequest(@NotNull Set<UUID> permissionIds) {
}
