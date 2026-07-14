package cn.superhuang.data.scalpel.business.system.access.domain;

/** The code-declared, stable permission catalogue. It is synchronized to the database at startup. */
public enum SystemPermissionDefinition {

    SYSTEM_USER_VIEW("system.user.view", "系统管理", "查看用户", "查看系统用户及其角色", 10),
    SYSTEM_USER_MANAGE("system.user.manage", "系统管理", "管理用户", "新增、修改、重置密码和删除系统用户", 20),
    SYSTEM_ROLE_VIEW("system.role.view", "系统管理", "查看角色", "查看角色及其权限", 30),
    SYSTEM_ROLE_MANAGE("system.role.manage", "系统管理", "管理角色", "新增、修改、授权和删除角色", 40),
    SYSTEM_PERMISSION_VIEW("system.permission.view", "系统管理", "查看权限", "查看由系统代码声明的权限目录", 50),
    SYSTEM_CONFIGURATION_VIEW("system.configuration.view", "系统管理", "查看系统配置", "查看程序声明的系统配置", 60),
    SYSTEM_CONFIGURATION_UPDATE("system.configuration.update", "系统管理", "修改系统配置", "修改系统配置当前值", 70),
    DIRECTORY_VIEW("directory.view", "通用目录", "查看目录", "查看业务目录树", 100),
    DIRECTORY_MANAGE("directory.manage", "通用目录", "管理目录", "新增、修改和删除业务目录", 110),
    DATA_SOURCE_VIEW("datasource.view", "数据源管理", "查看数据源", "查询数据源及连接详情", 200),
    DATA_SOURCE_CREATE("datasource.create", "数据源管理", "新增数据源", "新增数据源连接", 210),
    DATA_SOURCE_UPDATE("datasource.update", "数据源管理", "修改数据源", "修改数据源连接", 220),
    DATA_SOURCE_DELETE("datasource.delete", "数据源管理", "删除数据源", "删除数据源连接", 230),
    DATA_SOURCE_TEST("datasource.test", "数据源管理", "测试数据源", "测试数据源连接", 240),
    DATA_SOURCE_METADATA("datasource.metadata", "数据源管理", "读取数据源元数据", "读取库表、字段和数据预览", 250),
    MODEL_VIEW("model.view", "模型管理", "查看模型", "查询模型及字段定义", 300),
    MODEL_CREATE("model.create", "模型管理", "新增模型", "新增模型元数据", 310),
    MODEL_UPDATE("model.update", "模型管理", "修改模型", "修改模型元数据和字段定义", 320),
    MODEL_DELETE("model.delete", "模型管理", "删除模型", "删除模型元数据", 330),
    MODEL_PUBLISH("model.publish", "模型管理", "发布模型", "发布、停用和启用模型元数据", 340);

    private final String code;
    private final String module;
    private final String name;
    private final String description;
    private final int sortOrder;

    SystemPermissionDefinition(String code, String module, String name, String description, int sortOrder) {
        this.code = code;
        this.module = module;
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public String getCode() {
        return code;
    }

    public String getModule() {
        return module;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
