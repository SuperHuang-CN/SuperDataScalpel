# TYPE_CAST · 类型转换

[节点索引](index.md) · [Canvas 共用规则](../task-canvas.md)

## 用途与选择

支持 BATCH 和 STREAMING 的字段类型转换配置；对一个或多个不同上游表分别在原字段位置执行显式 Spark cast、容错 try_cast 或受控日期时间转换。未配置字段完整继承，转换字段清除物理默认值、生成属性和注释；流任务禁止转换事件时间字段。

## 模式与连线

- 模式：BATCH / STREAMING；类别：PROCESSOR。
- 至少一条入边，允许零条或多条出边；从上游 Map 按逻辑表名取表，不按边序号取表。
- 输入有界性及批流差异见下文。

## 关键配置与行为

- FAIL 使用严格转换；SET_NULL 失败变 NULL，会影响可空性，必须在方案中明确。
- 字符串时间解析、时间格式化、Epoch 转时间分别使用对应受控选项；单位和时区不能猜测。普通数值转换不附加日期选项。
- targetType 是平台类型对象；DECIMAL 明确 precision/scale。流任务禁止转换事件时间列。

配置定位（只列关键语义，完整字段读取实时契约）：

- `operations`：独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。

## 逻辑表 Map 与字段

按 output 策略替换源表或追加结果，未处理表透传；多操作读取同一份节点入口 Map。 原位转换选定列，其余保留；转换列清除物理默认值、生成属性和注释。

## 最小配置示例

前提：orders.id 为 INTEGER；示例扩大为 LONG。

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
      "casts": [
        {
          "columnName": "id",
          "targetType": {
            "type": "LONG"
          },
          "failureStrategy": "FAIL"
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
| `STREAM_EVENT_TIME_COLUMN_IMMUTABLE` | 不要改写流事件时间类型。 |
| `EMPTY_TYPE_CASTS` | 至少指定一个实际转换。 |

ERROR 修正后重新预校验最终定义；WARNING 记录影响，不把结构通过表述为数据值或输出结果通过。
