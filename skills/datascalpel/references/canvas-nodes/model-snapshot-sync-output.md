# MODEL_SNAPSHOT_SYNC_OUTPUT · 模型完整快照同步

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

仅用于 BATCH、BOUNDED 小数据完整快照的模型同步输出配置。它与 JDBC 快照同步使用相同的映射、Cast、Key、比较、删除保护和单事务执行语义；目标限定为 PostgreSQL、HighGo、MySQL、openGauss 和人大金仓 上已发布的 MANAGED 模型。

## 模式与连线

- 模式：BATCH；类别：OUTPUT。
- 恰好一条入边，无出边；一条入边可包含多张逻辑表。
- 输入必须为 BOUNDED。

## 关键配置与行为

- 先按映射 Cast 成目标类型，再按 key 比较：新增 INSERT、值变化 UPDATE、相同不写、目标独有行按 deletePolicy。不是任意规模增量或 CDC。
- KEEP 保留目标独有行，不传删除阈值；DELETE 需 maxDeleteRows≥1、maxDeleteRatio 在 (0,1]，空来源受保护。删除影响必须先在方案中确认。
- 真实执行在目标锁与单事务中处理 DELETE→UPDATE→INSERT；规模上限来自运行配置，不写入 Canvas。设计预校验没有实际验证删除行数。
- keyColumns 是目标字段/模型 code 组合。元数据可空或组合不匹配已知主键/唯一约束时是 WARNING；保留并解释。真实运行仍要求源、目标 Key 非 NULL 且各自唯一，预校验不扫描验证这些事实。
- 本手册按当前 Operator 的数据库能力分支校对；若部署环境仍是旧版本或契约说明与实际能力冲突，先报告差异并获取完整契约，不强行提交不支持的配置。

配置定位（只列关键语义，完整字段读取实时契约）：

- `targetModelId`：目标模型 UUID 字符串；模型必须处于 PUBLISHED 状态且物理模式为 MANAGED，其已启用 JDBC 数据源必须具有 STORAGE 用途并使用 PostgreSQL、HighGo、MySQL、openGauss 和人大金仓。
- `keyColumns`：用于匹配来源和模型记录的 1 到 32 个有序目标模型字段 code，不得为空或重复；每项必须已映射，且不能是 Geometry、自增或生成字段。字段元数据允许 NULL 或组合不匹配模型主键时仅警告，但运行数据中的来源和目标 Key 都必须非 NULL 且各自唯一。Key 变化表现为旧行 DELETE 和新行 INSERT。
- `columnMappings`：来源字段到可写模型字段的显式映射；来源值先 Cast 到模型目标类型，再用于 Key 校验、精确比较和写入。未映射来源字段忽略，未映射模型字段不比较或更新，插入时依赖目标默认值或 nullable 约束。
- `deletePolicy`：模型物理表中存在而完整来源快照中不存在的记录处理策略；必须提供。KEEP 保留目标独有行，DELETE 仅在非空来源保护和两项阈值均通过后删除。

## 逻辑表 Map 与字段

无下游 Map；keyColumns 为目标键，columnMappings 的 source→target 先决定 Cast 与比较字段；必须覆盖键和必要目标字段。

## 最小配置示例

前提：orders 是小规模完整 BOUNDED 源快照，id 非空唯一；目标是 PostgreSQL、HighGo、MySQL、openGauss 和人大金仓 上已发布 MANAGED 模型；完整主键和物理表已核对。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "sourceTableName": "orders",
  "targetModelId": "22222222-2222-4222-8222-222222222222",
  "keyColumns": [
    "id"
  ],
  "columnMappings": [
    {
      "sourceColumnName": "id",
      "targetColumnName": "id"
    }
  ],
  "deletePolicy": {
    "action": "KEEP"
  }
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `MODEL_SNAPSHOT_SYNC_REQUIRES_MANAGED_MODEL` | 模型快照同步只能选择已发布 MANAGED 目标。 |
| `MODEL_SNAPSHOT_SYNC_DATABASE_NOT_SUPPORTED` | 报告目标平台限制。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
