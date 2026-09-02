# GeoServer 空间服务发布 V1

## 定位

GeoServer 与 DataScalpel Service Engine 是控制面管理的同级运行时。普通单表、SQL 和脚本服务部署到 DataScalpel Engine；空间服务由 Admin 直接调用 GeoServer REST API，不经过 Service Engine 或 API Gateway。

## Engine 与数据源

- `ServiceEngine.type` 固定为 `DATASCALPEL` 或 `GEOSERVER`，类型和 Code 创建后不可修改。
- GeoServer 保存规范化管理地址、运行地址、加密管理凭据和专属 Workspace；Workspace 默认 `datascalpel`。
- 注册 GeoServer 时探测版本、PostGIS DataStore 扩展、WMS/WFS GetCapabilities，创建或复用 Workspace，并把 Workspace 级 WFS 设为 `BASIC`。
- GeoServer 只接受已启用且具有存储用途的 PostgreSQL/PostGIS 数据源。DataStore 名固定为 `ds_<dataSourceId 去横线>`，重复同步覆盖连接参数。
- 删除本地 Engine 不删除远端 Workspace；解除数据源注册只定向删除对应 DataStore，并先检查已启用空间服务引用。

## 空间服务

`SPATIAL_SERVICE` 只保存一个模型引用。可发布模型必须已发布、物理表匹配、数据源已在目标 GeoServer 同步就绪，并且恰好具有一个 XY Geometry 字段、明确 EPSG CRS 和一个非 Geometry 主键字段。缺少 GiST/SP-GiST 空间索引只产生警告，不阻止发布。

启用时生成不可变空间快照，资源名称固定为：

- Workspace：Engine 配置值。
- DataStore：`ds_<dataSourceId 去横线>`。
- FeatureType/Layer：`svc_<dataServiceCode>`。
- 默认样式：按 Point、Line、Polygon 或 Generic 选择 GeoServer 内置样式。

Admin 幂等创建或更新 FeatureType 和 Layer，并重新计算 native/lat-lon bounds。停用时只删除 Layer/FeatureType，不删除共享 DataStore 或 Workspace。空间服务运行信息直接给出 Qualified Layer Name、WMS/WFS 端点、GetCapabilities、WFS GeoJSON 示例和 WMS Reflect 预览入口。

## V1 边界

第一版固定为 PostGIS 物理表、只读 WMS + WFS、一个 Geometry 字段、一个主键字段和内置样式。不支持 WFS-T、WMTS、栅格、SQL View、SLD 管理、网关代理、GeoServer 集群和自动配置漂移扫描。
