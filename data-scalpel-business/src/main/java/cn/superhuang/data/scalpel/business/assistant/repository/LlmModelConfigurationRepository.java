package cn.superhuang.data.scalpel.business.assistant.repository;

import cn.superhuang.data.scalpel.business.assistant.domain.LlmModelConfiguration;
import cn.superhuang.data.scalpel.business.assistant.domain.LlmModelTestStatus;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LlmModelConfigurationRepository extends SearchRepository<LlmModelConfiguration, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByDefaultModelTrue();

    List<LlmModelConfiguration> findAllByDefaultModelTrue();

    Optional<LlmModelConfiguration> findFirstByDefaultModelTrueAndEnabledTrueAndTestStatus(
            LlmModelTestStatus testStatus
    );

    List<LlmModelConfiguration> findAllByEnabledTrueAndTestStatusOrderByDefaultModelDescNameAsc(
            LlmModelTestStatus testStatus
    );

    Optional<LlmModelConfiguration> findByIdAndEnabledTrueAndTestStatus(UUID id, LlmModelTestStatus testStatus);
}
