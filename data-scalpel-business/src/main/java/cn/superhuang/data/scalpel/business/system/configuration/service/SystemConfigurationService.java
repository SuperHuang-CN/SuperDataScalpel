package cn.superhuang.data.scalpel.business.system.configuration.service;

import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfiguration;
import cn.superhuang.data.scalpel.business.system.configuration.repository.SystemConfigurationRepository;
import cn.superhuang.data.scalpel.business.system.configuration.web.request.UpdateSystemConfigurationRequest;
import cn.superhuang.data.scalpel.business.system.configuration.web.response.SystemConfigurationResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
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
        Page<SystemConfiguration> result = searchEngine.search(request, SystemConfiguration.class, repository);
        return new PageResponse<>(
                result.getContent().stream().map(SystemConfigurationResponse::from).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    @Transactional
    public SystemConfigurationResponse update(UUID id, UpdateSystemConfigurationRequest request) {
        SystemConfiguration configuration = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "系统配置不存在"));
        try {
            configuration.updateValue(request.configValue());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        return SystemConfigurationResponse.from(repository.saveAndFlush(configuration));
    }
}
