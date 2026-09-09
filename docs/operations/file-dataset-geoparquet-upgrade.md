# GeoParquet 文件数据集升级

本次升级不迁移或删除已有文件数据集、逻辑表或 Canvas 引用。

1. 部署同时包含 Admin、Task Engine 和 Dispatcher 的版本；它们统一使用 Manifest v26。
2. 发布前排空或取消仍在运行的 v25 任务。新 Runner 会拒绝 v25 Manifest。
3. 若数据库已有 `ds_file_dataset.type` 或 `ds_file_dataset_file.format` 的枚举 CHECK，执行
   [`file-dataset-geoparquet-enum-compatibility.sql`](../../data-scalpel-admin/src/main/resources/db/file-dataset-geoparquet-enum-compatibility.sql)。
   新环境只使用 Hibernate `ddl-auto=update` 时无需执行。
4. 发布后可创建独立的 `GEOPARQUET` 文件数据集，单次上传一个 `.parquet` 文件；上传 Spark 输出时请选择其中
   一个 `part-*.parquet`，不要上传整个输出目录。

GeoParquet 不接受外层压缩或手工 EPSG 覆盖。坐标参考、Geometry 列和几何类型来自文件 Footer；运行时仅设置
已验证的 SRID，不进行坐标转换。
