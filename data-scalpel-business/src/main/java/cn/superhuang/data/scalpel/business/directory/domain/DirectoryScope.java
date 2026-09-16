package cn.superhuang.data.scalpel.business.directory.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Identifies the business area whose directory tree is being managed. */
@Schema(description = "目录范围：DATA_SOURCE 数据源、FILE_DATASET 文件数据集、PANORAMA 全景影像、MODEL 模型、METRIC 指标、BUSINESS_OBJECT 业务建模、TASK 任务、DATA_SERVICE 数据服务、MCP_SERVER 原 MCP 服务、ASSET 资产门户业务领域。各范围使用独立目录树，目录仅用于分类，不构成权限或数据隔离边界")
public enum DirectoryScope {

    DATA_SOURCE,
    FILE_DATASET,
    PANORAMA,
    MODEL,
    METRIC,
    BUSINESS_OBJECT,
    TASK,
    DATA_SERVICE,
    MCP_SERVER,
    ASSET
}
