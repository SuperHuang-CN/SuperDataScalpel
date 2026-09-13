package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("数据源用途：SOURCE 作为读取来源，STORAGE 作为平台存储目标，DISTRIBUTION 作为数据分发目标；一个数据源可声明多个适用用途。")
public enum DataSourcePurpose {
    SOURCE,
    STORAGE,
    DISTRIBUTION
}
