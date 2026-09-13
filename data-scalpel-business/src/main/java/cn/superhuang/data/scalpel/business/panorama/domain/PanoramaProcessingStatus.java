package cn.superhuang.data.scalpel.business.panorama.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "全景内容处理状态：QUEUED 候选已入队，PROCESSING 候选正在处理，READY 没有待处理候选且当前成品可用，FAILED 当前候选处理失败。替换候选失败时旧成品仍可用，首次上传失败时没有可预览成品")
public enum PanoramaProcessingStatus { QUEUED, PROCESSING, READY, FAILED }
