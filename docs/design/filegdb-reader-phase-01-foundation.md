# 纯 Java FileGDB Reader 阶段一：格式基础与最小纵向读取

## 1. 文档定位

本文记录独立 Maven 模块 `data-scalpel-filegdb` 的第一阶段格式基础。后续扩展见 [FileGDB Reader 本地与 S3 双来源设计](filegdb-reader-local-s3-sources.md)和 [FileGDB MultiPoint 与 Polyline 游标读取](filegdb-reader-multipoint-polyline.md)。模块只负责在 JVM 中只读解析 File Geodatabase，不接入现有文件数据集、对象存储业务、REST API、任务队列或前端。

阶段一不是完整 FileGDB 兼容性承诺，而是建立可审计的二进制读取内核，并完成一条可以与权威工具逐字段比对的最小纵向链路。阶段完成后，调用方可以打开一个已解包、未使用 FileGDB 表压缩的 `.gdb` 目录，列出普通表和矢量要素类，读取字段 Schema，并受限地读取标量属性以及 Point、Polygon 几何。

## 2. 已确认的架构决定

- 新增根工程 Maven 模块 `data-scalpel-filegdb`，使用 Java 21。
- 模块运行时只依赖 JDK，不依赖 DataScalpel 其他模块。
- 不引入 Spring、Spark、Scala、Hadoop、GDAL、JNI、JNA 或 Esri 原生 SDK。
- 使用位置无关的来源 SPI 和显式小端二进制读取实现随机访问；本地来源使用 `Path`、`FileChannel`，不使用内存映射。
- 模块只读，不创建、更新、压缩、修复或删除 GDB 内容。
- 输入是已经解包的单个 `.gdb`；核心模块支持本地目录和随机访问来源 SPI，ZIP 是传输包装，本阶段不处理。
- 第一阶段不做坐标转换。坐标值保持源空间参考，Schema 暴露原始 WKT 和 Z/M 信息。
- 公开 API 返回模块自有的 Java record、枚举和游标，不返回 Spark `Row`，也不直接生成 GeoJSON。
- `/Users/huangchao/Downloads/FileGDB-master` 仅作为 Apache-2.0 参考实现和比对输入；不直接复制 Scala/Spark API。

## 3. 第一阶段交付范围

### 3.1 必须交付

1. 验证输入目录具有 FileGDB 基本结构，定位系统目录表及其 `.gdbtable`、`.gdbtablx` 文件。
2. 读取 `.gdbtablx` 头和记录偏移，识别删除记录的零偏移槽位。
3. 读取 `.gdbtable` 头、字段描述、空值位图和变长记录。
4. 从系统目录解析逻辑表名、物理表编号和可用表清单，区分普通表与矢量要素类；系统内部表默认不作为业务图层返回。
5. 暴露稳定的只读 API：打开数据库、列出图层、取得 Schema、打开受限游标和关闭资源。
6. 支持第一阶段标量字段：`INT16`、`INT32`、`FLOAT32`、`FLOAT64`、`STRING`、`TIMESTAMP`、`OID`、`UUID/GUID`、`BINARY`、`XML`。
7. 支持 Point 与 Polygon 的 XY、XYZ、XYM、XYZM 读取，保留空间参考 WKT、范围、原点和比例尺元数据。
8. 使用至少两套来源明确的夹具完成目录、Schema、标量值和几何值的权威结果比对。
9. 对截断、越界、异常长度和不支持格式返回稳定异常，不静默生成全空记录。

### 3.2 第一阶段明确不做

- `.gdb.zip` 识别、安全解包和 ZIP bomb 防护；
- FileGDB 内部压缩表、加密内容或损坏文件修复；
- MultiPoint、Polyline、Multipatch 和 Raster；
- 附件、关系类、拓扑、网络、域、子类型和空间索引查询；
- 属性过滤、投影裁剪、空间查询和并行扫描；
- 坐标系识别服务和坐标转换；
- GeoJSON、WKB、WKT 序列化；
- 通用 HTTP、InputStream 或任意远程文件系统输入；S3 已由独立可选适配器按 Range 对象读取支持；
- 与 `data-scalpel-business`、Admin、Service Engine 或前端集成；
- 命令行工具、Spring Bean 和自动配置。

## 4. 模块与包结构

