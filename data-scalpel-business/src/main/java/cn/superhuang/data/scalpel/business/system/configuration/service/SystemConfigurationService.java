package cn.superhuang.data.scalpel.business.system.configuration.service;

import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfiguration;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationDefinition;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationValueType;
import cn.superhuang.data.scalpel.business.system.configuration.repository.SystemConfigurationRepository;
import cn.superhuang.data.scalpel.business.system.configuration.web.request.UpdateSystemConfigurationRequest;
import cn.superhuang.data.scalpel.business.system.configuration.web.response.SystemConfigurationResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class SystemConfigurationService {

    private final SystemConfigurationRepository repository;
    private final SearchEngine searchEngine;

    public SystemConfigurationService(SystemConfigurationRepository repository, SearchEngine searchEngine) {
        this.repository = repository;
        this.searchEngine = searchEngine;
    }

    @Transactional(readOnly = true)
    public PageResponse<SystemConfigurationResponse> search(SearchRequest request) {
        Specification<SystemConfiguration> publicConfigurations = (root, query, builder) ->
                builder.not(root.get("configKey").in(
                        java.util.Arrays.stream(SystemConfigurationDefinition.values())
                                .filter(SystemConfigurationDefinition::isInternal)
                                .map(SystemConfigurationDefinition::getConfigKey)
                                .toList()
                ));
        Page<SystemConfiguration> result = searchEngine.search(
                request,
                SystemConfiguration.class,
                repository,
                publicConfigurations
        );
        return new PageResponse<>(
                result.getContent().stream().map(SystemConfigurationResponse::from).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public String requireValue(String configKey) {
        return repository.findByConfigKey(configKey)
                .map(SystemConfiguration::getConfigValue)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "系统配置不存在：" + configKey
                ));
    }

    @Transactional(readOnly = true)
    public boolean requireBoolean(SystemConfigurationDefinition definition) {
        requireDefinitionType(definition, SystemConfigurationValueType.BOOLEAN);
        return Boolean.parseBoolean(definition.normalizeValue(requireValue(definition.getConfigKey())));
    }

    @Transactional(readOnly = true)
    public int requireInteger(SystemConfigurationDefinition definition) {
        requireDefinitionType(definition, SystemConfigurationValueType.INTEGER);
        return Integer.parseInt(definition.normalizeValue(requireValue(definition.getConfigKey())));
    }

    @Transactional
    public SystemConfigurationResponse update(UUID id, UpdateSystemConfigurationRequest request) {
        SystemConfiguration configuration = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "系统配置不存在"));
        try {
            SystemConfigurationDefinition definition = SystemConfigurationDefinition
                    .findByConfigKey(configuration.getConfigKey())
                    .orElse(null);
            if (definition != null && definition.isInternal()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "系统配置不存在");
            }
            String normalizedValue = definition == null
                    ? request.configValue()
                    : definition.normalizeValue(request.configValue());
            configuration.updateValue(normalizedValue);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        return SystemConfigurationResponse.from(repository.saveAndFlush(configuration));
    }

    private static void requireDefinitionType(
            SystemConfigurationDefinition definition,
            SystemConfigurationValueType expectedType
    ) {
        if (definition == null || definition.getValueType() != expectedType) {
            throw new IllegalArgumentException("系统配置值类型不是 " + expectedType);
        }
    }
}
