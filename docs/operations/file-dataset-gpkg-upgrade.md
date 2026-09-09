# GeoPackage 文件数据集升级

本次升级不迁移或删除已有文件数据集、逻辑表或 Canvas 引用。

1. 部署同时包含 Admin、Task Engine 和 Dispatcher 的版本；它们统一使用 Manifest v27。
2. 发布前排空或取消仍在运行的 v26 任务。新 Runner 会拒绝 v26 Manifest。
3. 若数据库已有 `ds_file_dataset.type` 或 `ds_file_dataset_file.format` 的枚举 CHECK，执行
   [`file-dataset-gpkg-enum-compatibility.sql`](../../data-scalpel-admin/src/main/resources/db/file-dataset-gpkg-enum-compatibility.sql)。
   新环境只使用 Hibernate `ddl-auto=update` 时无需执行。
4. Admin 和 Task Engine Runner 都需要包含 `org.xerial:sqlite-jdbc`，并为上传、校验和运行留出受
   `data-scalpel.file-parsing.max-materialized-size` 限制的临时磁盘空间。

发布后可创建独立的 `GPKG` 文件数据集。每个数据集只接受一个 `.gpkg` 文件；后台以只读方式发现
`gpkg_contents` 中登记的 `features` 图层和 `attributes` 属性表，并让每张业务表独立进入校验队列。
空间 CRS 由每个图层的 GeoPackage 元数据识别，不提供手工 EPSG 覆盖，也不转换坐标。
