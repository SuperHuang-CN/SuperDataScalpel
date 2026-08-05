package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.DataMaskingRule;
import cn.superhuang.data.scalpel.business.task.repository.DataMaskingRuleRepository;
import cn.superhuang.data.scalpel.business.task.web.request.CreateDataMaskingRuleRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateDataMaskingRuleRequest;
import cn.superhuang.data.scalpel.business.task.web.response.DataMaskingRuleResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.task.CanvasMaskingLimits;
import cn.superhuang.data.scalpel.contract.task.MaskingRuleDefinition;
import cn.superhuang.data.scalpel.contract.task.MaskingStrategy;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
public class DataMaskingRuleService {

    private final DataMaskingRuleRepository repository;
    private final SearchEngine searchEngine;
    private final ObjectMapper objectMapper;

    public DataMaskingRuleService(
            DataMaskingRuleRepository repository,
            SearchEngine searchEngine,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.searchEngine = searchEngine;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<DataMaskingRuleResponse> search(SearchRequest request) {
        Page<DataMaskingRule> page = searchEngine.search(request, DataMaskingRule.class, repository);
        return new PageResponse<>(
                page.getContent().stream().map(this::response).toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public DataMaskingRuleResponse get(UUID id) {
        return response(requireRule(id));
    }

    @Transactional
    public DataMaskingRuleResponse create(CreateDataMaskingRuleRequest request) {
        String code = request.code().trim();
        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "脱敏规则编码已存在");
        }
        MaskingRuleDefinition definition = validateAndCanonicalize(request.definition());
        DataMaskingRule rule = DataMaskingRule.create(
                code,
                request.name(),
                request.description(),
                definition.strategy(),
                serialize(definition)
        );
        try {
            return response(repository.saveAndFlush(rule));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "脱敏规则编码已存在", exception);
        }
    }

    @Transactional
    public DataMaskingRuleResponse update(UUID id, UpdateDataMaskingRuleRequest request) {
        DataMaskingRule rule = requireRule(id);
        MaskingRuleDefinition definition = validateAndCanonicalize(request.definition());
        rule.update(
                request.name(),
                request.description(),
                definition.strategy(),
                serialize(definition)
        );
        return response(repository.saveAndFlush(rule));
    }

    @Transactional
    public void delete(UUID id) {
        DataMaskingRule rule = requireRule(id);
        repository.delete(rule);
        repository.flush();
    }

    private DataMaskingRule requireRule(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "脱敏规则不存在"));
    }

    private MaskingRuleDefinition validateAndCanonicalize(MaskingRuleDefinition source) {
        if (source == null || source.strategy() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "脱敏策略不能为空");
        }
        validateUnusedParameters(source);
        MaskingRuleDefinition definition = source.canonical();
        switch (definition.strategy()) {
            case PARTIAL_MASK -> {
                requireKeepLength(definition.keepPrefixLength(), "保留前缀字符数");
                requireKeepLength(definition.keepSuffixLength(), "保留后缀字符数");
                requireMaskCharacter(definition.maskCharacter());
            }
            case KEEP_LENGTH_MASK -> requireMaskCharacter(definition.maskCharacter());
            case FIXED_VALUE -> {
                if (definition.fixedValue() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "固定替换值不能为空");
                }
                if (definition.fixedValue().length() > CanvasMaskingLimits.MAX_FIXED_VALUE_LENGTH) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "固定替换值不能超过 " + CanvasMaskingLimits.MAX_FIXED_VALUE_LENGTH + " 个字符"
                    );
                }
            }
            case NULLIFY -> {
            }
        }
        return definition;
    }

    private static void validateUnusedParameters(MaskingRuleDefinition definition) {
        MaskingStrategy strategy = definition.strategy();
        boolean invalid = switch (strategy) {
            case PARTIAL_MASK -> definition.fixedValue() != null;
            case KEEP_LENGTH_MASK -> definition.keepPrefixLength() != null
                    || definition.keepSuffixLength() != null
                    || definition.fixedValue() != null;
            case FIXED_VALUE -> definition.keepPrefixLength() != null
                    || definition.keepSuffixLength() != null
                    || definition.maskCharacter() != null;
            case NULLIFY -> definition.keepPrefixLength() != null
                    || definition.keepSuffixLength() != null
                    || definition.maskCharacter() != null
                    || definition.fixedValue() != null;
        };
        if (invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "脱敏策略包含不适用的参数");
        }
    }

    private static void requireKeepLength(Integer value, String label) {
        if (value == null || value < 0 || value > CanvasMaskingLimits.MAX_KEEP_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    label + "必须在 0.." + CanvasMaskingLimits.MAX_KEEP_LENGTH + " 之间"
            );
        }
    }

    private static void requireMaskCharacter(String value) {
        if (value == null || value.codePointCount(0, value.length()) != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "掩码字符必须是一个 Unicode 字符");
        }
    }

    private String serialize(MaskingRuleDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存脱敏规则定义", exception);
        }
    }

    private MaskingRuleDefinition deserialize(String definitionJson) {
        try {
            return objectMapper.readValue(definitionJson, MaskingRuleDefinition.class).canonical();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法读取脱敏规则定义", exception);
        }
    }

    private DataMaskingRuleResponse response(DataMaskingRule rule) {
        return new DataMaskingRuleResponse(
                rule.getId(),
                rule.getCode(),
                rule.getName(),
                rule.getDescription(),
                rule.getStrategy(),
                deserialize(rule.getDefinitionJson()),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
