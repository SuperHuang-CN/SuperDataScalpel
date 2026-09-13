package cn.superhuang.data.scalpel.business.service.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Runtime products managed through the unified service-engine control plane. */
@Schema(description = "服务运行时类型：DATASCALPEL 承载标准表、SQL 和脚本 API；GEOSERVER 承载空间服务。")
public enum ServiceEngineType {
    DATASCALPEL,
    GEOSERVER
}
