package cn.superhuang.data.scalpel.business.panorama.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "全景时间或位置的取值方式：AUTO 每次当前图片变更或资料更新时从当前成品白名单元数据重新取值，无法提取时清空；MANUAL 保留用户维护值，图片替换不会覆盖")
public enum PanoramaValueMode { AUTO, MANUAL }
