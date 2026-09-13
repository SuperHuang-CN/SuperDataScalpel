package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Join 输出字段来源侧：LEFT 指配置的左表，RIGHT 指配置的右表。左右表存在同名字段时必须通过该值明确选择来源。")
public enum JoinOutputColumnSource {
    LEFT,
    RIGHT
}
