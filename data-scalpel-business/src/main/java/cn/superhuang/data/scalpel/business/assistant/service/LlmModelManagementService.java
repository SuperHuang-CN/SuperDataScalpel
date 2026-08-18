package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.domain.LlmModelConfiguration;
import cn.superhuang.data.scalpel.business.assistant.domain.LlmModelTestStatus;
import cn.superhuang.data.scalpel.business.assistant.domain.LlmProtocol;
import cn.superhuang.data.scalpel.business.assistant.gateway.LlmGateway;
import cn.superhuang.data.scalpel.business.assistant.gateway.LlmGatewayException;
import cn.superhuang.data.scalpel.business.assistant.repository.AssistantRunRepository;
import cn.superhuang.data.scalpel.business.assistant.repository.LlmModelConfigurationRepository;
import cn.superhuang.data.scalpel.business.assistant.web.request.CreateLlmModelRequest;
import cn.superhuang.data.scalpel.business.assistant.web.request.UpdateLlmModelRequest;
import cn.superhuang.data.scalpel.business.assistant.web.response.AvailableLlmModelResponse;
import cn.superhuang.data.scalpel.business.assistant.web.response.LlmModelConfigurationResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class LlmModelManagementService {

    private static final String TEST_TOOL = "report_datascalpel_compatibility";
    private static final String TEST_TOKEN = "DATASCALPEL_TOOL_OK";
    private static final Set<String> RESERVED_EXTRA_PARAMETER_NAMES = Set.of(
            "model", "messages", "tools", "tool_choice", "stream", "temperature", "max_tokens", "n",
            "authorization", "api_key", "apikey", "access_token"
    );

    private final LlmModelConfigurationRepository repository;
    private final AssistantRunRepository runRepository;
    private final SearchEngine searchEngine;
    private final AssistantCredentialCipher credentialCipher;
    private final LlmGateway gateway;
    private final ObjectMapper objectMapper;
    private final LlmModelTestResultService testResultService;

    public LlmModelManagementService(
            LlmModelConfigurationRepository repository,
            AssistantRunRepository runRepository,
            SearchEngine searchEngine,
            AssistantCredentialCipher credentialCipher,
            LlmGateway gateway,
            ObjectMapper objectMapper,
            LlmModelTestResultService testResultService
    ) {
        this.repository = repository;
        this.runRepository = runRepository;
        this.searchEngine = searchEngine;
        this.credentialCipher = credentialCipher;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.testResultService = testResultService;
    }

    @Transactional(readOnly = true)
    public PageResponse<LlmModelConfigurationResponse> search(SearchRequest request) {
        var page = searchEngine.search(request, LlmModelConfiguration.class, repository);
        return new PageResponse<>(
                page.getContent().stream().map(LlmModelConfigurationResponse::from).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public LlmModelConfigurationResponse get(UUID id) {
        return LlmModelConfigurationResponse.from(requireModel(id));
    }

    @Transactional
    public LlmModelConfigurationResponse create(CreateLlmModelRequest request) {
        String name = request.name().trim();
        validateUniqueName(name, null);
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        String extraRequestParameters = normalizeExtraRequestParameters(request.extraRequestParameters());
        try {
            LlmModelConfiguration model = LlmModelConfiguration.create(
                    name,
                    request.protocol(),
                    baseUrl,
                    request.modelName(),
                    credentialCipher.encryptOptional(request.apiKey()),
                    extraRequestParameters
            );
            return LlmModelConfigurationResponse.from(repository.saveAndFlush(model));
        } catch (DataIntegrityViolationException exception) {
            throw conflict("AI 模型显示名称已存在", exception);
        }
    }

    @Transactional
    public LlmModelConfigurationResponse update(UUID id, UpdateLlmModelRequest request) {
        LlmModelConfiguration model = requireModel(id);
        String name = request.name().trim();
        validateUniqueName(name, id);
        String baseUrl = normalizeBaseUrl(request.baseUrl());
        String extraRequestParameters = normalizeExtraRequestParameters(request.extraRequestParameters());
        String ciphertext = request.clearApiKey()
                ? null
                : request.apiKey() != null && !request.apiKey().isBlank()
                    ? credentialCipher.encryptOptional(request.apiKey())
                    : model.getApiKeyCiphertext();
        boolean connectionChanged = request.protocol() != model.getProtocol()
                || !baseUrl.equals(model.getBaseUrl())
                || !request.modelName().trim().equals(model.getModelName())
                || !Objects.equals(ciphertext, model.getApiKeyCiphertext())
                || !extraRequestParameters.equals(model.getExtraRequestParameters());
        if (connectionChanged) {
            model.updateConnection(
                    name, request.protocol(), baseUrl, request.modelName(), ciphertext, extraRequestParameters
            );
        } else {
            model.updateDisplayName(name);
        }
        return LlmModelConfigurationResponse.from(repository.saveAndFlush(model));
    }

    /** Executes the remote compatibility check without holding a management database transaction. */
    public LlmModelConfigurationResponse test(UUID id) {
        LlmModelConfiguration snapshot = requireModel(id);
        LlmModelTestStatus status;
        String message;
        try {
            LlmGateway.Completion result = gateway.complete(toRuntime(snapshot), new LlmGateway.CompletionRequest(
                    List.of(
                            LlmGateway.Message.system("You are running a protocol compatibility test. Call the required tool exactly once."),
                            LlmGateway.Message.user("Call the compatibility tool with token " + TEST_TOKEN + ".")
                    ),
                    List.of(new LlmGateway.ToolDefinition(
                            TEST_TOOL,
                            "Reports that OpenAI-compatible tool calling is available.",
                            Map.of(
                                    "type", "object",
                                    "properties", Map.of("token", Map.of("type", "string")),
                                    "required", List.of("token"),
                                    "additionalProperties", false
                            )
                    )),
                    TEST_TOOL
            ));
            boolean compatible = result.toolCalls().stream().anyMatch(call -> TEST_TOOL.equals(call.name())
                    && testTokenMatches(call.argumentsJson()));
            status = compatible ? LlmModelTestStatus.AVAILABLE : LlmModelTestStatus.INCOMPATIBLE;
            message = compatible ? "模型服务可用，工具调用测试通过" : "模型可以响应，但未按要求返回工具调用";
        } catch (LlmGatewayException exception) {
            status = LlmModelTestStatus.UNAVAILABLE;
            message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? (exception.isTimeout() ? "模型服务响应超时" : "模型服务不可用")
                    : exception.getMessage();
        }
        return testResultService.record(id, snapshot.getUpdatedAt(), status, message);
    }

    @Transactional
    public LlmModelConfigurationResponse enable(UUID id) {
        LlmModelConfiguration model = requireModel(id);
        if (model.getTestStatus() != LlmModelTestStatus.AVAILABLE) {
            throw conflict("只有通过工具调用兼容性测试的模型可以启用", null);
        }
        model.enable(!repository.existsByDefaultModelTrue());
        return LlmModelConfigurationResponse.from(repository.saveAndFlush(model));
    }

    @Transactional
    public LlmModelConfigurationResponse disable(UUID id) {
        LlmModelConfiguration model = requireModel(id);
        model.disable();
        return LlmModelConfigurationResponse.from(repository.saveAndFlush(model));
    }

    @Transactional
    public LlmModelConfigurationResponse setDefault(UUID id) {
        LlmModelConfiguration selected = requireModel(id);
        if (!selected.isEnabled() || selected.getTestStatus() != LlmModelTestStatus.AVAILABLE) {
            throw conflict("只有已启用且可用的模型可以设为默认模型", null);
        }
        for (LlmModelConfiguration model : repository.findAllByDefaultModelTrue()) {
            if (!model.getId().equals(id)) model.clearDefault();
        }
        selected.markDefault();
        return LlmModelConfigurationResponse.from(repository.saveAndFlush(selected));
    }

    @Transactional
    public void delete(UUID id) {
        LlmModelConfiguration model = requireModel(id);
        if (model.isEnabled()) throw conflict("请先停用 AI 模型", null);
        if (runRepository.existsByModelId(id)) throw conflict("AI 模型已有会话运行记录，只能停用", null);
        repository.delete(model);
    }

    @Transactional(readOnly = true)
    public List<AvailableLlmModelResponse> availableModels() {
        return repository.findAllByEnabledTrueAndTestStatusOrderByDefaultModelDescNameAsc(LlmModelTestStatus.AVAILABLE)
                .stream().map(AvailableLlmModelResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public LlmModelConfiguration requireAvailable(UUID id) {
        return repository.findByIdAndEnabledTrueAndTestStatus(id, LlmModelTestStatus.AVAILABLE)
                .orElseThrow(() -> conflict("所选 AI 模型不可用", null));
    }

    @Transactional(readOnly = true)
    public LlmModelConfiguration requireDefault() {
        return repository.findFirstByDefaultModelTrueAndEnabledTrueAndTestStatus(LlmModelTestStatus.AVAILABLE)
                .orElseThrow(() -> conflict("系统尚未配置可用的默认 AI 模型", null));
    }

    public LlmGateway.RuntimeModel toRuntime(LlmModelConfiguration model) {
        return new LlmGateway.RuntimeModel(
                model.getId(), model.getName(), model.getProtocol(), model.getBaseUrl(), model.getModelName(),
                credentialCipher.decryptOptional(model.getApiKeyCiphertext()),
                readExtraRequestParameters(model.getExtraRequestParameters())
        );
    }

    private String normalizeExtraRequestParameters(String value) {
        String source = value == null || value.isBlank() ? "{}" : value.trim();
        try {
            JsonNode root = objectMapper.readTree(source);
            if (root == null || !root.isObject()) {
                throw badRequest("额外请求参数必须是 JSON 对象", null);
            }
            for (Map.Entry<String, JsonNode> property : root.properties()) {
                String normalizedName = property.getKey().trim().toLowerCase(Locale.ROOT).replace('-', '_');
                if (RESERVED_EXTRA_PARAMETER_NAMES.contains(normalizedName)) {
                    throw badRequest("额外请求参数不能包含系统保留字段：" + property.getKey(), null);
                }
            }
            String normalized = objectMapper.writeValueAsString(root);
            if (normalized.length() > 16_000) {
                throw badRequest("额外请求参数不能超过 16000 个字符", null);
            }
            return normalized;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw badRequest("额外请求参数不是有效的 JSON 对象", exception);
        }
    }

    private Map<String, Object> readExtraRequestParameters(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("AI 模型额外请求参数无效", exception);
        }
    }

    private boolean testTokenMatches(String argumentsJson) {
        try {
            return TEST_TOKEN.equals(objectMapper.readTree(argumentsJson).path("token").asText());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void validateUniqueName(String name, UUID excludedId) {
        boolean exists = excludedId == null
                ? repository.existsByNameIgnoreCase(name)
                : repository.existsByNameIgnoreCaseAndIdNot(name, excludedId);
        if (exists) throw conflict("AI 模型显示名称已存在", null);
    }

    private static String normalizeBaseUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("invalid model URL");
            }
            return value.trim().replaceAll("/+$", "");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型服务地址必须是有效的 HTTP 或 HTTPS 地址", exception);
        }
    }

    private LlmModelConfiguration requireModel(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI 模型不存在"));
    }

    private static ResponseStatusException conflict(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message, cause);
    }

    private static ResponseStatusException badRequest(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message, cause);
    }
}
