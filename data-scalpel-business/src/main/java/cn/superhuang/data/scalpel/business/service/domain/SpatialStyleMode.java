package cn.superhuang.data.scalpel.business.service.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "空间样式来源：CARTOGRAPHY 为结构化在线制图文档；UPLOADED_SLD 为上传的 SLD XML；SIMPLE 仅为旧数据兼容值，查询响应会规范化为 CARTOGRAPHY。")
public enum SpatialStyleMode {
    /** Legacy V1 value. New responses normalize this to CARTOGRAPHY. */
    SIMPLE,
    CARTOGRAPHY,
    UPLOADED_SLD
}
