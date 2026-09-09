# 全景影像管理 V1

状态：已实现。适用于当前单 Admin 部署；真实 DJI 成品、S3 和浏览器 WebGL 的联合验收需在实际部署环境执行。

## 产品与输入范围

全景影像是企业或厅局全域数据管理平台的一类独立来源资源。入口为“数据管理 / 全景影像”，支持目录组织、成品上传、列表和地图检索、360°浏览、资料修订、原图替换，以及统一资产登记和发布。

只接收已拼接的完整球形 JPEG（`.jpg` / `.jpeg`），用户声明内容为 360°×180°。宽高必须为 2:1，单张不超过 100 MiB（104857600 字节），宽度不超过 32768 像素。结构检查不能证明真实拍摄覆盖范围。无 GPano 声明的 JPEG 可以接收；明确的其他投影、裁剪范围或解码损坏会阻止成品生效。

原图保存原始字节和 SHA-256。浏览图最大 4096×2048，缩略图最大 512×256，不放大小尺寸原图。使用文件型 ImageIO 输入和源降采样，使解码结果宽度最多为 4096；随后 Java2D 输出固定上限的 JPEG。临时目录按次创建和清理，不修改全局 ImageIO 缓存设置。

V1 不支持拼接、裁剪或柱面全景、PNG/TIFF、热点导览、历史版本回滚、多分辨率切片或 AI 分析。不同日期照片分别建立资源；没有新增拍摄点或项目实体。

## 结构与持久化

- 后端位于 Business `panorama`，复用现有 `FileObjectStorage` 基础存取和私有 S3 配置；每个文件包保存到独立 `panoramas/<UUID>/` 前缀。
- 前端位于 `modules/panorama`，继续使用单一 React 应用。Pannellum 2.5.6 随构建产物交付，metadata-extractor 2.19.0 负责元数据读取。
- 不使用文件数据集逻辑表或解析队列，不接入 Canvas、Dispatcher、Service Engine。
- `ds_panorama` 保存名称、描述、目录、有效时间/位置、AUTO/MANUAL 模式、当前/候选 UUID、内容版本、处理状态及安全错误。
- `ds_panorama_content` 保存原文件名、大小、尺寸、SHA-256、上传请求 UUID、元数据及内部文件前缀和清理计划。
- 两表继承 `BaseEntity`，使用 UUID 标量引用、物理删除、无 `@Version`。内部 UUID、文件前缀、清理字段与元数据 JSON 使用 `SearchExcluded`，不暴露存储地址。
- 新表由 `ddl-auto=update` 建立。现有目录作用域约束随枚举同步；资产类型 CHECK 约束按工程已有启动兼容方式加入 PANORAMA，避免旧 PostgreSQL 库拒绝登记新类型。

## 元数据语义

拍摄时间优先 EXIF `DateTimeOriginal`，回退 XMP EXIF `DateTimeOriginal` / XMP `CreateDate`。本地时间和偏移分别保存；缺少时区不推测 UTC，不以上传时间补齐。前端按照片本地时间显示，缺少偏移时标注“时区未知”。

位置优先使用 EXIF GPS，回退 XMP EXIF GPS 或 DJI XMP 经纬度，按 WGS84 管理；明确的非 WGS84 坐标不自动采用。允许人工补录。EXIF 高度（含海平面上下符号）、DJI 绝对高度、DJI 相对高度独立保留；飞行器偏航角与全景方向声明独立展示，不用飞行器角度推测正北。

时间和位置分别采用 AUTO/MANUAL。AUTO 在成品切换后采用新图提取值；MANUAL 保留修订内容，空值表示明确清空。编辑表单可以恢复文件提取值。设备、投影、高度、方向为只读。缺失可选信息不会阻止图片浏览，详情提示补录。

## 上传、替换和回收

浏览器每个文件独立提交，最多 2 个并发；上传失败不回滚其他文件。上传和状态使用真实阶段，不伪造处理进度百分比。上传超时采用单请求 10 分钟，不改变共享 HTTP 默认值。

原图先在本地流式检查并计算摘要；在外部 S3 写入前用短事务记录文件包和清理地址，防止部分上传失去回收依据。原图写入后短事务创建资源或接入候选，状态为 QUEUED。接收中的临时文件包在进程内排除清理；进程异常后，未关联的文件包在一小时保护期结束后回收。正常失败立即进入清理调度。

`PanoramaWorker` 使用单独的单线程执行器，默认每秒调度一次。领取时锁定资源并转为 PROCESSING；解码、元数据提取及 S3 写入在事务外完成；提交时再次校验候选 UUID 和状态。应用就绪时把上次 PROCESSING 恢复为 QUEUED。

当前成品首次发布版本为 1，成功替换递增。新候选处理失败保留原图以便重试，旧成品和当前版本不变。已有排队或处理任务时再次替换返回 409；失败候选可以重试、重新替换或放弃，首次失败没有当前成品时不能放弃，只能重试、重新替换或删除。PROCESSING 拒绝删除，QUEUED 可原子删除。

创建请求 UUID 同时保存在资源上，原图替换和旧文件回收后仍能识别该创建请求。候选文件的请求 UUID 用于接收/提交重试去重；同一请求尚在接收或清理时返回明确冲突，稍后重试。SHA-256 不用于不同资源去重。资源物理删除后不保留请求历史。

