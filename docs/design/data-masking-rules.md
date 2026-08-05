# 数据脱敏规则和字段脱敏节点

## 1. 范围

第一版把全局脱敏规则定位为方便复用的配置模板，把 Canvas 节点内的 `definition` 定位为唯一执行依据：

```text
全局规则详情 → 选择时复制 → MASK_FIELDS.configuration
                                  ├─ 保存、发布、编译
                                  └─ 批处理或流处理执行
```

全局规则只提供创建、查询、修改和物理删除。系统不维护启用状态、定义版本、历史版本、快照实体、规则任务关系或 `query-snapshot-status` 一类状态接口。删除规则不检查也不修改已经复制配置的任务。

## 2. 统一执行定义

`data-scalpel-contracts` 中的 `MaskingRuleDefinition` 是规则管理、Canvas 和 Task Engine 共用的稳定契约。第一版支持：

| 策略 | 参数 | 类型边界 | 语义 |
| --- | --- | --- | --- |
| `PARTIAL_MASK` | `keepPrefixLength`、`keepSuffixLength`、`maskCharacter` | 仅 `STRING` | 保留前后字符，中间等长掩码；短值整体掩码 |
| `KEEP_LENGTH_MASK` | `maskCharacter` | 仅 `STRING` | 每个字符替换为掩码字符 |
| `FIXED_VALUE` | `fixedValue` | 仅 `STRING` | 使用固定字符串替换，允许空字符串 |
| `NULLIFY` | 无 | 任意可空平台类型 | 替换为原字段类型的 `null` |

所有策略对输入 `null` 保持 `null`。掩码字符默认 `*`，配置时必须恰好包含一个 Unicode 字符；保留长度为 `0..1024` 的整数；固定值最长 1024 个字符。未被当前策略使用的参数必须为空，避免同一执行含义出现多种不稳定表达。

第一版不支持脚本、自由表达式、自由正则、加密、HMAC 或密钥管理。

## 3. 全局规则

规则保存于 `ds_data_masking_rule`：

- `code`：全局唯一，只允许小写字母、数字和下划线，创建后不可修改。
- `name`、`description`：展示信息。
- `strategy`：当前策略的查询投影，用于 Search DSL 筛选。
- `definition_json`：以 PostgreSQL `text` 保存完整强类型执行定义。
- `created_at`、`updated_at`：通用审计时间。

更新直接替换当前定义，不生成新版本；删除为物理删除。规则列表使用统一 `SearchRequest`、`SearchEngine` 和分页协议，支持按编码、名称和策略查询。

接口如下：

```http
GET  /api/v1/masking-rules
GET  /api/v1/masking-rules/{ruleId}
POST /api/v1/masking-rules
POST /api/v1/masking-rules/{ruleId}/actions/update
POST /api/v1/masking-rules/{ruleId}/actions/delete
```

查询要求 `task.view`，创建、修改和删除要求 `task.update`；错误统一返回 RFC 9457 `ProblemDetail`。不提供启用、停用、版本、快照状态或关联任务接口。

管理页面位于“任务管理 → 脱敏规则”，提供搜索、策略筛选、分页、刷新以及创建、查看、修改和删除抽屉。手工预览只处理浏览器组件内由用户输入的测试文本，测试值不进入请求、数据库、Canvas 定义或日志。

## 4. Canvas `MASK_FIELDS`

`MASK_FIELDS` 从 Canvas `1.18` 引入，支持 `BATCH` 和 `STREAMING`，属于无状态 `PROCESSOR`。节点恰好一条输入边和一条输出边，继承来源有界性；配置结构为：

```json
{
  "sourceTableName": "customers",
  "outputTableName": "customers_masked",
  "fieldRules": [
    {
      "fieldName": "mobile",
      "ruleSource": "GLOBAL",
      "sourceRuleRef": {
        "ruleId": "2e73144a-372b-40ae-b972-d56e528470c5",
        "ruleCode": "mask_mobile",
        "ruleName": "手机号脱敏"
      },
      "definition": {
        "strategy": "PARTIAL_MASK",
        "keepPrefixLength": 3,
        "keepSuffixLength": 4,
        "maskCharacter": "*",
        "fixedValue": null
      }
    }
  ]
}
```

每个字段只能出现一次，至少配置一个字段，最多配置 100 个字段。字段必须存在于来源 Schema；非 `NULLIFY` 策略只允许 `STRING`，`NULLIFY` 只允许可空字段。流任务禁止修改事件时间字段。未配置字段原样透传，配置字段在输出表中原位替换，不改变字段名称、顺序、平台类型或 nullable。

`GLOBAL` 必须保存来源规则 ID、编码、名称和完整执行定义；规则 ID 只在保存边界校验 UUID 格式，不查询规则。`INLINE` 不允许保留 `sourceRuleRef`。节点配置禁止保存测试值、真实数据样例或凭据。

## 5. 选择、比较和同步

选择全局规则时，Inspector 调用普通详情接口并一次性复制来源信息与执行定义。全局来源模式的参数只读：

- “同步当前规则”重新读取详情并显式覆盖节点定义。
- “转为自定义”保留当前定义，删除来源信息并开放参数编辑。

打开节点时，Inspector 按去重后的 `ruleId` 使用 TanStack Query 读取普通详情并比较规范化后的执行定义：

- 定义相同：不提示。
- 定义不同：提示全局规则已修改，任务仍使用节点配置。
- 详情返回 `404`：提示来源规则已删除，节点仍可运行。
- 网络、权限或服务异常：提示暂时无法校验，不解释为删除。

比较忽略 JSON 字段顺序和默认参数表达差异，只比较执行定义；规则名称或说明变化不产生执行变化提示。检查本身不修改节点、不触发 dirty，也不阻止保存、发布或运行。只有同步、转为自定义或手工编辑会形成未保存修改，并继续受 Canvas 页面离开确认保护。

## 6. 保存与执行边界

任务保存、发布、编译和运行不读取全局规则，不检查来源规则是否存在，不比较定义，也不以全局定义覆盖节点配置。全局规则修改或删除不会改变已经保存任务的执行结果。

Task Engine 使用 Spark 内置 `Column` 表达式执行，不使用 Java UDF；批处理和流处理复用同一个无状态 Operator。`NULLIFY` 显式转换回原字段类型。字段缺失、类型不兼容、配置非法或 Spark 分析失败会让节点失败，禁止回退输出未经脱敏的原始值。

节点安全摘要只记录节点 ID、字段数量、策略类型，以及全局来源和自定义规则数量。日志禁止记录数据值、固定替换值、完整规则参数、测试值或完整节点配置。
