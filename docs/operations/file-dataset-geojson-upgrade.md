# GeoJSON 文件数据集升级

本次升级是非破坏性的：现有文件数据集、逻辑表和 Canvas 引用均不迁移、不删除。

1. 部署同时包含 Admin、Task Engine 和 Dispatcher 的新版本；它们统一使用 Manifest v26。
2. 在发布前排空或取消仍在运行的 v24 任务。新 Runner 会拒绝 v24 Manifest。
3. 若数据库对 `ds_file_dataset.type` 或 `ds_file_dataset_file.format` 已创建枚举 CHECK，执行
   [`file-dataset-geojson-enum-compatibility.sql`](../../data-scalpel-admin/src/main/resources/db/file-dataset-geojson-enum-compatibility.sql)。
   新环境仅依赖 Hibernate `ddl-auto=update` 时无需执行。
4. 发布后创建 `GEOJSON` 文件数据集，解析参数中的 EPSG 默认值为 `4326`。

GeoJSON 与普通 JSON 是不同的数据集类型：只接受 RFC 7946 `FeatureCollection`，坐标按配置的
EPSG 解释但不会转换；旧式 `crs` 成员不会改变该配置。
