package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.domain.LlmModelConfiguration;
import cn.superhuang.data.scalpel.business.assistant.domain.LlmModelTestStatus;
import cn.superhuang.data.scalpel.business.assistant.repository.LlmModelConfigurationRepository;
import cn.superhuang.data.scalpel.business.assistant.web.response.LlmModelConfigurationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class LlmModelTestResultService {

    private final LlmModelConfigurationRepository repository;

    public LlmModelTestResultService(LlmModelConfigurationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public LlmModelConfigurationResponse record(
            UUID id,
            Instant expectedUpdatedAt,
            LlmModelTestStatus status,
            String message
    ) {
        LlmModelConfiguration current = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI 模型不存在"));
        if (!Objects.equals(current.getUpdatedAt(), expectedUpdatedAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "测试期间模型连接配置已变化，请重新测试");
        }
        current.recordTest(status, message, Instant.now());
        return LlmModelConfigurationResponse.from(repository.saveAndFlush(current));
    }
}
