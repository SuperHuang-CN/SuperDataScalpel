# MASK_FIELDS · 字段脱敏

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

支持 BATCH 和 STREAMING 的无状态字段脱敏配置；对一个或多个不同上游表分别用节点内嵌 definition 原位替换选定字段。全局规则只是在设计时复制的模板，保存、发布、编译和运行均不查询或同步全局规则。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- INLINE 直接保存 definition；GLOBAL 规则需要读取现有规则并按当前契约保存可执行定义和 sourceRuleRef，不能只有引用就假设运行时自动取规则。
- 支持部分/位置/保长遮罩、固定值与 NULLIFY；遮罩字符串长度和目标类型需兼容，NULLIFY 要求允许空。
- 这是运行时字段变换，不会为本地快照中的真实数据自动脱敏；本任务不采样。

配置定位（只列关键语义，完整字段读取实时契约）：

- `operations`：独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。

## 逻辑表 Map 与字段

按 output 策略替换源表或追加结果，未处理表透传；多操作读取同一份节点入口 Map。 按规则原位替换字段值，未选字段透传；不把脱敏当作哈希或可逆加密。

## 最小配置示例

前提：orders.phone 是 STRING，用户已批准输出脱敏后的字段。

以下仅为节点 `configuration`；资源 UUID、字段与单位应换成已确认的真实元数据。稳定的操作/写入 ID 由当前任务生成。

```json
{
  "operations": [
    {
      "operationId": "11111111-1111-4111-8111-111111111111",
      "sourceTableName": "orders",
      "output": {
        "mode": "CREATE_NEW_TABLE",
        "outputTableName": "processed"
      },
      "fieldRules": [
        {
          "fieldName": "phone",
          "ruleSource": "INLINE",
          "definition": {
            "strategy": "KEEP_LENGTH_MASK",
            "maskCharacter": "*"
          }
        }
      ]
    }
  ]
}
```

## 预校验检查与修正

将节点放入完整画布，按 Canvas 指南调用 Engine 预校验，核对输入/输出表及字段；此处没有真实数据测试步骤。

| 诊断或检查点 | 处理方式 |
| --- | --- |
| `COLUMN_NOT_FOUND` | 核对 fieldName。 |
| `REQUIRED_CONFIGURATION` | 读取实际脱敏规则契约，补齐所选策略。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
