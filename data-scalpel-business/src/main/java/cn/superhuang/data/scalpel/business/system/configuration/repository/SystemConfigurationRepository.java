package cn.superhuang.data.scalpel.business.system.configuration.repository;

import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfiguration;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.Optional;
import java.util.UUID;

public interface SystemConfigurationRepository extends SearchRepository<SystemConfiguration, UUID> {

    Optional<SystemConfiguration> findByConfigKey(String configKey);
}
