package cn.superhuang.data.scalpel.business.model.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Lifecycle of a model whose physical table must be structurally ready before publishing. */
@Schema(description = "模型生命周期：DRAFT 草稿可编辑，PUBLISHED 已发布可被稳定引用，DISABLED 已停用可重新发布")
public enum DataModelStatus {

    DRAFT,
    PUBLISHED,
    DISABLED
}