计划中的代码结构如下，目录按实际实施阶段创建，不预先生成空类：

```text
data-scalpel-filegdb
├─ pom.xml
└─ src
   ├─ main/java/cn/superhuang/data/scalpel/filegdb
   │  ├─ FileGeodatabase.java
   │  ├─ FileGdbOpenOptions.java
   │  ├─ FileGdbReadLimits.java
   │  ├─ FileGdbSource.java
   │  ├─ FileGdbRandomAccessObject.java
   │  ├─ FileGdbSourceInfo.java
   │  ├─ FileGdbException.java
   │  ├─ model
   │  │  ├─ FileGdbLayer.java
   │  │  ├─ FileGdbLayerType.java
   │  │  ├─ FileGdbSchema.java
   │  │  ├─ FileGdbField.java
   │  │  ├─ FileGdbFieldType.java
   │  │  ├─ FileGdbFeature.java
   │  │  ├─ FileGdbSpatialReference.java
   │  │  └─ geometry
   │  │     ├─ FileGdbGeometry.java
   │  │     ├─ FileGdbPoint.java
   │  │     ├─ FileGdbPolygon.java
   │  │     └─ FileGdbCoordinateSequence.java
   │  └─ internal
   │     ├─ LittleEndianRandomAccessReader.java
   │     ├─ LocalFileGdbSource.java
   │     ├─ BoundedBufferReader.java
   │     ├─ GdbTableIndexReader.java
   │     ├─ GdbTableReader.java
   │     ├─ GdbTableHeaderReader.java
   │     ├─ GdbFieldDescriptorReader.java
   │     ├─ GdbRecordReader.java
   │     ├─ GdbCatalogReader.java
   │     └─ GdbGeometryReader.java
   └─ test/java/cn/superhuang/data/scalpel/filegdb
      ├─ FileGeodatabaseTest.java
      ├─ GdbTableIndexReaderTest.java
      ├─ GdbTableHeaderReaderTest.java
      ├─ GdbRecordReaderTest.java
      ├─ GdbGeometryReaderTest.java
      └─ FileGdbMalformedInputTest.java
```

`internal` 包不作为兼容 API。第一阶段只有根包和 `model` 包可供调用方使用；不能把文件偏移、页大小、位掩码或 Spark 参考实现的数据结构暴露到公开 API。

## 5. 公开 API 契约

### 5.1 打开与关闭

建议的最小调用方式：

```java
try (FileGeodatabase database = FileGeodatabase.open(gdbDirectory, options)) {
    List<FileGdbLayer> layers = database.layers();
    FileGdbSchema schema = database.schema(layers.getFirst().id());
    try (FileGdbFeatureCursor cursor = database.openCursor(
            layers.getFirst().id(), FileGdbReadOptions.limit(10))) {
        while (cursor.hasNext()) {
            FileGdbFeature feature = cursor.next();
        }
    }
}
```

API 规则：

- `open(Path)` 只接受存在、可读、规范化后仍为目录的本地路径；`open(FileGdbSource)` 接受随机访问来源并取得其所有权。符号链接选项只作用于本地来源，默认拒绝目录外逃逸。
- `layers()` 返回不可变快照，顺序稳定；默认排除系统表。
- 图层以模块生成的稳定 ID 或物理表编号定位，不能在读取过程中再次用可变展示名称解析。
- `schema` 是打开时读取或首次访问时读取的不可变结果。
- `openCursor` 必须指定正数上限且不得超过全局限制；第一阶段不提供“读取全部”捷径。
- `FileGdbFeature` 保存 OID、按 Schema 顺序排列的不可变属性 Map 和可空 Geometry。
- Cursor 与数据库均实现 `AutoCloseable`；关闭后所有读取操作稳定失败。
- 单个数据库对象第一阶段不承诺线程安全；多个独立实例可以并发读取同一不可变数据源。

### 5.2 Java 类型映射

| FileGDB 字段 | Java 输出 |
| --- | --- |
| INT16 | `Short` |
| INT32 / OID | `Integer` |
| FLOAT32 | `Float` |
| FLOAT64 | `Double` |
| STRING / XML | `String` |
| TIMESTAMP | `Instant` |
| UUID / GUID | `UUID`；非法编码作为格式错误，不退化为任意字符串 |
| BINARY | 只读 `byte[]` 副本 |
| SHAPE | `FileGdbGeometry` |

