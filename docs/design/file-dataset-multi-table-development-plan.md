# 文件数据集多表模型

## 1. 统一模型

文件数据集将物理文件和逻辑表分离：

```text
FileDataset
  ├─ FileDatasetFile
  └─ FileDatasetTable
       ├─ FileDatasetField
       └─ FileDatasetTableSource
```

单表格式每上传一个文件创建一张逻辑表；Excel 的一个 Sheet 创建一张逻辑表；GDB 的一个图层创建
一张逻辑表。逻辑表名称和 code 独立于物理文件，后续表级覆盖不会改变表 ID。

## 2. 类型语义

### 单表格式

CSV、TSV、TXT、JSON、JSONL、Parquet 和 Avro 的表名、初始来源名取文件基础名，来源键固定为
文件级键。数据集允许上传多个文件，每个文件创建新表。

SHP 归档也表示一张表，但必须先经过准备任务发布组件集合。

### Excel

Excel 数据集只允许一个物理文件。上传时发现 Sheet 并创建表；`sourceKey` 保存 Sheet 名，解析器
只读取对应 Sheet。工作簿内所有 Sheet 共用数据集解析参数。

### GDB

GDB 数据集只允许一个物理归档。准备成功后按图层创建表，图层共享原归档和物化 GDB 目录。

## 3. 当前来源

`FileDatasetTableSource` 只保存已经校验生效的数据。单表格式和 SHP 支持多个有序来源，运行时
按顺序 `UNION ALL`。Excel/GDB 当前每张逻辑表只有整文件产生的一个来源，不支持表级更新。

来源只保存表 ID、文件 ID、来源名、来源键、顺序、行数、Schema 指纹、元数据和生效时间。
临时装载操作由 `FileDatasetParseJob` 表达。

## 4. 状态与参数

逻辑表状态为 `QUEUED/PARSING/READY/SCHEMA_READY`。初始校验失败时删除空表。已有表进行追加或
覆盖时保持可用，`currentLoadJobId` 表示当前后台装载。

数据集存在文件、表或非终态 Job 后锁定解析参数。文件全部清空后可以重新配置。

## 5. API

数据集级上传创建新表；表级 API 负责追加与覆盖。Excel/GDB 只保留整文件替换。所有后台装载
通过上传响应中的 `jobIds` 或 `jobId` 跟踪，不提供手工解析或来源重试接口。

表查询、Schema、预览和当前来源接口都使用稳定 `tableId`。预览和 Canvas 只读取来源表中当前
存在的有序来源。

## 6. 破坏性升级

旧的单文件绑定表模型不迁移。升级时清空文件数据集对象前缀并重建相关表，旧 Canvas 节点重新
选择逻辑表。
