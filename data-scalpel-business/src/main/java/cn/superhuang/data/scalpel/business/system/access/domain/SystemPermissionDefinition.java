package cn.superhuang.data.scalpel.business.system.access.domain;

/** The code-declared, stable permission catalogue. It is synchronized to the database at startup. */
public enum SystemPermissionDefinition {

    SYSTEM_USER_VIEW("system.user.view", "系统管理", "查看用户", "查看系统用户及其角色", 10),
    SYSTEM_USER_MANAGE("system.user.manage", "系统管理", "管理用户", "新增、修改、重置密码和删除系统用户", 20),
    SYSTEM_ROLE_VIEW("system.role.view", "系统管理", "查看角色", "查看角色及其权限", 30),
    SYSTEM_ROLE_MANAGE("system.role.manage", "系统管理", "管理角色", "新增、修改、授权和删除角色", 40),
    SYSTEM_PERMISSION_VIEW("system.permission.view", "系统管理", "查看权限", "查看由系统代码声明的权限目录", 50),
    SYSTEM_CONFIGURATION_VIEW(
            "system.configuration.view",
            "系统管理",
            "查看系统配置",
            "查看程序声明的系统配置和数仓分层配置",
            60
    ),
    SYSTEM_CONFIGURATION_UPDATE(
            "system.configuration.update",
            "系统管理",
            "修改系统配置",
            "修改系统配置当前值并管理数仓分层",
            70
    ),
    DIRECTORY_VIEW("directory.view", "通用目录", "查看目录", "查看业务目录树", 100),
    DIRECTORY_MANAGE("directory.manage", "通用目录", "管理目录", "新增、修改和删除业务目录", 110),
    ASSET_VIEW("asset.view", "数据资产", "查看资产", "查看资产登记和来源同步状态", 120),
    ASSET_MANAGE("asset.manage", "数据资产", "管理资产", "登记、维护、发布、下线、检查和同步资产", 130),
    DATA_SOURCE_VIEW("datasource.view", "数据源管理", "查看数据源", "查询数据源及连接详情", 200),
    DATA_SOURCE_CREATE("datasource.create", "数据源管理", "新增数据源", "新增数据源连接", 210),
    DATA_SOURCE_UPDATE("datasource.update", "数据源管理", "修改数据源", "修改数据源连接", 220),
    DATA_SOURCE_DELETE("datasource.delete", "数据源管理", "删除数据源", "删除数据源连接", 230),
    DATA_SOURCE_TEST("datasource.test", "数据源管理", "测试数据源", "测试数据源连接", 240),
    DATA_SOURCE_METADATA("datasource.metadata", "数据源管理", "读取数据源元数据", "读取库表、字段和数据预览", 250),
    FILE_DATASET_VIEW("filedataset.view", "文件数据集", "查看文件数据集", "查询文件数据集及下载原始文件", 260),
    FILE_DATASET_CREATE("filedataset.create", "文件数据集", "新增文件数据集", "上传并新增文件数据集", 270),
    FILE_DATASET_UPDATE("filedataset.update", "文件数据集", "修改文件数据集", "修改信息、解析参数或替换内容", 280),
    FILE_DATASET_DELETE("filedataset.delete", "文件数据集", "删除文件数据集", "删除文件数据集及其存储内容", 290),
    STANDARD_DICTIONARY_VIEW(
            "standard.dictionary.view",
            "数据标准",
            "查看码表",
            "查询业务码表、树形码值及模型字段引用",
            295
    ),
    STANDARD_DICTIONARY_MANAGE(
            "standard.dictionary.manage",
            "数据标准",
            "管理码表",
            "新增、修改、导入、启停和删除业务码表",
            296
    ),
    MODEL_VIEW("model.view", "模型管理", "查看模型", "查询模型、字段定义和常用字段模板", 300),
    MODEL_CREATE("model.create", "模型管理", "新增模型", "新增模型元数据", 310),
    MODEL_UPDATE("model.update", "模型管理", "修改模型", "修改模型元数据和字段定义，并维护常用字段模板", 320),
    MODEL_DELETE("model.delete", "模型管理", "删除模型", "删除模型元数据", 330),
    MODEL_PUBLISH("model.publish", "模型管理", "发布模型", "发布、停用和启用模型元数据", 340),
    DATA_ENTRY_VIEW("dataentry.view", "数据填报", "查看填报", "查看填报表单、健康状态、物理数据和操作日志", 342),
    DATA_ENTRY_MANAGE("dataentry.manage", "数据填报", "管理填报", "创建、配置、发布、停用和删除填报表单", 344),
    DATA_ENTRY_SUBMIT("dataentry.submit", "数据填报", "提交填报", "向已发布填报表单新增或批量导入数据", 346),
    DATA_ENTRY_DELETE("dataentry.delete", "数据填报", "删除填报数据", "按模型业务主键批量删除目标物理数据", 348),
    TASK_VIEW("task.view", "任务管理", "查看任务", "查询任务、定义和运行记录", 350),
    TASK_CREATE("task.create", "任务管理", "新增任务", "新增任务", 360),
    TASK_UPDATE("task.update", "任务管理", "修改任务", "修改任务基本信息和草稿定义", 370),
    TASK_DELETE("task.delete", "任务管理", "删除任务", "删除未发布或已停用任务", 380),
    TASK_PUBLISH("task.publish", "任务管理", "发布任务", "校验、发布、停用和启用任务", 390),
    TASK_EXECUTE("task.execute", "任务管理", "执行任务", "手动运行和取消已发布任务", 395),
    SERVICE_ENGINE_VIEW("service.engine.view", "服务引擎", "查看服务引擎", "查询服务引擎定义", 400),
    SERVICE_ENGINE_CREATE("service.engine.create", "服务引擎", "新增服务引擎", "新增服务引擎定义", 410),
    SERVICE_ENGINE_UPDATE("service.engine.update", "服务引擎", "修改服务引擎", "修改服务引擎定义", 420),
    SERVICE_ENGINE_DELETE("service.engine.delete", "服务引擎", "删除服务引擎", "删除服务引擎定义", 430),
    SERVICE_ENGINE_TEST("service.engine.test", "服务引擎", "测试服务引擎", "测试服务引擎连通性和能力", 440),
    COMPUTE_ENGINE_VIEW("compute.engine.view", "计算引擎", "查看计算引擎", "查询计算引擎和 Dispatcher 状态", 450),
    COMPUTE_ENGINE_CREATE("compute.engine.create", "计算引擎", "新增计算引擎", "新增计算引擎定义", 460),
    COMPUTE_ENGINE_UPDATE("compute.engine.update", "计算引擎", "修改计算引擎", "修改未激活的计算引擎定义", 470),
    COMPUTE_ENGINE_DELETE("compute.engine.delete", "计算引擎", "删除计算引擎", "删除未使用的计算引擎", 480),
    COMPUTE_ENGINE_TEST("compute.engine.test", "计算引擎", "测试计算引擎", "测试 Dispatcher 连通性和能力", 490),
    COMPUTE_ENGINE_MANAGE("compute.engine.manage", "计算引擎", "管理计算引擎", "注册、Drain 和反注册计算引擎", 500),
    SERVICE_VIEW("service.view", "数据服务", "查看数据服务", "查询数据服务及部署状态", 510),
    SERVICE_CREATE("service.create", "数据服务", "新增数据服务", "新增标准数据服务", 520),
    SERVICE_UPDATE("service.update", "数据服务", "修改数据服务", "修改未发布或已下线的数据服务", 530),
    SERVICE_DELETE("service.delete", "数据服务", "删除数据服务", "删除已下线的数据服务", 540),
    SERVICE_PUBLISH("service.publish", "数据服务", "发布数据服务", "发布和下线数据服务", 550);

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
