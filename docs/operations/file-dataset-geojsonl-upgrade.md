# GEOJSONL 文件数据集升级

本次升级不迁移或删除现有文件数据集、逻辑表和 Canvas 引用。

1. 部署同时包含 Admin、Task Engine 和 Dispatcher 的版本；它们统一使用 Manifest v26。
2. 发布前排空或取消仍在运行的 v25 任务。新 Runner 会拒绝 v25 Manifest。
3. 若数据库已有 `ds_file_dataset.type` 或 `ds_file_dataset_file.format` 的枚举 CHECK，执行
   [`file-dataset-geojsonl-enum-compatibility.sql`](../../data-scalpel-admin/src/main/resources/db/file-dataset-geojsonl-enum-compatibility.sql)。
   新环境只使用 Hibernate `ddl-auto=update` 时无需执行。
4. 发布后可创建独立的 `GEOJSONL` 文件数据集，EPSG 默认值为 `4326`。

GEOJSONL 不等同于普通 JSONL 或 GeoJSON：每个非空物理行必须是一个完整 GeoJSON `Feature`。坐标按配置的
EPSG 解释而不转换；不接受 FeatureCollection、单独 Geometry 或 RS 分隔的 GeoJSON Text Sequence。
