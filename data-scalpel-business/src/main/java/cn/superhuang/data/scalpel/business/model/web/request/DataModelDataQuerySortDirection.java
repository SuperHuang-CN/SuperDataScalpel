package cn.superhuang.data.scalpel.business.model.web.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "查询排序方向：ASC 升序，DESC 降序")
public enum DataModelDataQuerySortDirection {
    ASC,
    DESC
}
