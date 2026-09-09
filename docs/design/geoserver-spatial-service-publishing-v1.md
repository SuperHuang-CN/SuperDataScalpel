# GeoServer 空间服务发布与在线制图

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
- 服务专属样式：Workspace 级 `sty_svc_<dataServiceCode>`；未配置的点、线、面使用 DataScalpel 默认简单样式，通用 Geometry 暂时使用 GeoServer `generic`。

Admin 幂等创建或更新 Workspace Style、FeatureType 和 Layer，并重新计算 native/lat-lon bounds。停用时删除服务专属 Layer、FeatureType 和 Style，不删除共享 DataStore 或 Workspace；本地样式草稿继续保留。空间服务运行信息直接给出 Qualified Layer Name、WMS/WFS 端点、GetCapabilities、WFS GeoJSON 示例和 WMS Reflect 预览入口。

## 服务预览

空间服务详情页固定提供“在线制图”Tab，宽屏采用左侧 WMS 地图、右侧在线配图工作台，窄屏将配图工作台放入 Drawer。“服务定义”只描述模型、物理表、Geometry、EPSG、主键和发布协议，并展示样式来源、版本、同步状态及在线制图入口。只有本地部署状态为 `DEPLOYED` 且 GeoServer 中同时存在 FeatureType 和 Layer 时才展示交互地图；草稿、停用、部署失败、远端资源缺失或没有有效空间边界时展示明确原因，但这些状态仍可编辑并保存样式草稿。

管理页面通过 `GET /api/v1/data-services/{id}/spatial-preview` 读取已发布图层边界，并通过 `GET /api/v1/data-services/{id}/spatial-preview/map` 获取当前 EPSG:3857 视口的 WMS PNG。图片由 Admin 在服务端请求 GeoServer 运行地址，浏览器不需要配置 GeoServer CORS，也不会接触 GeoServer 管理凭据。WMS 图片请求不携带管理认证，用于验证空间服务的实际公开访问能力。

这两个接口只用于已登录的管理页面，不是通用 GeoServer 代理，不代理 WFS，也不改变业务调用方直连 GeoServer WMS/WFS 的运行方式。

## SLD 与在线制图 V4

DataScalpel 是空间服务样式的源数据，GeoServer 只保存部署副本。样式版本独立于数据服务定义版本和部署 Revision，状态固定为 `NOT_APPLIED / OUT_OF_SYNC / SYNCING / IN_SYNC / SYNC_FAILED`。保存草稿不改变线上 WMS；已部署服务可单独应用或重新应用样式，无需停用 WMS/WFS。应用期间产生新草稿时，仅记录本次快照版本已应用，最新草稿继续保持待应用状态。

管理接口为：

- `GET /api/v1/data-services/{id}/spatial-style`：读取当前模式、V4 样式文档、默认文档、字段能力、版本和同步状态。
- `POST /api/v1/data-services/{id}/actions/query-spatial-style-sld`：只读编译当前草稿，提交 `{ "styleDocument": ... }`，返回 `{ "sldText": "..." }`，仅需 `service.view` 权限。
- `POST /api/v1/data-services/{id}/actions/update-spatial-style`：保存并激活在线制图文档。
- `POST /api/v1/data-services/{id}/actions/upload-spatial-sld`：以 multipart 上传并保存 SLD 草稿。
- `POST /api/v1/data-services/{id}/actions/apply-spatial-style`：将当前草稿同步到已部署图层。
- `POST /api/v1/data-services/{id}/actions/query-spatial-style-field-profile`：按模型字段生成唯一值或数值分级统计。
- `POST /api/v1/data-services/{id}/actions/render-spatial-style-preview`：使用 JSON Renderer 或 multipart SLD 文件渲染未保存草稿。
- `GET /api/v1/data-services/{id}/spatial-preview/legend`：代理已应用样式的 GeoServer 图例。

