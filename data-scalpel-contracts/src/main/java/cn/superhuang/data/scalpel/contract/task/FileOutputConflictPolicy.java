package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("S3 目标目录冲突策略：FAIL_IF_EXISTS 在 targetPath 前缀已存在任何结果时不写入；OVERWRITE 覆盖整个目录前缀。OVERWRITE 不提供 S3 原子替换或失败回滚保证。")
public enum FileOutputConflictPolicy {
    FAIL_IF_EXISTS,
    OVERWRITE
}
