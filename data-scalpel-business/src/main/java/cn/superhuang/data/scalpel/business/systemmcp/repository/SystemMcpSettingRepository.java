package cn.superhuang.data.scalpel.business.systemmcp.repository;
import cn.superhuang.data.scalpel.business.systemmcp.domain.SystemMcpSetting;
import cn.superhuang.data.scalpel.search.SearchRepository;
import java.util.UUID;
public interface SystemMcpSettingRepository extends SearchRepository<SystemMcpSetting, UUID> {
    java.util.Optional<SystemMcpSetting> findBySettingKey(String key);
}
