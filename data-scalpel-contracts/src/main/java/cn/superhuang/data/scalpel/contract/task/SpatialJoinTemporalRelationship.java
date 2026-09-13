package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Canvas 4.59 起空间连接可选的有方向时间关系。左侧为目标要素时间，右侧为连接要素时间；前十二项使用 Allen 区间关系，后三项使用固定时长阈值。")
public enum SpatialJoinTemporalRelationship {
    EQUALS,
    INTERSECTS,
    DURING,
    CONTAINS,
    FINISHES,
    FINISHED_BY,
    MEETS,
    MET_BY,
    OVERLAPS,
    OVERLAPPED_BY,
    STARTS,
    STARTED_BY,
    NEAR,
    NEAR_BEFORE,
    NEAR_AFTER
}