在线制图采用可编辑 `StyleDocument schemaVersion=4`。Renderer 与 Symbol 都使用 `type` 判别联合，点、线、面不再共享包含大量空字段的符号对象。Renderer 支持：

- `SINGLE_SYMBOL`：点、线或面单一符号。
- `UNIQUE_VALUE`：字符串、布尔或数值单字段分类，默认 12 类、最多 50 类，支持空值规则和 `ElseFilter` 其他值规则。
- `CLASS_BREAKS`：数值字段等距、分位数或手工分级，自动分级为 3–9 级；断点物化进文档，正式应用时不重新查询数据。点支持分级颜色或 `6–24px` 默认范围的分级大小，线支持分级颜色或 `1–8px` 默认范围的分级宽度，面固定使用分级填色。

`PointSymbol` 支持圆、方、三角、星形、大小、填充与边框；`LineSymbol` 支持颜色、透明度、宽度和实线/虚线/点线；`PolygonSymbol` 支持填充以及独立的边界颜色、透明度、宽度和线型。默认值为：点 `#4F6BFF`、10px、0.85 填充及 1px 白色边框；线 `#4F6BFF`、0.9、2.5px；面 `#6F7DFF`、0.35 填充及 `#3F51C6`、1.5px 边框。内置色带固定为 `DATASCALPEL_12 / BLUE_PURPLE / BLUES / GREENS / YELLOW_RED`，色带和尺寸范围只用于批量物化每条规则的完整 Symbol，不是运行时依赖。

### 比例尺、标注与专业符号

文档整体和 Labeling 各自保存 `scaleRange`，字段为 `minScaleDenominator / maxScaleDenominator`，默认均为空（不限制）。非空分母必须是有限正数，最小值严格小于最大值。整体范围作用于全部绘制层，标注取两个范围交集；启用标注但交集为空时阻止保存。SLD 使用最小值包含、最大值不包含的 `MinScaleDenominator / MaxScaleDenominator`。界面以 `1:N` 展示，提供常用分母和“当前视图”填值。

标注保留一个非 Geometry、非 Binary 字段、固定 `SansSerif`、8–48px 字号、粗体、文字色和 0–5px Halo，新增：

- 前缀、后缀各最多 50 字符；数值字段可选 0–6 位小数，默认保留原值；null 和空字符串不标注。
- 点：上、下、左、右，默认上方；偏移默认 6px，范围 0–64px。
- 线：沿线或水平，默认沿线；默认重复，间隔 300px，范围 50–2000px。
- 面：内部放置，默认仅在面内可容纳时显示。
- 默认避免重叠，可以显式允许重叠。上传 SLD 不叠加在线标注。

标注独立编译到 `FeatureTypeStyle`，避免影响分类的 `ElseFilter`。文本通过 DOM 与受控格式表达式写入，不接受任意函数或脚本。

线的可选 `casing` 默认关闭；开启默认白色、透明度 1、单侧扩展 1px（允许 0.1–10px）。外层宽度为主线宽度加两倍扩展，固定实线。独立 `FeatureTypeStyle` 先绘制全部外层、再绘制全部主线，避免交叉路段被后绘制的外层遮挡。调整色带只改颜色，调整分级宽度只改主线宽度，保留外描边和其他属性。

面的可选 `pattern` 默认关闭，支持斜线、交叉线、点状。开启默认斜线、`#3F51C6`、透明度 0.8、平铺单元 12px（6–48px）；线状宽度默认 1px（0.5–4px），点径默认 2px（1–8px 且不超过单元）。依次绘制背景、`GraphicFill` 图案、边界；使用固定白名单 `shape://slash / shape://times / circle`，无外部图片或 URL。切换色带保留图案与边界。图例通过本地 SVG 示意，真实 GeoServer WMS 是最终渲染结果。

字段统计只允许使用服务绑定模型中的物理标识符。Admin 在短事务中读取快照，事务外使用现有 JDBC 方言和连接工厂访问 PostgreSQL，Statement 超时为 10 秒。唯一值按频次降序和值升序稳定排序；分位数使用 `percentile_cont`，重复断点合并并返回实际级数。统计结果不持久化，数据变化后由用户显式重新统计，以保持已发布地图稳定。

