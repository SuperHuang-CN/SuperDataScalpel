package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间叠加运算：INTERSECTION 为左右要素成对交叠；ERASE 为左侧独有部分且不输出右属性；UNION 为交叠加双方独有部分；IDENTITY 为交叠加左侧独有部分；SYMMETRICAL_DIFFERENCE 为双方独有部分。FAMILY_2D 下 UNION 仅支持面→面，ERASE/对称差要求同家族，IDENTITY 支持同家族或右侧为面，相交支持任意明确点/线/面家族组合。")
public enum SpatialOverlayOperation {
    INTERSECTION,
    ERASE,
    UNION,
    IDENTITY,
    SYMMETRICAL_DIFFERENCE
}
