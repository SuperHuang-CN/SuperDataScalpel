# DataScalpel Shapefile S3 Adapter

`data-scalpel-shapefile-s3` 将 S3 中已解包的 `.shp/.shx/.dbf` 及可选 `.cpg/.prj` 暴露为核心解析器的 `ShapefileSource`。它使用同步 AWS SDK v2 `S3Client`、HeadObject 和条件 Range GET，不依赖 AWS CRT、Netty 异步客户端、JNI/JNA 或临时文件。

本模块是独立底层适配器，不接入 DataScalpel Business、Admin、文件数据集、Spring Bean、对象存储业务配置、REST API 或前端。

## 使用

调用方创建、配置、复用并关闭 `S3Client`：

```java
S3ShapefileLocation location = S3ShapefileLocation.fromShpKey(
        "gis-bucket",
        "datasets/roads/roads.shp");

ShapefileSource source = S3ShapefileSource.create(
        s3Client,
        location,
        S3ShapefileOptions.defaults());

try (ShapefileDataset dataset = ShapefileDataset.open(
        source,
        ShapefileOpenOptions.defaults())) {
    ShapefileSchema schema = dataset.schema();
    try (ShapefileFeatureCursor cursor = dataset.openCursor(
            ShapefileReadOptions.limit(1_000))) {
        while (cursor.hasNext()) {
            ShapefileFeature feature = cursor.next();
        }
    }
}
```

`ShapefileDataset.open` 成功或失败后都拥有并关闭 Source。关闭 Dataset/Source 不会关闭传入的 `S3Client`。Endpoint、Region、凭证、代理、超时、SDK 重试和 MinIO path-style 设置属于调用方职责。

## 对象 Key

`fromShpKey` 将最终 `.shp` 后缀替换为小写 `.shx/.dbf/.cpg/.prj`。S3 区分大小写，本模块不调用 `ListObjects`；不同大小写或非默认位置必须显式指定：

```java
S3ShapefileLocation location = S3ShapefileLocation
        .fromShpKey("bucket", "layers/Roads.SHP")
        .withComponentKey(ShapefileComponent.SHX, "layers/Roads.SHX")
        .withComponentKey(ShapefileComponent.CPG, null); // 不探测 CPG
```

SHP、SHX、DBF 必需；CPG、PRJ 可选。所有 Key 位于同一 bucket 且必须互不重复。适配器不处理 ZIP，不发现图层，不创建或修改对象。

## Range 与缓存

- 创建 Source 时先 Head 全部配置组件，冻结大小、VersionId 和 ETag。
- 数据读取全部使用 `bytes=start-end`，不发起无 Range 的完整对象 GET。
- 有 VersionId 时固定读取该版本；否则发送 `If-Match: ETag`。
- 响应体长度、Content-Length、Content-Range、VersionId 和 ETag 必须匹配快照。
- 默认 block 为 1 MiB，范围 64 KiB～8 MiB 且必须为 2 的幂。
- 一个 Source 的全部组件共享一个 LRU，默认总上限 64 MiB。
- 不使用磁盘缓存、内存映射、后台线程、异步预取或并发 Range。

Source 创建后任一对象发生变化，返回 `SOURCE_CHANGED` 并锁存整个 Source。S3 无法为五个对象提供原子快照；发布数据应使用不可变 Key/前缀，或由上层 manifest 固定全部组件版本。

## 权限

适配器不会调用 `ListObjects`。读取已知且存在的固定对象只需要 `s3:GetObject`；对象存在 VersionId 时，后续固定版本读取还需要 `s3:GetObjectVersion`。

AWS S3 对不存在对象的 HeadObject 有一项权限相关语义：具备 `s3:ListBucket` 时通常返回 404，不具备时可能返回 403。适配器只会把明确的 404/`NoSuchKey` 视为可选 `.cpg/.prj` 不存在，403 始终返回 `INVALID_SOURCE`，不会静默忽略访问错误。因此，在不授予 `s3:ListBucket` 的最小权限部署中，调用方应确保配置的可选对象确实存在，或者通过 `withComponentKey(CPG, null)` / `withComponentKey(PRJ, null)` 明确关闭对应探测。

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:GetObject", "s3:GetObjectVersion"],
    "Resource": "arn:aws:s3:::gis-bucket/datasets/roads/*"
  }]
}
```

## 错误映射

| 情况 | `ShapefileErrorCode` |
| --- | --- |
| 初始 Head 的必需对象 404 / `NoSuchKey` | `MISSING_COMPONENT` |
| 403 / `AccessDenied` | `INVALID_SOURCE` |
| 快照后的 404、412、416、VersionId/ETag/总长度变化 | `SOURCE_CHANGED` |
| 短响应 | `TRUNCATED_INPUT` |
| 无效 Content-Range、网络或 S3 5xx | `IO_ERROR` |
| 对象超过核心安全上限 | `LIMIT_EXCEEDED` |

## 验证

默认测试包括内存 S3 替身、SDK 请求捕获、本地/S3 逐值兼容性，以及 JDK 本地 HTTP Range 端点：

```bash
./mvnw -pl data-scalpel-shapefile,data-scalpel-shapefile-s3 -am test
```

真实 AWS S3/MinIO 测试默认跳过，只读已有组件，凭证使用 AWS SDK 默认凭证链：

```bash
./mvnw -pl data-scalpel-shapefile-s3 -am \
  -Dshapefile.s3.bucket=gis-bucket \
  -Dshapefile.s3.shpKey=datasets/roads/roads.shp \
  -Dshapefile.s3.region=us-east-1 \
  -Dshapefile.s3.endpoint=http://127.0.0.1:9000 \
  -Dshapefile.s3.pathStyle=true \
  -Dtest=S3RemoteCompatibilityTest test
```

架构与一致性说明见[本地与 S3 双来源设计](../docs/design/shapefile-reader-local-s3-sources.md)，第三方许可证见[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。
