package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "定义中直接绑定该数据源，或通过存储在该数据源上的模型间接引用它的数据服务摘要；关联模型只表达来源关系，不构成 SQL 访问白名单。")
public record DataSourceRelatedServiceResponse(
        @Schema(description = "数据服务 UUID，可用于查询服务详情或处理依赖。") UUID serviceId,
        @Schema(description = "数据服务在平台内稳定唯一的技术编码。") String serviceCode,
        @Schema(description = "数据服务当前显示名称。") String serviceName,
        @Schema(description = "服务定义类型：STANDARD_TABLE 标准单表、SQL_QUERY 参数化只读 SQL、SCRIPT_API 受控脚本、SPATIAL_FEATURE 空间要素服务。") DataServiceType serviceType,
        @Schema(description = "数据服务当前业务生命周期状态；不等同于 Engine 部署或 API 网关发布的实时状态。") DataServiceStatus status,
        @Schema(description = "当前类型专属服务定义的内容版本，从 1 开始并在定义实际变化时递增；尚未保存该类型定义时为空。") Integer definitionVersion,
        @Schema(description = "服务引用数据源的方式，可同时包含直接引用和经模型间接引用") List<DataSourceRelationKind> relationKinds,
        @Schema(description = "服务在 DataScalpel 中声明的对外路由路径；创建时尚未分配或类型不使用路径时为空。") String routePath,
        @Schema(description = "数据服务管理记录最后更新时间，ISO-8601 UTC 时间戳；不表示 Engine 或网关最后同步时间。") Instant updatedAt
) {
}
