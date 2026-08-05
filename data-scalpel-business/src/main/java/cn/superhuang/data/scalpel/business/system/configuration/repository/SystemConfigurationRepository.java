package cn.superhuang.data.scalpel.business.system.configuration.repository;

import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfiguration;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SystemConfigurationRepository extends SearchRepository<SystemConfiguration, UUID> {

    Optional<SystemConfiguration> findByConfigKey(String configKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select configuration from SystemConfiguration configuration where configuration.configKey = :configKey")
    Optional<SystemConfiguration> findByConfigKeyForUpdate(@Param("configKey") String configKey);
}
