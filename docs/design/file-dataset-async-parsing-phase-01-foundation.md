# 文件数据集异步解析阶段一：当前数据模型

## 目标

阶段一建立“文件数据集 → 文件/逻辑表 → 权威字段/当前来源”模型及统一解析 Job。业务数据不使用
修订号，不保存旧来源或失败文件。

## 核心实体

- `FileDataset`：保存类型和共享解析参数；存在文件、表或非终态 Job 时锁定参数。
- `FileDatasetFile`：状态只有 `PREPARING/READY`，保存对象 Key 和可选物化信息。
- `FileDatasetTable`：状态只有 `QUEUED/PARSING/READY/SCHEMA_READY`，使用
  `currentLoadJobId` 限制单表并发。
- `FileDatasetTableSource`：只保存已经生效的当前来源、顺序、行数、Schema 指纹和元数据。
- `FileDatasetParseJob`：保存准备或校验执行历史、当前装载方式、目标来源和名称快照。

任务状态为 `QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED`。领取、重试、租约和历史保留属于执行
可靠性，不代表业务数据历史。

## 持久化边界

实体继续继承 `BaseEntity`，使用 UUID 标量引用，不增加 JPA 关联或数据库外键。第一版由
Hibernate `ddl-auto=update` 创建新结构；已有文件数据集采用破坏性重建，不做字段迁移。

部署和重建步骤见
[文件数据表当前来源模型重建](../operations/file-dataset-table-source-rebuild.md)。
