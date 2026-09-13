package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.service.domain.SpatialGeometryFamily;
import cn.superhuang.data.scalpel.business.service.domain.SpatialStyleMode;
import cn.superhuang.data.scalpel.business.service.domain.SpatialStyleSyncStatus;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;

import java.time.Instant;
import java.util.List;

@Schema(description = "空间数据服务当前样式、可编辑字段及其与 GeoServer 的本地同步记录。")

public record SpatialDataServiceStyleResponse(
        @Schema(description = "当前样式来源：CARTOGRAPHY 结构化制图、UPLOADED_SLD 上传文件；SIMPLE 为旧数据兼容值。")
        SpatialStyleMode mode,
        @Schema(description = "发布模型 Geometry 字段的精确几何类型。")
        GeometryKind geometryKind,
        @Schema(description = "点、线或面等几何族，用于判断简单样式编辑能力。")
        SpatialGeometryFamily geometryFamily,
        @Schema(description = "当前几何族是否支持结构化简单样式编辑；通用或不受支持的 Geometry 通常为 false。")
        boolean simpleEditable,
        @Schema(description = "当前保存的结构化样式；UPLOADED_SLD 模式或通用 Geometry 时可能为空。")
        SpatialStyleDocument styleDocument,
        @Schema(description = "根据几何族生成的默认结构化样式，用于重置或首次初始化；无法生成时为空。")
        SpatialStyleDocument defaultStyleDocument,
        @Schema(description = "当前发布模型中可用于分类渲染或标注的字段列表，按模型字段顺序排列。")
        List<SpatialStyleFieldResponse> fields,
        @Schema(description = "最近上传并保留的 SLD 原始文件名；从 UPLOADED_SLD 切回 CARTOGRAPHY 后仍会保留，以便重新激活；从未上传或更换模型后为空。")
        String sldFileName,
        @Schema(description = "最近上传并保留的 SLD UTF-8 字节数；从未上传或更换模型后为空。")
        Integer sldFileSize,
        @Schema(description = "最近上传并保留的完整 SLD 1.0 XML；切回 CARTOGRAPHY 后仍会返回，以便预览或重新激活；从未上传或更换模型后为空。")
        String uploadedSldText,
        @Schema(description = "Admin 中当前样式版本，初始可为 0；保存不同结构化样式、切换模式、上传 SLD 或更换模型时递增。重复保存相同结构化样式或重复激活当前上传样式不变，但重复上传相同文件仍递增。")
        int styleVersion,
        @Schema(description = "最近一次成功应用到 GeoServer 的样式版本；尚未应用或远端样式已移除时为空，小于 styleVersion 表示本地样式较新。该值来自本地记录，不会在查询时实时核对 GeoServer。")
        Integer appliedStyleVersion,
        @Schema(description = "本地记录的样式同步状态：NOT_APPLIED 尚无应用版本，OUT_OF_SYNC 本地版本较新，SYNCING 正在调用 GeoServer，IN_SYNC 当前版本最近一次已成功应用，SYNC_FAILED 当前版本应用失败；查询不会实时核对远端。")
        SpatialStyleSyncStatus syncStatus,
        @Schema(description = "最近一次样式同步失败的安全摘要；未失败时为空。")
        String syncError,
        @Schema(description = "appliedStyleVersion 最近成功应用到服务引擎的时间；尚未成功同步时为空。")
        Instant appliedAt,
        @Schema(description = "根据本地服务 status=ENABLED 且 deploymentStatus=DEPLOYED 判断是否已部署；不会实时访问 GeoServer。false 时可编辑样式，但不能调用应用样式。")
        boolean deployed
) {
}
