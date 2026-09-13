package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("JDBC 增量输入在没有可恢复 Checkpoint 时的首次位置：EARLIEST 使用无下界并读取截至首个安全时间的全部记录；LATEST 以启动时源数据库安全时间为下界，只读取之后的记录；AT_TIME 以必填 startTime 为排他下界。后续批次和恢复均由已提交 Offset 推进。")
public enum JdbcIncrementalStartPosition {
    LATEST,
    EARLIEST,
    AT_TIME
}
