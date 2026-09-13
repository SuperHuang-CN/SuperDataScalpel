package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("Canvas 节点在前端画布中的可选位置和尺寸；只影响展示，不参与任务计算语义。")
public record CanvasNodeLayout(
        @JsonPropertyDescription("节点左上角在 Canvas 中的 X 坐标，单位像素。")
        Double x,
        @JsonPropertyDescription("节点左上角在 Canvas 中的 Y 坐标，单位像素。")
        Double y,
        @JsonPropertyDescription("节点在 Canvas 中的宽度，单位像素；未指定时由前端使用默认值。")
        Double width,
        @JsonPropertyDescription("节点在 Canvas 中的高度，单位像素；未指定时由前端使用默认值。")
        Double height
) {
}
