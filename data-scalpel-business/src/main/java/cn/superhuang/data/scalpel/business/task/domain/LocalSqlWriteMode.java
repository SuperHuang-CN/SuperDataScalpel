package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Local SQL 输出写入模式：APPEND 向目标表追加查询结果；OVERWRITE 仅允许平台管理物理表，并按数据库方言受控覆盖目标数据。发布还要求方言支持对应 INSERT SELECT 能力，事务和原子性由方言实现决定。")
public enum LocalSqlWriteMode {
    APPEND,
    OVERWRITE
}
