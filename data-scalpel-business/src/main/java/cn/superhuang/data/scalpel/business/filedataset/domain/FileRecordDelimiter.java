package cn.superhuang.data.scalpel.business.filedataset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文本记录分隔方式：AUTO 将 LF、CRLF 或 CR 都识别为换行；LF 只认 \\n；CRLF 只认连续的 \\r\\n；CR 只认 \\r。")
public enum FileRecordDelimiter {
    AUTO,
    LF,
    CRLF,
    CR
}