所有可空字段使用 Java `null` 表示缺失值。Schema 明确记录可空性；读取器不能根据抽样值重新推断类型。

### 5.3 几何模型

- Point 使用明确的 `x/y` 和可选 `z/m`；
- Polygon 使用 ring 边界与连续坐标序列，不在读取阶段判断或修改顺逆时针；
- 坐标序列内部可使用 primitive array，但公开构造必须防止调用方修改内部状态；
- Geometry 携带类型与 Z/M 维度，空间参考属于 Schema，不在每条 Feature 重复保存；
- 空几何返回 `null`，格式错误不能伪装成空几何；
- 第一阶段不执行环闭合修复、拓扑校验或坐标转换。

## 6. 二进制读取边界

### 6.1 文件访问

- 每个物理表只通过受控 `FileGdbRandomAccessObject` 读取对应 `.gdbtable` 和 `.gdbtablx`；本地来源底层使用独立只读 `FileChannel`；
- 所有整数、变长整数和浮点值显式按 FileGDB 小端规则读取；
- `LittleEndianRandomAccessReader` 必须实现 `readFully(position, target)`，短读直接报错；
- 不依赖共享可变 channel position，使一次记录读取的偏移计算可审计；
- 第一阶段不使用全局静态 Header 缓存。单个数据库实例允许使用有界实例缓存；关闭时释放随机访问对象和来源。

### 6.2 索引与记录

读取顺序固定为：

1. 解析 `.gdbtablx` 固定头，取得页面数、槽位数和每槽偏移宽度；
2. 按 4、5 或 6 字节无符号小端偏移读取记录位置；
3. 零偏移表示已删除或未使用槽位，跳过但保持 OID 计算一致；
4. 非零偏移必须落在 `.gdbtable` 文件范围内；
5. 在记录位置读取长度，长度必须满足全局限制且不能越过文件尾；
6. 读取空值位图，再严格按字段顺序消费记录内容；
7. 记录结束位置必须与声明长度一致；允许的尾部内容必须有格式依据和测试，不能无条件忽略。

所有加法、乘法和偏移换算使用 `Math.addExact`、`Math.multiplyExact` 或等价显式检查，防止恶意长度造成整数溢出。

### 6.3 字符串与时间

- 字段名、别名和 WKT 根据 FileGDB 元数据编码规则读取，不使用平台默认 Charset；
- 数据字符串按格式规定的变长长度读取并应用最大字符/字节限制；
- Timestamp 的源纪元、单位和 UTC 语义必须通过夹具比对确认后固化；确认前不以“看起来正确”的时间进入公开 API；
- XML 第一阶段作为普通字符串返回，不解析 DOM，避免实体扩展和额外内存风险。

## 7. 资源与安全限制

新增不可变 `FileGdbReadLimits`，默认值属于 API 契约并接受更严格覆盖：

| 限制 | 第一阶段默认值 | 目的 |
| --- | ---: | --- |
| 最大物理表文件大小 | 16 GiB | 拒绝明显超出第一阶段验证范围的输入 |
| 最大字段数 | 1,024 | 限制 Schema 和空值位图 |
| 最大单记录字节数 | 64 MiB | 防止按不可信长度分配大数组 |
| 最大字符串 UTF-8/UTF-16 字节数 | 16 MiB | 限制异常变长值 |
| 最大二进制字段字节数 | 64 MiB | 限制 BLOB |
| 最大 Polygon part/ring 数 | 100,000 | 限制嵌套结构 |
| 最大单 Geometry 坐标点数 | 10,000,000 | 限制坐标数组与循环 |
| 单 Cursor 最大索引槽位数 | 100,000,000 | 限制大量删除槽位导致的无界扫描 |
| 单 Cursor 最大 Feature 数 | 100,000 | 禁止无界扫描 API |

限制值在实现前通过真实夹具调整一次；变更默认值必须更新本文和边界测试。读取错误必须包含文件角色、物理表和 OID 等诊断上下文，但不返回整个记录内容或不可控绝对路径。

## 8. 异常模型

公开异常统一继承 `FileGdbException`，至少区分：

