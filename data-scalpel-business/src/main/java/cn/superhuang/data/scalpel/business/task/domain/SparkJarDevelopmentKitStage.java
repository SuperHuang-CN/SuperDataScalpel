package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "开发套件生成阶段：QUEUED 排队；VALIDATING 校验；COUNTING 统计采样量；EXPORTING 导出；PACKAGING 打包；UPLOADING 保存制品；COMPLETED 完成；FAILED 失败；EXPIRED 已过期。")
public enum SparkJarDevelopmentKitStage {
    QUEUED, VALIDATING, COUNTING, EXPORTING, PACKAGING, UPLOADING, COMPLETED, FAILED, EXPIRED
}
