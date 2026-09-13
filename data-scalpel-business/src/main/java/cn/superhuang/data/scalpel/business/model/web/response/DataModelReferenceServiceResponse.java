package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceRelatedModelRole;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "引用当前模型的数据服务摘要")
public record DataModelReferenceServiceResponse(
        @Schema(description = "数据服务 UUID") UUID id,
        @Schema(description = "数据服务显示名称") String name,
        @Schema(description = "数据服务类型") DataServiceType type,
        @Schema(description = "数据服务当前生命周期状态") DataServiceStatus status,
        @Schema(description = "模型在服务定义中的角色") DataServiceRelatedModelRole role,
        @Schema(description = "模型在服务定义中的顺序；角色不使用顺序时为空") Integer ordinal
) {
}
