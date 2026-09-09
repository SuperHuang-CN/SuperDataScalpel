package cn.superhuang.data.scalpel.business.operations.service;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.business.operations.repository.AlertSignalRepository;
import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;

@Service
public class TaskRunAlertService {
    private final AlertRuleService rules;
    private final AlertSignalRepository signals;
    private final DataTaskRepository tasks;
    private final ObjectMapper json;
    public TaskRunAlertService(AlertRuleService rules, AlertSignalRepository signals, DataTaskRepository tasks, ObjectMapper json) {
        this.rules = rules; this.signals = signals; this.tasks = tasks; this.json = json;
    }
    /** Called within the transaction that commits the run's terminal state. No external I/O. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void capture(TaskRun run) {
        if (run.getExecutionMode() != TaskRunExecutionMode.REAL || Boolean.TRUE.equals(run.getAlertEvaluated()) || run.getEndedAt() == null) return;
        AlertRuleType type = switch (run.getStatus()) {
            case FAILED, TIMED_OUT -> AlertRuleType.RUN_FAILED;
            case SUCCESS -> run.getQualityConclusion() == QualityConclusion.FAILED ? AlertRuleType.QUALITY_FAILED : null;
            default -> null;
        };
        run.markAlertEvaluated();
        if (type == null) return;
        var effective = rules.effective(type, run.getTaskId()).orElse(null);
        if (effective == null || !effective.getEnabled()
                || effective.getEnabledAt() != null && run.getEndedAt().isBefore(effective.getEnabledAt())) return;
        String summary = type == AlertRuleType.QUALITY_FAILED ? "质检执行完成，质量结论不通过"
                : run.getStatus() == TaskRunStatus.TIMED_OUT ? "任务运行超时" : "任务运行失败";
        String code = run.getErrorCode();
        if (code != null && !code.matches("[A-Z][A-Z0-9_]{0,99}")) code = null;
        var fact = new AlertRunFact(run.getId(), run.getTaskId(),
                tasks.findById(run.getTaskId()).map(t -> t.getName()).orElse("已删除任务"),
                run.getComputeEngineId(), summary, code, run.getErrorDiagnosticId(), run.getEndedAt(), rules.snapshot(effective));
        var signal = new AlertSignal(); signal.setRunId(run.getId()); signal.setRuleType(type);
        signal.setFactsJson(json.writeValueAsString(fact)); signal.setNextAttemptAt(Instant.now()); signals.save(signal);
    }
}