- `INVALID_DIRECTORY`：目录结构不是可识别的 FileGDB；
- `MISSING_FILE`：目录或表清单引用的物理文件缺失；
- `UNSUPPORTED_FORMAT`：压缩表、不支持的字段或几何类型；
- `MALFORMED_HEADER`：文件头、字段描述或系统目录损坏；
- `INVALID_OFFSET`：索引偏移或记录范围越界；
- `LIMIT_EXCEEDED`：字段、记录、字符串、二进制或几何超过限制；
- `TRUNCATED_INPUT`：实际文件短于声明结构；
- `IO_ERROR`：底层读取失败。

异常包含稳定错误码和安全消息，可选保留底层 cause。任何单条记录解析失败都终止当前游标；不允许像参考实现一样捕获 `Throwable` 后返回全空行继续执行。

## 9. 参考实现与许可证边界

`FileGDB-master` 使用 Apache License 2.0，可以作为实现参考，但开发时必须记录来源：

- 优先根据公开格式资料、受控十六进制样本和测试结果重新表达 Java 实现；
- 如果实质性翻译某段算法或结构，源文件保留明确来源注释，并在模块增加第三方通知；
- 不复制 Spark、Hadoop、Scala 适配层以及其公开 API；
- 不沿用全局无界可变缓存、吞异常返回空行和无界 `rows().toArray` 行为；
- 外部仓库中的测试 GDB 在纳入本工程前单独确认数据授权、最小化文件体积并登记预期结果；
- 阶段一结束前输出一份来源清单，列明参考文件、采用的算法和独立修改点。

## 10. 测试数据与权威比对

### 10.1 夹具原则

至少准备：

1. `scalar-and-point.gdb`：覆盖所有第一阶段标量类型、null、空字符串、非 ASCII 文本、Point XY/XYZ/XYM/XYZM；
2. `polygon.gdb`：覆盖单环、多环、多部件、空 Geometry、Z/M 和较大坐标序列；
3. 由测试代码生成的最小二进制片段：覆盖每种索引宽度、变长整数边界、空值位图和损坏输入，不依赖完整 GDB。

完整 GDB 夹具尽量小于 5 MiB。不得直接提交当前外部仓库约 190 MiB 的整个 `data` 目录。

### 10.2 权威结果

每套完整夹具保存人工审阅的预期清单，至少包含：

- 图层逻辑名称、类型和物理表编号；
- 字段名称、别名、类型、可空性和顺序；
- 总槽位数、有效记录数和选定 OID；
- 选定记录的标量值；
- Point 坐标、Polygon ring/part 边界和坐标；
- WKT、extent、XY/Z/M 原点与比例尺。

预期结果必须至少由 ArcGIS Pro 导出结果或 GDAL/OpenFileGDB 结果之一验证；关键 Geometry 再使用第二种实现抽查。GDAL 只用于开发期生成和核对预期值，不作为模块运行依赖或默认测试依赖。

### 10.3 测试分层

- 二进制单元测试：直接构造最小字节片段，覆盖小端值、4/5/6 字节偏移、变长整数和截断；
- 表级测试：读取真实 `.gdbtable/.gdbtablx`，验证 Schema、null 和指定 OID；
- 数据库级测试：打开完整目录，验证图层发现、Schema 和前 N 条记录；
- 恶意输入测试：负数或超大长度、溢出偏移、循环耗尽、坐标点数超限和短读；
- 资源测试：游标提前关闭、数据库关闭、重复关闭和解析异常后的 Channel 释放；
- 架构测试：Maven 依赖树中不存在 Spring、Spark、Scala、Hadoop、GDAL/JNI/JNA 和 DataScalpel 业务模块。

## 11. 实施步骤

### 步骤 1：模块基线与来源登记

- 固化模块 POM、Java 21 和 JUnit；
- 建立测试夹具授权与来源清单；
- 记录 FileGDB-master 参考文件与 Apache-2.0 义务；
- 添加依赖树守卫测试或构建检查。

完成标准：空模块可独立执行 `./mvnw -pl data-scalpel-filegdb test`，运行时依赖为零。

### 步骤 2：受限随机读取原语

- 实现位置无关的小端随机访问读取；
- 实现 4/5/6 字节无符号整数、变长有符号/无符号整数和边界检查；
- 实现带上限的 Buffer/字符串读取；
- 完成短读、EOF、溢出和限制测试。

