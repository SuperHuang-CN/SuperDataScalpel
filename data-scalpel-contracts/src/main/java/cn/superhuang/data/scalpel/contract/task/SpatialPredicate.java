package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("受控空间拓扑谓词，参数顺序为 left、right。INTERSECTS 表示存在任何公共点；CONTAINS 表示 left 包含 right 且内部相交，纯边界情形不算包含；WITHIN 是其反向关系；COVERS/COVERED_BY 包含边界覆盖；TOUCHES 表示边界接触且内部不相交；OVERLAPS 表示同维几何部分重叠且双方都未完全覆盖另一方；CROSSES 表示内部以较低维方式穿越；EQUALS 表示拓扑相等。当前不支持 DISJOINT、DWITHIN、距离或容差。")
public enum SpatialPredicate {
    INTERSECTS,
    CONTAINS,
    WITHIN,
    COVERS,
    COVERED_BY,
    TOUCHES,
    OVERLAPS,
    CROSSES,
    EQUALS
}