数值分级的 SLD Filter 固定为首级 `<= break1`、中间级 `> previous AND <= current`、末级 `> last`，避免区间重叠并覆盖发布后新增极值。所有字段名、值、名称和标签都通过 DOM 节点写入；数值规范化为 `BigDecimal.toPlainString()`，不接受 NaN、Infinity 或科学计数法。

上传仅接受 UTF-8 `.sld/.xml`，最大 512KB，根元素必须为 SLD 1.0 `StyledLayerDescriptor`。XML 解析禁用 DTD、外部实体和外部 Schema，拒绝 `ExternalGraphic`、`OnlineResource`及外部 URI，并要求存在与当前 Geometry 类型匹配的 Symbolizer。通过本地安全和结构校验后，GeoServer REST 继续完成最终语义校验。上传 SLD 可以使用安全的多 Rule、Filter 和 TextSymbolizer。

### V4 破坏性升级

在线制图和上传 SLD 分别保留最后一份草稿，当前 Mode 决定下一次正式应用内容。Admin 首次启动 V4 时，`SpatialStyleV4ResetConfiguration` 将历史 `SIMPLE`、V1/V2/V3 及无法解析的旧在线草稿重置为当前 Geometry 的 V4 默认文档，并清空旧简单样式 JSON。此次升级不转换旧 JSON，不提供恢复入口；部署升级前如需留档，应自行备份原数据库。

- 在线模式：增加样式版本；已有应用版本保留并标记 `OUT_OF_SYNC`，否则为 `NOT_APPLIED`。
- 上传 SLD 激活模式：只替换后台在线草稿，不改变上传原文、文件名、激活模式、版本、应用时间、错误或同步状态。
- 已有有效 V4 文档不重复重置；无法确定模型 Geometry 时记录提示并跳过，不猜测类型。Generic Geometry 保持仅上传的行为。
- 启动处理只改本地草稿，不调用 GeoServer、不自动应用。当前线上 Style 保留到用户应用 V4 草稿或重新启用服务。

V4 替换原 V3 启动处理，不并存两套逻辑；沿用 `style_document_json` 等现有列，不新增数据库字段。规范化文档内容相同时重复保存不增加样式版本。

## 草稿预览与工作台

已部署服务可以预览尚未保存的 Renderer 或上传 SLD。Admin 在内存中校验并编译 Renderer；上传 SLD 会再次经过安全校验，并且只在预览 DOM 中把 `NamedLayer/Name` 改为当前 Qualified Layer Name。随后通过 GeoServer 公开 WMS 地址提交 `GetMap + SLD_BODY`，返回 PNG。这个流程不创建临时 Style、不更新版本，也不修改同步状态；原始上传文本在保存和正式部署时保持不变。

前端 `src/modules/cartography` 是独立业务模块，只公开制图类型、受控工作台、点/线/面专属 Symbol 编辑器、固定色带、规则构造和本地图例，不导入 DataService API、服务 ID、权限点或 Engine。数值分级先选择 Geometry 支持的表达方式，再选择字段与分级方法；切换表达方式保留字段、断点、规则 ID 和自定义名称，只重新物化颜色、点大小或线宽。DataService 预览容器负责接口、权限、部署状态和响应适配。宽屏采用左地图右编辑器，窄屏编辑器进入 Drawer；字段、符号或视口变化仅标记待渲染，用户点击“渲染当前视图”才请求 WMS，新请求取消旧请求，失败时保留上一张成功图片。

在线编辑器包含“符号化 / 标注 / 比例尺”三个页签。外描边和图案按开关展开，分类规则与单一符号复用控件；标注未启用时禁止编辑标注比例尺。