完成标准：所有原语都有正常值、边界值和损坏值测试，不读取完整 FileGDB。

### 步骤 3：表索引与表头

- 解析 `.gdbtablx` Header 和槽位偏移；
- 解析 `.gdbtable` Header 与字段描述；
- 构造内部不可变 Schema；
- 验证物理文件边界、偏移和记录长度。

完成标准：可以对固定物理表输出与权威结果一致的 Schema 和有效 OID 列表。

### 步骤 4：系统目录与公开层模型

- 读取系统目录并建立逻辑名称到物理表编号映射；
- 识别普通表、矢量要素类和系统表；
- 实现 `FileGeodatabase.open/layers/schema/close`；
- 处理名称重复、缺失物理文件和不支持项目类型。

完成标准：完整夹具的公开图层清单、类型和 Schema 全部一致，系统表不泄漏到默认清单。

### 步骤 5：标量记录游标

- 解析空值位图和所有第一阶段标量类型；
- 实现 OID、删除槽位跳过、Feature 上限与 Cursor 生命周期；
- 时间与 UUID 语义必须通过权威夹具后才能完成；
- 单记录错误立即终止并带稳定错误码。

完成标准：标量夹具的指定记录逐字段一致，null、空值、非 ASCII 和二进制值覆盖完整。

### 步骤 6：Point 与 Polygon

- 读取 Geometry 元数据、空间参考 WKT、原点、比例尺与 extent；
- 实现 XY/XYZ/XYM/XYZM Point；
- 实现 Polygon 的 part/ring 边界和 delta 坐标恢复；
- 应用 part 数、点数、Buffer 和数值边界限制；
- 验证空 Geometry、负坐标、大坐标和多环多部件。

完成标准：选定 Geometry 与两种权威读取结果在原始坐标空间内一致；不做拓扑修复或坐标转换。

### 步骤 7：鲁棒性与阶段验收

- 完成异常分类、资源释放、不可变性和重复关闭测试；
- 用截断和变异夹具做系统化负向测试；
- 检查公开 API 未暴露 internal 数据结构；
- 生成来源清单和兼容性矩阵；
- 运行模块测试、完整后端构建和格式检查。

完成标准：满足第 12 节全部门槛，才能进入 MultiPoint/Polyline 等下一阶段。

## 12. 阶段验收清单

- [x] `data-scalpel-filegdb` 不依赖任何 DataScalpel 模块或运行时第三方库。
- [x] 在 x86_64 与 arm64 的 Java 21 环境使用同一个 Jar 运行模块测试。
- [x] 可以打开至少两套未压缩 FileGDB 夹具并稳定列出业务图层。
- [x] 系统目录、物理表编号、Schema、字段别名与可空性和权威结果一致。
- [x] 第一阶段全部标量类型及 null 值读取正确。
- [x] Point 和 Polygon 的 XY/XYZ/XYM/XYZM 读取正确。
- [x] 删除槽位不会生成伪 Feature，OID 保持正确。
- [x] 任何记录失败都返回明确异常，不产生全空或部分成功记录。
- [x] 所有不可信长度、偏移和计数在分配或循环前检查上限及溢出。
- [x] Cursor 和数据库在正常、提前关闭及异常路径均释放文件句柄。
- [x] 压缩表、Multipatch、Raster 和其他超范围内容返回 `UNSUPPORTED_FORMAT`。
- [x] 夹具来源、许可和权威预期已记录，仓库未引入未确认来源的大型数据。
- [x] `./mvnw -pl data-scalpel-filegdb test` 通过。
- [x] `./mvnw verify` 通过。
- [x] `git diff --check` 通过。

## 13. 第一版后续阶段

阶段一通过后，独立模块第一版按以下顺序继续：

1. 阶段二：MultiPoint、Polyline、完整 Z/M、更多真实 GDB 兼容性与性能基线；
2. 阶段三：受控 `.gdb.zip` 输入、解包限制和临时目录生命周期；
3. 阶段四：GeoJSON/WKB 等可选序列化适配、公开 API 稳定性和独立发布准备。

这些阶段仍只建设 `data-scalpel-filegdb`。是否接入 DataScalpel 文件数据集属于另一项需求，必须重新评估依赖方向、错误映射和异步解析生命周期，不在本计划中预留业务适配层。
