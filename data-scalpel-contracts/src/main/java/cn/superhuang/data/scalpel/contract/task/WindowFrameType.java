package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("窗口 Frame 类型；当前只支持 ROWS，边界按分区排序后的物理行位置计算，不支持 RANGE、时间间隔或动态字段边界。")
public enum WindowFrameType {
    ROWS
}
