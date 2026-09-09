package cn.superhuang.data.scalpel.business.metric.web.response;
import java.util.UUID;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
public record MetricFieldCandidateResponse(UUID id,String code,String name,PlatformDataType fieldType) {}
