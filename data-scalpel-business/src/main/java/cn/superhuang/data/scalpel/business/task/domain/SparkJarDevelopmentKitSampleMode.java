package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "开发套件样本模式：NONE 不采样，ROW_COUNT 固定行数，PERCENTAGE 按百分比，ALL 全量")
public enum SparkJarDevelopmentKitSampleMode {
    NONE, ROW_COUNT, PERCENTAGE, ALL
}
