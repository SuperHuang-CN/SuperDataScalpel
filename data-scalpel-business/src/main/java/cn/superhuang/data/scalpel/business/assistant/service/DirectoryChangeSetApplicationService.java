package cn.superhuang.data.scalpel.business.assistant.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class DirectoryChangeSetApplicationService {

    private final DirectoryPlanExecutor executor;
    private final AssistantChangeSetStatusService statusService;

    public DirectoryChangeSetApplicationService(
            DirectoryPlanExecutor executor,
            AssistantChangeSetStatusService statusService
    ) {
        this.executor = executor;
        this.statusService = statusService;
    }

    public DirectoryPlanExecutor.Execution approve(UUID id, String username) {
        try {
            return executor.execute(id, username);
        } catch (StaleDirectoryPlanException exception) {
            statusService.markStale(id, exception.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (ResponseStatusException exception) {
            String reason = exception.getReason() == null ? "目录变更执行失败" : exception.getReason();
            statusService.markFailed(id, username, reason);
            throw exception;
        } catch (RuntimeException exception) {
            statusService.markFailed(id, username, "目录变更执行失败");
            throw exception;
        }
    }
}
