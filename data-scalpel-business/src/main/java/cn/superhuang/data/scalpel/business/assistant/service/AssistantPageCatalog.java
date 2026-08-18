package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import org.springframework.security.core.Authentication;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class AssistantPageCatalog {

    private AssistantPageCatalog() {
    }

    public enum Page {
        DASHBOARD("DASHBOARD", "工作台", null, null),
        ASSET_PORTAL("ASSET_PORTAL", "资产门户", null, null),
        ASSET_MANAGEMENT("ASSET_MANAGEMENT", "资产管理", "asset.view", null),
        ASSET_DOMAINS("ASSET_DOMAINS", "业务领域", "directory.view", DirectoryScope.ASSET),
        DATA_SOURCE_LIST("DATA_SOURCE_LIST", "数据源", "datasource.view", DirectoryScope.DATA_SOURCE),
        FILE_DATASET_LIST("FILE_DATASET_LIST", "文件数据集", "filedataset.view", DirectoryScope.FILE_DATASET),
        DICTIONARY_LIST("DICTIONARY_LIST", "码表管理", "standard.dictionary.view", null),
        MODEL_LIST("MODEL_LIST", "模型列表", "model.view", DirectoryScope.MODEL),
        MODEL_FIELD_TEMPLATES("MODEL_FIELD_TEMPLATES", "常用字段模板", "model.view", null),
        DATA_ENTRY_LIST("DATA_ENTRY_LIST", "数据填报", "dataentry.view", null),
        TASK_LIST("TASK_LIST", "任务列表", "task.view", DirectoryScope.TASK),
        TASK_ORCHESTRATION("TASK_ORCHESTRATION", "任务编排", "task.view", DirectoryScope.TASK),
        TASK_MASKING_RULES("TASK_MASKING_RULES", "脱敏规则", "task.view", null),
        DATA_SERVICE_LIST("DATA_SERVICE_LIST", "数据服务", "service.view", DirectoryScope.DATA_SERVICE),
        DATA_SERVICE_CONSUMERS("DATA_SERVICE_CONSUMERS", "消费者管理", "service.view", null),
        DATA_SERVICE_OPERATIONS("DATA_SERVICE_OPERATIONS", "调用统计", "service.view", null),
        SERVICE_ENGINE("SERVICE_ENGINE", "服务引擎", "service.engine.view", null),
        COMPUTE_ENGINE("COMPUTE_ENGINE", "计算引擎", "compute.engine.view", null),
        SYSTEM_CONFIGURATIONS("SYSTEM_CONFIGURATIONS", "系统配置", "system.configuration.view", null),
        SYSTEM_WAREHOUSE_LAYERS("SYSTEM_WAREHOUSE_LAYERS", "数仓分层", "system.configuration.view", null),
        SYSTEM_AI_MODELS("SYSTEM_AI_MODELS", "AI 模型", "system.configuration.view", null),
        SYSTEM_USERS("SYSTEM_USERS", "用户管理", "system.user.view", null),
        SYSTEM_ROLES("SYSTEM_ROLES", "角色管理", "system.role.view", null),
        SYSTEM_PERMISSIONS("SYSTEM_PERMISSIONS", "权限管理", "system.permission.view", null);

        private final String key;
        private final String label;
        private final String permission;
        private final DirectoryScope directoryScope;

        Page(String key, String label, String permission, DirectoryScope directoryScope) {
            this.key = key;
            this.label = label;
            this.permission = permission;
            this.directoryScope = directoryScope;
        }

        public String key() { return key; }
        public String label() { return label; }
        public String permission() { return permission; }
        public DirectoryScope directoryScope() { return directoryScope; }
    }

    public static List<Page> available(Authentication authentication) {
        return Arrays.stream(Page.values()).filter(page -> allowed(authentication, page)).toList();
    }

    public static Optional<Page> find(String pageKey) {
        if (pageKey == null) return Optional.empty();
        return Arrays.stream(Page.values()).filter(page -> page.key().equals(pageKey)).findFirst();
    }

    public static boolean allowed(Authentication authentication, Page page) {
        return page.permission() == null || hasAuthority(authentication, page.permission());
    }

    public static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
