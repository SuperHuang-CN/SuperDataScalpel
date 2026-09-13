package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/** Square sizes are side lengths in both modes. Only the hexagon interpretation differs. */
@JsonClassDescription("平面格网大小语义：LEGACY_SIDE_LENGTH 将六边形 binSize 解释为中心到顶点的边长；HEXAGON_FLAT_TO_FLAT 将其解释为两条相对平行边之间的距离并除以 √3 得到实际边长。方格始终按边长解释。")
public enum SpatialBinSizeSemantics {
    LEGACY_SIDE_LENGTH,
    HEXAGON_FLAT_TO_FLAT
}
