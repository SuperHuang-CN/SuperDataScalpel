package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Canvas 定义加载状态：UNCONFIGURED 尚未保存，LOADED 已加载或兼容升级，INCOMPATIBLE 协议不兼容不可编辑")
public enum CanvasDefinitionLoadStatus {
    UNCONFIGURED,
    LOADED,
    INCOMPATIBLE
}
