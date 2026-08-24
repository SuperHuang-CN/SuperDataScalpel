package cn.superhuang.datascalpel.taskengine.contract;

import java.util.UUID;
import java.util.regex.Pattern;

public record OutputWriteExecutionResult(
        String writeId,
        String sourceTableName,
        String targetDisplayName,
        OutputWriteExecutionState state,
        Long affectedRows,
        String errorCode
) {
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,99}");

    public OutputWriteExecutionResult {
        try {
            UUID.fromString(writeId);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("输出写入 ID 必须是 UUID", exception);
        }
        if (sourceTableName == null || sourceTableName.isBlank() || sourceTableName.length() > 256
                || targetDisplayName == null || targetDisplayName.isBlank()
                || targetDisplayName.length() > 1024 || state == null
                || affectedRows != null && affectedRows < 0) {
            throw new IllegalArgumentException("输出写入结果字段无效");
        }
        if (state == OutputWriteExecutionState.SUCCESS) {
            if (errorCode != null) throw new IllegalArgumentException("成功写入不能包含错误码");
        } else if (state == OutputWriteExecutionState.FAILED) {
            if (affectedRows != null || errorCode == null || !ERROR_CODE.matcher(errorCode).matches()) {
                throw new IllegalArgumentException("失败写入结果字段无效");
            }
        } else if (affectedRows != null || errorCode != null) {
            throw new IllegalArgumentException("未成功写入不能包含影响行数或错误码");
        }
    }
}
