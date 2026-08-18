package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.quality.domain.ModelQualityRule;
import cn.superhuang.data.scalpel.business.quality.repository.ModelQualityRuleRepository;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.ModelQualityTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.ModelQualityTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateModelQualityTaskDefinitionRequest;
import cn.superhuang.data.scalpel.business.task.web.response.ModelQualityTaskDefinitionResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskModelReferenceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ModelQualityTaskDefinitionService {
    private final DataTaskRepository taskRepository;
    private final ModelQualityTaskDefinitionRepository definitionRepository;
    private final DataModelRepository modelRepository;
    private final ModelQualityRuleRepository ruleRepository;
    private final ModelQualityTaskRunPreparationService runPreparationService;

    public ModelQualityTaskDefinitionService(
            DataTaskRepository taskRepository,
            ModelQualityTaskDefinitionRepository definitionRepository,
            DataModelRepository modelRepository,
            ModelQualityRuleRepository ruleRepository,
            ModelQualityTaskRunPreparationService runPreparationService
    ) {
        this.taskRepository = taskRepository;
        this.definitionRepository = definitionRepository;
        this.modelRepository = modelRepository;
        this.ruleRepository = ruleRepository;
        this.runPreparationService = runPreparationService;
    }

    @Transactional(readOnly = true)
    public ModelQualityTaskDefinitionResponse get(UUID taskId) {
        requireQualityTask(taskId);
        return definitionRepository.findByTaskId(taskId)
                .map(this::response)
                .orElseGet(() -> ModelQualityTaskDefinitionResponse.unconfigured(taskId));
    }

    @Transactional
    public ModelQualityTaskDefinitionResponse update(
            UUID taskId,
            UpdateModelQualityTaskDefinitionRequest request
    ) {
        DataTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        requireQualityTask(task);
        if (task.getStatus() == TaskStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布任务不能修改质检定义，请先停用");
        }
        requireModel(request.modelId());
        ModelQualityTaskDefinition definition = definitionRepository.findByTaskId(taskId).orElse(null);
        if (definition == null) {
            definition = ModelQualityTaskDefinition.create(taskId, request.modelId());
            if (request.failureSampleLimit() != null) {
                definition.update(request.modelId(), request.failureSampleLimit());
            }
        } else if (!definition.update(request.modelId(), request.failureSampleLimit())) {
            return response(definition);
        }
        return response(definitionRepository.saveAndFlush(definition));
    }

    @Transactional(readOnly = true)
    public ModelQualityTaskDefinition requireConfigured(UUID taskId) {
        requireQualityTask(taskId);
        return definitionRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "请先选择质检目标模型"));
    }

    @Transactional(readOnly = true)
    public void validatePublishable(UUID taskId) {
        ModelQualityTaskDefinition definition = requireConfigured(taskId);
        requireModel(definition.getModelId());
        boolean executable = ruleRepository.findAllByModelIdOrderByCreatedAtAsc(definition.getModelId()).stream()
                .anyMatch(rule -> rule.isEnabled() && rule.getInvalidCode() == null);
        if (!executable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标模型至少需要一条启用且有效的质量规则");
        }
    }

    ModelQualityTaskDefinitionResponse response(ModelQualityTaskDefinition definition) {
        DataModel model = requireModel(definition.getModelId());
        List<ModelQualityTaskRunPreparationService.RuleAssessment> assessments =
                runPreparationService.assessRules(model.getId());
        List<ModelQualityTaskDefinitionResponse.SkippedRule> skipped = assessments.stream()
                .filter(assessment -> !assessment.executable())
                .map(assessment -> new ModelQualityTaskDefinitionResponse.SkippedRule(
                        assessment.rule().getId(), assessment.rule().getName(), assessment.skipReason()))
                .toList();
        long executable = assessments.size() - skipped.size();
        return new ModelQualityTaskDefinitionResponse(
                definition.getTaskId(), true, definition.getVersion(), TaskModelReferenceResponse.from(model),
                definition.getFailureSampleLimit(), executable, skipped.size(), skipped, definition.getUpdatedAt());
    }

    private DataTask requireQualityTask(UUID taskId) {
        DataTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        requireQualityTask(task);
        return task;
    }

    private static void requireQualityTask(DataTask task) {
        if (task.getType() != TaskType.SPARK_MODEL_QUALITY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不是 Spark 模型质检任务");
        }
    }

    private DataModel requireModel(UUID modelId) {
        return modelRepository.findById(modelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "质检目标模型不存在"));
    }
}