地图明确选择“线上样式 / 当前草稿”：有更新权限默认草稿，仅查看权限固定线上。线上走已有 GetMap 与 GeoServer 图例；草稿始终使用当前文档，不根据脏状态或同步状态推断。上传草稿优先使用新文件，否则提交已保存上传原文；上传草稿不显示线上图例。切换不保存、不自动渲染、不修改样式版本。应用成功也只标记待渲染，由用户手动刷新。

当前视图比例尺依据实际 EPSG:3857 BBox 和图片尺寸计算，标准像元为 0.28mm，GetMap 显式使用 `scaleMethod=OGC`，不直接使用 MapLibre zoom。图片宽 256–1600、高 256–1200，等比缩放并调整 BBox 维持正方形像元；过于狭长或超出 Mercator 可表示范围时提示调整视图。超出草稿符号、标注范围分别提示，不把透明图片当成渲染失败。

成功图片始终标识其实际来源和请求比例尺；后续编辑、切换、应用、移动或缩放标记“待渲染”，保留上一张成功图片。取消和响应校验同时覆盖视口、模式与草稿版本，过期结果不得覆盖当前图片。未部署服务仅可编辑、本地查看图例和源码，不请求 WMS。

### SLD 源码只读查看

配图面板提供默认收起的“SLD 源码（只读）”区域，两种样式模式共用。源码使用 Monaco 的 XML 高亮、行号和搜索，支持复制与放大查看，不允许手工修改或反向生成可视化配置。

在线制图展开源码后，通过只读编译接口生成当前配置对应的 SLD；配置变化防抖 450ms 更新，取消或忽略过期请求，失败时显示紧凑错误和重试入口，不显示过期源码。接口按模型 Geometry 和字段校验 V4 文档，限制 256KB，复用正式部署的 `SpatialSldCompiler` 和图层命名，在只读事务中取得编译快照后于事务外编译。服务不必已部署，不调用 GeoServer，也不保存文档或改变版本、同步状态。

样式详情及保存响应增加可空 `uploadedSldText`，返回已有上传草稿的原文，不新增数据库列。新选择的 UTF-8 `.sld/.xml` 文件（最大 512KB）立即读取，不改写文本，并标记“待保存校验”；正式保存仍执行后端安全校验。重新打开页面显示已保存原文。Generic Geometry 未上传 SLD 时显示“使用 GeoServer 内置 generic 样式”，不读取或伪造内置样式源码。

源码表达当前草稿（可以尚未保存），不是 GeoServer 线上样式。查看、搜索、复制和放大不改变表单脏状态，不触发保存、应用或 WMS 渲染。DataService 模块负责 API 接入，制图工作台仅接收编译回调与上传原文 Props。

后端纯内核位于 `cn.superhuang.data.scalpel.business.cartography`，按 `model / validation / sld` 组织，只负责样式模型、默认样式与色带、等距断点、规范化校验、SLD 编译和上传 SLD 安全解析，不依赖 Spring、JPA、HTTP、Repository 或 DataScalpel 业务实体。GeoServer 调用、字段统计、权限、事务和样式生命周期仍在现有 Business service 中。

依赖方向固定为：

```text
DataScalpel 业务编排 → cartography 内核
cartography 内核      ↛ DataScalpel 业务实体
```

## 当前边界

空间发布仍固定为 PostGIS 物理表、只读 WMS + WFS、一个 Geometry 字段和一个主键字段。在线工作台不支持多字段 Renderer、任意 Filter、Jenks 自然断点、多比例尺规则组、点旋转、图标/SVG、方向箭头、自定义虚线、字体上传、高级标注避让、SLD 反向解析、样式历史、模板、撤销重做、自动重分类、Vector Tile 或 Mapbox Style 转换。Generic Geometry 继续只支持上传 SLD。服务预览不提供 WFS 要素点击、直方图、外部底图、自动预览或多图层编排；运行时不支持 WFS-T、WMTS、栅格、SQL View、公开网关代理、GeoServer 集群或自动配置漂移扫描。