切换、放弃或删除把无引用文件包标为待清理；正常退休提供 60 秒缓冲，后台按前缀删除。清理失败保留位置和记录，60 秒后重试，不影响当前成品。数据库记录只在对象删除成功后移除。V1 不能多 Admin 并行领取任务，也不保留历史回滚入口。

## HTTP 与权限

路径统一为 `/api/v1/panoramas`；媒体均先执行 Admin JWT/RBAC 鉴权，不在 URL 中传 Token、不返回 S3 地址。媒体响应禁止缓存。图片 Blob 和查看器在页面离开、内容切换时释放，请求可取消。地图和查看器仅在需要时加载。

| 方法与相对路径 | 请求 / 行为 | 权限 |
| --- | --- | --- |
| GET 空路径 | SearchRequest；可选 `directoryIds`（逗号分隔 UUID）、`uncategorized`；PageResponse | panorama.view |
| POST 空路径 | multipart `file`、`clientRequestId`、可选 `directoryId`；201 | panorama.create |
| GET `/{id}` | 当前与候选的详情 | panorama.view |
| POST `/{id}/actions/update` | name、description、directoryId、expectedContentVersion、timeMode、captureTime、captureOffset、locationMode、latitude、longitude | panorama.update |
| POST `/{id}/actions/replace` | multipart file、clientRequestId、expectedContentVersion；202 | panorama.update |
| POST `/{id}/actions/retry-processing` | `{candidateId}`；202 | panorama.update |
| POST `/{id}/actions/discard-replacement` | `{candidateId}` | panorama.update |
| POST `/{id}/actions/delete` | 204 | panorama.delete |
| GET `/{id}/thumbnail`、`/{id}/preview`、`/{id}/content` | 必填 `version`；缩略图、浏览图、原图流 | panorama.view |
| GET `/map-points` | SearchRequest + 目录条件 + west/south/east/north；最多 500 点、匹配总数及 truncated | panorama.view |
| GET `/map-config` | `{url, attribution, maxZoom}` | panorama.view |

修改和替换检查预期内容版本；旧媒体版本返回 `PANORAMA_CONTENT_CHANGED`，前端刷新资源再读取。目录条件与 Search DSL 始终 AND。地图固定只查询存在当前成品且坐标有效的资源，视口支持跨越日期变更线；无位置资源保留在列表。

## 地图设置与页面

系统配置声明 `panorama.map`，类型为 STRING，默认值 `{"url":"","attribution":"","maxZoom":18}`。在系统配置页用专用 Drawer 编辑 XYZ 地址、纯文本署名和 0～22 的缩放上限，保存时原子提交受控 JSON。其他字符串配置的非空规则不变。

只支持 HTTP(S)、标准 Web Mercator XYZ 地址，必须包含 `{z}`、`{x}`、`{y}`，不允许 URL 用户信息或 fragment。URL 空时不请求底图；没有默认公网服务。署名按纯文本展示。底图失败不移除拍摄点；不实现 WMS/TMS 或 GCJ/BD 偏移转换。

配置读取即时生效，重新进入地图时重新读取；同浏览器保存会使地图配置缓存失效。普通筛选显式查询，目录导航只采用已应用条件。地图视口查询最多加载 500 点，显示匹配总数与加载数；聚合只涵盖这些已加载点。列表、上传 Drawer 和详情只在相关页面可见且仍有处理中内容时轮询。

Pannellum 支持拖动、缩放、全屏、复位，关闭自动旋转且不提供陀螺仪控制。WebGL 或加载失败保留错误和重试入口，基本信息及原图下载仍可使用。

## 统一资产

`AssetType.PANORAMA` 的登记条件是有可用当前成品；替换正在进行或失败且旧图可用时仍满足条件。资产模块单向读取全景 Service。

来源快照只保存内容版本、尺寸、大小、拍摄本地时间/偏移、设备摘要和是否有位置。门户不返回精确坐标、原始 EXIF/XMP、图片地址或文件引用。匿名可发现已发布介绍，使用资产须登录并通过 `panorama.view` 后导航至 `/panorama/{id}`。来源删除保持现有 SOURCE_MISSING 与缓存快照规则，不联动删除资产。替换变化通过既有检查/同步识别，不自动登记、发布或覆盖治理字段。

## 验证与运维

针对性测试覆盖无元数据 JPEG、衍生尺寸与原始字节不变、损坏和错误比例、XMP 时间/高度语义、裁剪投影拒绝、设置校验，以及失败保旧、成功回收、人工修订保留、创建请求去重、重启恢复、过期媒体版本和清理重试。生命周期测试使用内存对象存储与模拟事务边界，不证明真实 PostgreSQL/S3 并发行为。

实际环境仍应使用标准 DJI 成品核对下载摘要、100 MiB / 32768 像素边界、权限、地图超过 500 点、未配置底图、WebGL 错误与连续打开/关闭资源释放。根工程当前不强制测试、构建和联调政策不变。需要本地联调时采用 `local` Profile、根本地配置和 `start-local-dev.sh`。

未配置 S3 时应用仍可启动；存储操作返回 `PANORAMA_STORAGE_UNAVAILABLE`。启动只插入缺失的权限和地图配置，不覆盖角色授权或已保存值，不迁移文件数据集。部署应保证临时目录空间、私有对象存储可用，并运行单个 Admin 图片 Worker。
