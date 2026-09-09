# 空间汇总日历窗口（Canvas 4.39）

适用于 `SPATIAL_BIN_AGGREGATE` 与 `SPATIAL_SUMMARIZE_WITHIN` 的批处理时间切片。
对齐参照保持 Enterprise 11.3 GeoAnalytics Server；不把 Pro / Engine 的同名工具参数混入 Server。
本文定义平台可执行语义，不声明未经真实 ArcGIS 服务验证的 DST、月末和默认参考时刻完全等价。

## 配置与兼容

在既有 `SpatialTemporalSlicing` 上增加可选对象：

```ts
calendar?: {
  mode: 'FIXED_DURATION' | 'CALENDAR' | null;
  intervalUnit: 'MILLISECONDS' | 'SECONDS' | 'MINUTES' | 'HOURS'
    | 'DAYS' | 'WEEKS' | 'MONTHS' | 'YEARS' | null;
  repeatIntervalUnit: 'MILLISECONDS' | 'SECONDS' | 'MINUTES' | 'HOURS'
    | 'DAYS' | 'WEEKS' | 'MONTHS' | 'YEARS' | null;
} | null;
```

- 缺失/null 或 `FIXED_DURATION` 使用原固定算法；DAYS 始终是 24 小时，旧任务不自动开启日历模式。
- `CALENDAR` 使用对象内的单位；根 `intervalUnit/repeatIntervalUnit` 仍保留为固定模式草稿，不参与日历计算。
- 根 `interval/repeatInterval` 的整数数值两模式共用，切换不转换或重置数值。窗宽必须为正整数；重复数值为 null 时使用窗宽及其单位，保留但不执行独立重复单位。
- 时间字段、参考时刻、时区及结果字段名继续使用既有根字段。不增加多套时间来源或输出列。
- 非 null 对象无论是否活动都要求 4.39；Business、GraphPlan、前端导入共同执行版本门槛。
  旧小版本可读并规范化为当前小版本，但携带新字段的低版本定义必须拒绝。
- null 模式/单位、0 窗宽等是不完整业务草稿，可以保存，Compiler 明确报错；未知枚举、数组代替对象等结构错误拒绝。
- 保留 Java 九参数便利构造器；Manifest、Result、HTTP API 不升级。

## 边界语义

| 项目 | 日历模式 |
| --- | --- |
| 毫秒、秒、分、小时 | 实际经过的固定时长 |
| 日、周 | 按 IANA 时区的本地日历推进；周为 7 个日历日 |
| 月、年 | 按原参考时刻推进月份；年为 12 个月，不使用 30/365 天近似 |
| 参考时刻缺失 | Unix Epoch 在所选时区对应的本地时刻，不默认为本地午夜 |
| 带偏移 ISO 时间 | 偏移确定唯一瞬时，再转换到配置时区 |
| 无偏移 ISO 时间 | 必须在配置时区唯一存在；DST 空隙/重叠拒绝，要求明确偏移 |
| 时间精度 | 参考时刻至多微秒，不静默截断更高精度 |
| 起止 | `[start,end)`；NULL 时间、空隙内观测不参与 |

窗口起点 `S(k) = advance(reference, k × repeat)`，k 可为负数。
窗宽/步长同属月年族或日周族时，终点也从原参考时刻加总周期计算：
例如参考 `2024-01-31`、月宽/月步，得到 `[Jan31,Feb29)`、`[Feb29,Mar31)`，不会漂移到 Mar29。
跨族组合先定位起点，再在起点加窗宽。重复小于/等于/大于窗宽可形成重叠、连续或留空；
混合单位不能仅比较整数数值判断重叠。

生成的本地边界沿用 Java ZonedDateTime 时区规则：DST 空隙向前移动，重叠优先保留参考偏移（若有效）。
跨过整日跳变时忽略零长度窗口，并按完整起止对去重，不把同起点但终点不同的窗口误合并。
纽约日历日可为 23/25 小时；固定 1 天或日历模式 24 小时仍是 24 小时。

## 执行、空结果与安全

- 两节点复用一个按观测展开窗口的无状态 Java UDF。输入/输出中间边界为微秒 Long，再恢复 Spark Timestamp，
  避免 java8 datetime API 开关改变 UDF 的 Java 参数类型。
- 窗口只 explode 一次，起止从同一 Struct 读取；不把重叠起止独立展开形成笛卡尔积。
- 编译只构造惰性/零行计划；无 Driver collect、额外 Action、物化、缓存或外部时间范围查询。
- 每条观测最多检查 4096 个候选窗口（保守候选区间，可能比最终命中数多）。超限失败，不截断窗口或近似统计。
  不以全表行数作为此限制；空间范围保护仍遵循对应节点规则。
- 只对实际参与观测的时间窗补空格网/空区域；空输入不会凭空生成月份。Within 主表/关联组表共享起止时间身份，
  每个窗口单独汇总，不平均组级统计；时间字段血缘追溯到来源时间列。
- 日历参考值和观测时间不写入 Canvas 安全摘要、Runner 摘要或安全错误。摘要仅标识是否切片、是否日历。

| 问题 | 稳定错误/类别 |
| --- | --- |
| 旧版本携带 calendar | `SPATIAL_CALENDAR_WINDOW_REQUIRE_SCHEMA_VERSION` |
| 未选模式 | `INVALID_SPATIAL_TEMPORAL_MODE` |
| 窗宽/重复无效 | `INVALID_SPATIAL_TEMPORAL_INTERVAL` / `INVALID_SPATIAL_TEMPORAL_REPEAT` |
| 参考时刻/时区无效 | `INVALID_SPATIAL_TEMPORAL_REFERENCE` / `INVALID_TIME_ZONE` |
| 候选展开超限 | `SPATIAL_CALENDAR_WINDOW_LIMIT_EXCEEDED`，CONFIGURATION、不可重试 |
| 实际边界超出微秒算术或 Java 时间范围 | `SPATIAL_CALENDAR_WINDOW_RANGE_INVALID`，SCHEMA、不可重试 |

运行算术/日期异常只抛安全错误码，不携带含原时间值的底层 Cause；统一 Runner 分类与诊断链保持不变。

## 配置面板

两节点 Inspector 仍只常驻一行“时间切片 · 2 日历月 · 日历 [开关] [设置]”。
620px 设置 Modal 复用紧凑编辑器，解释通过邻近帮助图标按需展开：

```text
┌ 设置时间切片                                    ┐
│ 窗口与重复间隔 (?)                              │
│ 时间语义       [日历周期                    ▾] │
│ 时间字段       [event_time                  ▾] │
│ 窗口长度 [2] [日历月 ▾]  重复 [1] [日历月 ▾]   │
│ 参考时刻       [2024-01-31T00:00:00Z          ] │
│ 时区           [Asia/Shanghai                ] │
│ 开始字段 [window_start]  结束字段 [window_end]   │
│                         [取消] [保存草稿]       │
└────────────────────────────────────────────────┘
```

- 切换语义需确认，提示整数不变、单位解释变化与结果风险；取消确认不修改编辑器。
- 仅展示活动模式的单位；两分支单位分别保留。缺失单位、0/负数等就地标红，不阻断保存草稿。
- 每次打开从 Inspector 当前配置克隆独立草稿；取消丢弃，不在下一次打开或重新启用时偷偷恢复取消的编辑。
- Canvas 卡片只展示“固定切片/日历切片”，不展示参考时刻或数据值。

本地验证及未完成范围见[开发清单](canvas-spatial-development-progress.md)。真实 ArcGIS 服务对照、全页面与规模验收未由本次专项代替。
