package cn.superhuang.data.scalpel.business.datasource.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "数据源在任务中的角色：INPUT 输入，OUTPUT 输出")
public enum DataSourceTaskRelationRole {
    INPUT,
    OUTPUT
}
