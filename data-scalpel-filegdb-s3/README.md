# DataScalpel FileGDB S3 Adapter

`data-scalpel-filegdb-s3` 把 S3 中以对象前缀保存的已解包 `.gdb` 暴露为核心解析器的 `FileGdbSource`。它只使用同步 AWS SDK v2 `S3Client`，不依赖 AWS CRT、Netty 异步客户端、JNI/JNA 或平台分类器，因此同一组 Java 21 Jar 可用于 arm64 和 x86_64。

本模块是独立解析组件，不接入 DataScalpel Business、Admin、文件数据集、Spring Bean、对象存储配置或 REST API。

## 使用

调用方创建、配置、复用并关闭 `S3Client`。Endpoint、Region、凭证、代理、超时、重试以及 MinIO 的 path-style 设置都属于调用方职责：

```java
S3FileGdbLocation location = new S3FileGdbLocation(
        "bucket",
        "datasets/sample.gdb");

FileGdbSource source = S3FileGdbSource.create(
        s3Client,
        location,
        S3FileGdbOptions.defaults());

try (FileGeodatabase database = FileGeodatabase.open(
        source,
        FileGdbOpenOptions.defaults())) {
    for (FileGdbLayer layer : database.layers()) {
        FileGdbSchema schema = database.schema(layer.id());
    }
}
```

数据库关闭时会清空来源的元数据缓存和数据块 LRU，但不会关闭传入的 `S3Client`。`FileGeodatabase.open` 失败时也会关闭来源。

## 对象布局与读取方式

S3 中必须是已解包对象前缀，不支持 `.gdb.zip`：

```text
s3://bucket/datasets/sample.gdb/gdb
s3://bucket/datasets/sample.gdb/a00000001.gdbtable
s3://bucket/datasets/sample.gdb/a00000001.gdbtablx
...
```

- 不调用 `ListObjects`，核心解析器引用到哪个物理文件，就对哪个对象执行 `HeadObject`。
- 数据读取全部是精确的 `bytes=start-end` Range GET；不会发起无 Range 的完整对象 GET。
- 默认块大小为 1 MiB，可设置为 64 KiB～8 MiB 的 2 次幂。
- 每个来源共享一个访问顺序 LRU，默认上限 64 MiB；上限必须是块大小的整数倍。
- 不使用临时文件、磁盘缓存、内存映射、后台线程、异步预取或并发请求。
- 小于一个块的对象仍以带 Range 的单次 GET 读取；Range 恰好覆盖该小对象不代表适配器退化为无 Range 请求。

`FileGdbOpenOptions.limits().maxTableFileBytes()` 在对象第一次打开、发起数据 GET 之前生效。

## 一致性

一个 S3 `.gdb` 前缀在发布后必须视为不可变：

- `HeadObject` 返回 VersionId 时，后续 Range GET 固定读取该 VersionId；
- 没有 VersionId 时，保存 ETag 并在每个 Range GET 上发送 `If-Match`；
- Range 响应的长度、`Content-Range`、VersionId 和 ETag 都必须与已保存元数据一致；
- 412、VersionId 或 ETag 变化返回 `SOURCE_CHANGED`，失败数据不会进入缓存。

上述机制只能固定单个对象版本，不能为一个前缀下的多个独立对象建立原子快照。发布新数据应使用新前缀；如果必须保留同一 Key，则应启用 S3 版本控制，并避免分批覆盖一个正在读取的前缀。

## 权限

适配器不需要 `s3:ListBucket`。对固定前缀的最小权限是 `s3:GetObject`；S3 的 HEAD 与 GET 都由该权限覆盖：

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::bucket/datasets/sample.gdb/*"
  }]
}
```

读取特定对象版本时还需允许相应的版本读取。适配器不会在异常消息中输出凭证、签名 URL 或 S3 响应正文。

## 错误映射

| S3/读取情况 | `FileGdbErrorCode` |
| --- | --- |
| 404、`NoSuchKey` | `MISSING_FILE` |
| 412、ETag 或 VersionId 变化 | `SOURCE_CHANGED` |
| 416、短响应、错误的 `Content-Range` | `TRUNCATED_INPUT` |
| 对象超过核心文件限制 | `LIMIT_EXCEEDED` |
| 403、超时、连接失败、S3 5xx | `IO_ERROR` |

核心支持的 Point、MultiPoint、Polyline 和 Polygon 都可以通过同一套 Range 来源读取。压缩表、ZIP、Multipatch、Raster 和曲线段仍返回或属于 `UNSUPPORTED_FORMAT` 范围。

## 验证

确定性测试使用内存 S3 替身和 JDK 本地 HTTP 端点，验证完整夹具、本地/S3 结果一致、Range、缓存、错误映射及客户端生命周期：

```bash
./mvnw -pl data-scalpel-filegdb,data-scalpel-filegdb-s3 -am test
```

默认跳过的真实 AWS S3/MinIO 测试只读现有夹具，不创建或删除远程对象。凭证沿用 AWS SDK 默认凭证链：

```bash
./mvnw -pl data-scalpel-filegdb-s3 -am \
  -Dfilegdb.s3.bucket=bucket \
  -Dfilegdb.s3.prefix=datasets/sample.gdb \
  -Dfilegdb.s3.region=us-east-1 \
  -Dfilegdb.s3.endpoint=http://127.0.0.1:9000 \
  -Dfilegdb.s3.pathStyle=true \
  -Dtest=S3RemoteCompatibilityTest test
```

第三方许可证见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。
