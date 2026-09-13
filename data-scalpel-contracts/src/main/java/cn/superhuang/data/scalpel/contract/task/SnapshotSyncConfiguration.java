package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import java.util.List;

@JsonClassDescription("JDBC 与模型快照同步输出共享的稳定配置视图；只抽取完整来源表、目标 Key、字段映射和目标独有行策略，不包含具体目标引用、运行规模限制、锁参数、ChangeSet 或运行指标。")
public sealed interface SnapshotSyncConfiguration
        permits JdbcSnapshotSyncOutputConfiguration, ModelSnapshotSyncOutputConfiguration {
    String sourceTableName();

    List<String> keyColumns();

    List<JdbcColumnMapping> columnMappings();

    SnapshotDeletePolicy deletePolicy();
}
