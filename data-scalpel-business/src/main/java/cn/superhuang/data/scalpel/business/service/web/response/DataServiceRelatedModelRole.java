package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "模型在数据服务中的角色：PRIMARY 为主模型，REFERENCE 为 SQL 等定义引用的辅助模型。")

public enum DataServiceRelatedModelRole {
    PRIMARY,
    REFERENCE
}
