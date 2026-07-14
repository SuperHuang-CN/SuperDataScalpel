package cn.superhuang.data.scalpel.business.system.access.service;

import cn.superhuang.data.scalpel.business.system.access.domain.SystemPermission;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemPermissionDefinition;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemRole;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemRolePermission;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemUser;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemPermissionRepository;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemRolePermissionRepository;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemRoleRepository;
import cn.superhuang.data.scalpel.business.system.access.repository.SystemUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
class SystemAccessInitializer {

    static final String SUPER_ADMIN_ROLE_CODE = "super_admin";

    @Bean
    @Order(0)
    ApplicationRunner initializeSystemAccess(
            SystemPermissionRepository permissionRepository,
            SystemRoleRepository roleRepository,
            SystemRolePermissionRepository rolePermissionRepository,
            SystemUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager,
            @Value("${data-scalpel.security.admin.username}") String administratorUsername,
            @Value("${data-scalpel.security.admin.password}") String administratorPassword
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return arguments -> transactionTemplate.executeWithoutResult(status -> synchronize(
                permissionRepository, roleRepository, rolePermissionRepository, userRepository,
                passwordEncoder, administratorUsername, administratorPassword));
    }

    void synchronize(
            SystemPermissionRepository permissionRepository,
            SystemRoleRepository roleRepository,
            SystemRolePermissionRepository rolePermissionRepository,
            SystemUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            String administratorUsername,
            String administratorPassword
    ) {
        Map<String, SystemPermission> permissionsByCode = new HashMap<>();
        for (SystemPermission permission : permissionRepository.findAll()) {
            permissionsByCode.put(permission.getCode(), permission);
        }
        Set<String> declaredCodes = Arrays.stream(SystemPermissionDefinition.values())
                .map(SystemPermissionDefinition::getCode)
                .collect(Collectors.toSet());
        for (SystemPermissionDefinition definition : SystemPermissionDefinition.values()) {
            SystemPermission permission = permissionsByCode.get(definition.getCode());
            if (permission == null) {
                permission = SystemPermission.create(
                        definition.getCode(), definition.getModule(), definition.getName(), definition.getDescription(), definition.getSortOrder()
                );
            } else {
                permission.updateDefinition(
                        definition.getModule(), definition.getName(), definition.getDescription(), definition.getSortOrder()
                );
            }
            permissionRepository.save(permission);
        }
        permissionRepository.findAll().stream()
                .filter(permission -> !declaredCodes.contains(permission.getCode()))
                .filter(SystemPermission::isActive)
                .forEach(SystemPermission::deactivate);
        permissionRepository.flush();

        SystemRole superAdmin = roleRepository.findByCode(SUPER_ADMIN_ROLE_CODE)
                .orElseGet(() -> roleRepository.saveAndFlush(SystemRole.create(
                        SUPER_ADMIN_ROLE_CODE, "超级管理员", "系统内置管理员，拥有全部有效权限。", true
                )));
        List<SystemPermission> activePermissions = permissionRepository.findAll().stream()
                .filter(SystemPermission::isActive)
                .toList();
        rolePermissionRepository.deleteAllByRoleId(superAdmin.getId());
        rolePermissionRepository.flush();
        rolePermissionRepository.saveAll(activePermissions.stream()
                .map(permission -> SystemRolePermission.create(superAdmin.getId(), permission.getId()))
                .toList());

        String username = administratorUsername.trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.save(SystemUser.create(
                    username, "系统管理员", passwordEncoder.encode(administratorPassword), superAdmin.getId(), true
            ));
        }
    }
}
