package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("JDBC/模型写入模式：APPEND 追加数据；OVERWRITE 保留表结构并先 TRUNCATE 后追加，两个步骤不保证原子性且实时任务禁止；UPSERT 按目标唯一键插入或更新，仅 PostgreSQL/MySQL 支持。UPSERT 当前批次的 Key 含 NULL 或重复时失败。")
public enum JdbcWriteMode {
    APPEND,
    OVERWRITE,
    UPSERT
}
