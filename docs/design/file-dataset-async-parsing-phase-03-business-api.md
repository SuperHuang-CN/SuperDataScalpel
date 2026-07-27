# 文件数据集异步解析阶段三：业务 API

## 上传与装载

初始上传保存文件并返回 `jobIds`。表级追加、全量覆盖和来源替换均接受一个 multipart `file`，
返回 `202 Accepted` 和 `jobId/file/table`。校验成功前不存在来源记录。

当前来源接口：

```http
GET  /api/v1/file-datasets/{datasetId}/tables/{tableId}/sources
POST /api/v1/file-datasets/{datasetId}/tables/{tableId}/actions/append
POST /api/v1/file-datasets/{datasetId}/tables/{tableId}/actions/replace-data
POST /api/v1/file-datasets/{datasetId}/tables/{tableId}/sources/{sourceId}/actions/replace
POST /api/v1/file-datasets/{datasetId}/tables/{tableId}/sources/{sourceId}/actions/delete
```

表级手工解析、来源重试和文件手工准备接口已删除。Excel/GDB 表级装载返回 `409`，只允许使用
整文件替换。

## 响应

- 表响应包含 `sourceCount/totalRowCount/currentLoadJobId/previewSupported`。
- 来源响应只包含当前来源字段，不包含状态、装载方式或错误。
- Job 响应包含类型、装载方式、目标来源、名称快照、队列状态、尝试和租约信息。
- 所有错误使用 RFC 9457 `ProblemDetail`。

解析参数在数据集存在文件、表或非终态 Job 时返回锁定状态。名称、目录和描述始终可修改；
数据集清空后可重新配置解析参数。

## 删除

删除中间来源后压缩顺序。删除最后来源时存在下游引用返回 `409`，否则删除表和字段。事务提交
后立即清理无引用文件与对象。
