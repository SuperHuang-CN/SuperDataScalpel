package cn.superhuang.data.scalpel.business.system.access.service;

import cn.superhuang.data.scalpel.business.system.access.domain.SystemPermission;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemRole;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemRolePermission;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemUser;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemPermissionRepository;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemRolePermissionRepository;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemRoleRepository;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemUserRepository;
import cn.superhuang.data.scalpel.business.system.access.web.request.CreateSystemRoleRequest;
import cn.superhuang.data.scalpel.business.system.access.web.request.CreateSystemUserRequest;
import cn.superhuang.data.scalpel.business.system.access.web.request.ResetSystemUserPasswordRequest;
import cn.superhuang.data.scalpel.business.system.access.web.request.UpdateSystemRolePermissionsRequest;
import cn.superhuang.data.scalpel.business.system.access.web.request.UpdateSystemRoleRequest;
import cn.superhuang.data.scalpel.business.system.access.web.request.UpdateSystemUserRequest;
import cn.superhuang.data.scalpel.business.system.access.web.response.SystemPermissionResponse;
import cn.superhuang.data.scalpel.business.system.access.web.response.SystemRoleResponse;
import cn.superhuang.data.scalpel.business.system.access.web.response.SystemUserResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SystemAccessService {

    private final SystemUserRepository userRepository;
    private final SystemRoleRepository roleRepository;
    private final SystemPermissionRepository permissionRepository;
    private final SystemRolePermissionRepository rolePermissionRepository;
    private final SearchEngine searchEngine;
    private final PasswordEncoder passwordEncoder;

    public SystemAccessService(
            SystemUserRepository userRepository,
            SystemRoleRepository roleRepository,
            SystemPermissionRepository permissionRepository,
            SystemRolePermissionRepository rolePermissionRepository,
            SearchEngine searchEngine,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.searchEngine = searchEngine;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<SystemUserResponse> searchUsers(SearchRequest request) {
        var page = searchEngine.search(request, SystemUser.class, userRepository);
        Map<UUID, SystemRole> roles = rolesById(page.getContent().stream().map(SystemUser::getRoleId).toList());
        return new PageResponse<>(
                page.getContent().stream().map(user -> SystemUserResponse.from(user, requiredRole(roles, user.getRoleId()))).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public SystemUserResponse getUser(UUID id) {
        SystemUser user = requireUser(id);
        return SystemUserResponse.from(user, requireRole(user.getRoleId()));
    }

    @Transactional
    public SystemUserResponse createUser(CreateSystemUserRequest request) {
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        SystemRole role = requireRole(request.roleId());
        SystemUser user = SystemUser.create(
                username, request.displayName(), passwordEncoder.encode(request.password()), role.getId(),
                request.enabled() == null || request.enabled()
        );
        return SystemUserResponse.from(userRepository.saveAndFlush(user), role);
    }

    @Transactional
    public SystemUserResponse updateUser(UUID id, UpdateSystemUserRequest request, String currentUsername) {
        SystemUser user = requireUser(id);
        if (user.getUsername().equals(normalizeUsername(currentUsername)) && !request.enabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能停用当前登录用户");
        }
        SystemRole role = requireRole(request.roleId());
        user.update(request.displayName(), role.getId(), request.enabled());
        return SystemUserResponse.from(userRepository.saveAndFlush(user), role);
    }

    @Transactional
    public void resetUserPassword(UUID id, ResetSystemUserPasswordRequest request) {
        SystemUser user = requireUser(id);
        user.resetPassword(passwordEncoder.encode(request.password()));
        userRepository.saveAndFlush(user);
    }

    @Transactional
    public void deleteUser(UUID id, String currentUsername) {
        SystemUser user = requireUser(id);
        if (user.getUsername().equals(normalizeUsername(currentUsername))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能删除当前登录用户");
        }
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<SystemRoleResponse> searchRoles(SearchRequest request) {
        var page = searchEngine.search(request, SystemRole.class, roleRepository);
        Map<UUID, Set<UUID>> permissions = permissionsByRoleId(page.getContent().stream().map(SystemRole::getId).toList());
        return new PageResponse<>(
                page.getContent().stream().map(role -> SystemRoleResponse.from(role, permissions.getOrDefault(role.getId(), Set.of()))).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public SystemRoleResponse getRole(UUID id) {
        SystemRole role = requireRole(id);
        return SystemRoleResponse.from(role, permissionsByRoleId(List.of(id)).getOrDefault(id, Set.of()));
    }

    @Transactional
    public SystemRoleResponse createRole(CreateSystemRoleRequest request) {
        String code = normalizeCode(request.code());
        if (roleRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "角色编码已存在");
        }
        SystemRole role = SystemRole.create(code, request.name(), request.description(), false);
        return SystemRoleResponse.from(roleRepository.saveAndFlush(role), Set.of());
    }

    @Transactional
    public SystemRoleResponse updateRole(UUID id, UpdateSystemRoleRequest request) {
        SystemRole role = requireRole(id);
        role.update(request.name(), request.description());
        SystemRole saved = roleRepository.saveAndFlush(role);
        return SystemRoleResponse.from(saved, permissionsByRoleId(List.of(id)).getOrDefault(id, Set.of()));
    }

    @Transactional
    public SystemRoleResponse updateRolePermissions(UUID id, UpdateSystemRolePermissionsRequest request) {
        SystemRole role = requireRole(id);
        if (role.isBuiltIn()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "内置角色的权限由系统自动维护");
        }
        Set<UUID> permissionIds = Set.copyOf(request.permissionIds());
        List<SystemPermission> permissions = permissionRepository.findAllById(permissionIds);
        if (permissions.size() != permissionIds.size() || permissions.stream().anyMatch(permission -> !permission.isActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "权限不存在或已失效");
        }
        rolePermissionRepository.deleteAllByRoleId(id);
        rolePermissionRepository.flush();
        rolePermissionRepository.saveAll(permissionIds.stream()
                .map(permissionId -> SystemRolePermission.create(id, permissionId))
                .toList());
        return SystemRoleResponse.from(role, permissionIds);
    }

    @Transactional
    public void deleteRole(UUID id) {
        SystemRole role = requireRole(id);
        if (role.isBuiltIn()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "内置角色不能删除");
        }
        if (userRepository.existsByRoleId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "角色已被用户使用，不能删除");
        }
        rolePermissionRepository.deleteAllByRoleId(id);
        roleRepository.delete(role);
    }

    @Transactional(readOnly = true)
    public PageResponse<SystemPermissionResponse> searchPermissions(SearchRequest request) {
        var page = searchEngine.search(request, SystemPermission.class, permissionRepository);
        return new PageResponse<>(
                page.getContent().stream().map(SystemPermissionResponse::from).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public SystemPermissionResponse getPermission(UUID id) {
        return SystemPermissionResponse.from(permissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "权限不存在")));
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticationUser> findAuthenticationUser(String username) {
        return userRepository.findByUsername(normalizeUsername(username))
                .filter(SystemUser::isEnabled)
                .map(this::toAuthenticationUser);
    }

    private AuthenticationUser toAuthenticationUser(SystemUser user) {
        SystemRole role = requireRole(user.getRoleId());
        Set<UUID> permissionIds = permissionsByRoleId(List.of(role.getId())).getOrDefault(role.getId(), Set.of());
        List<String> permissionCodes = permissionRepository.findAllById(permissionIds).stream()
                .filter(SystemPermission::isActive)
                .map(SystemPermission::getCode)
                .sorted()
                .toList();
        return new AuthenticationUser(user.getUsername(), user.getPasswordHash(), role.getCode(), permissionCodes);
    }

    private Map<UUID, SystemRole> rolesById(Collection<UUID> roleIds) {
        Map<UUID, SystemRole> roles = new HashMap<>();
        for (SystemRole role : roleRepository.findAllById(roleIds)) {
            roles.put(role.getId(), role);
        }
        return roles;
    }

    private Map<UUID, Set<UUID>> permissionsByRoleId(Collection<UUID> roleIds) {
        Map<UUID, Set<UUID>> permissions = new HashMap<>();
        for (SystemRolePermission mapping : rolePermissionRepository.findAllByRoleIdIn(roleIds)) {
            permissions.computeIfAbsent(mapping.getRoleId(), ignored -> new HashSet<>()).add(mapping.getPermissionId());
        }
        return permissions;
    }

    private SystemUser requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private SystemRole requireRole(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不存在"));
    }

    private SystemRole requiredRole(Map<UUID, SystemRole> roles, UUID id) {
        SystemRole role = roles.get(id);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户关联的角色不存在");
        }
        return role;
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    public record AuthenticationUser(String username, String passwordHash, String roleCode, List<String> permissionCodes) {
    }
}
