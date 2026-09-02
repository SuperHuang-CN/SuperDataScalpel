# ArcGIS REST 与 OGC WFS 数据源

## 目标

ArcGIS REST 和 OGC WFS 虽然都基于 HTTP，却有稳定的服务发现、要素 Schema、Geometry 与 CRS 语义；因此作为独立数据源类型，而不是要求使用者手工配置通用 HTTP API 的 JSON 指针。

## 管理端

- `ARCGIS_REST`、`WFS` 都复用 HTTP API 的 Base URL、超时、默认 Header 和加密凭据槽位，只允许 `SOURCE` 用途。
- 建立连接时可使用无鉴权、Basic、固定 Bearer Token、Header/Query API Key。连接测试返回详细且脱敏的诊断；管理端当前明确拒绝 OAuth2 Client Credentials 和 Token Endpoint，避免看似成功但运行期不可用。
- “空间资源”登记的是 ArcGIS layer/table 或 WFS FeatureType，而不是任意 HTTP 请求。`ds_spatial_feature_resource` 保存资源编码、远端标识和不可变 Schema 快照，JSON 定义包含 Geometry 字段、Geometry 类型、EPSG、ArcGIS ObjectId/WFS 版本及属性字段。
- 发现 API：`GET /api/v1/data-sources/{dataSourceId}/spatial-resources/catalog`。ArcGIS 可按 folder/service 逐层浏览；WFS 从 GetCapabilities 获取 FeatureType。登记、修改、刷新、删除均遵守资源 Action API；属性预览使用 `POST .../actions/query-preview`，不返回 Geometry 值。
- 删除数据源前会检查已登记的空间资源，避免留下悬挂引用。

## Canvas 与运行期

`SPATIAL_SERVICE_INPUT` 是批处理输入节点，引用一个数据源 ID 和有序空间资源选择数组；每项保存资源 ID 与输出表名。任务准备把无凭据的全部资源 Schema 与加密解密后的 HTTP 运行连接一并写入 Manifest v22；Runner 按数组顺序使用 ArcGIS Query 或 WFS GetFeature 拉取 GeoJSON 分页结果，并用 Sedona 转换 Geometry。任一资源无效时节点整体失败，不向下游传播部分表。Geometry 必须是 EPSG、XY 的明确类型，后续空间处理节点可直接消费。

第一版边界：WFS 与 ArcGIS Runner 要求远端支持 GeoJSON 输出；WFS 1.x 在不支持可靠 offset 分页时只读取首批；空间服务运行期目前支持无鉴权、Basic、固定 Bearer 与 Header/Query API Key。对需要动态 Token 的服务，应先通过通用 HTTP API 接入或扩展空间运行期的 Token 机制。
