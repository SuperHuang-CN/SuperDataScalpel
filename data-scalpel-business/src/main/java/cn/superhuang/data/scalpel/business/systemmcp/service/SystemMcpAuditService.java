package cn.superhuang.data.scalpel.business.systemmcp.service;
import cn.superhuang.data.scalpel.business.systemmcp.domain.SystemMcpAudit;
import cn.superhuang.data.scalpel.business.systemmcp.repository.SystemMcpAuditRepository;
import cn.superhuang.data.scalpel.business.systemmcp.config.SystemMcpProperties;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.util.UUID;
@Service
public class SystemMcpAuditService {
    private final SystemMcpAuditRepository repository;
    private final SystemMcpProperties properties;
    private final TransactionTemplate transaction;
    public SystemMcpAuditService(SystemMcpAuditRepository r,SystemMcpProperties p,PlatformTransactionManager m) {
        repository=r;
        properties=p;
        transaction=new TransactionTemplate(m);
        transaction.setPropagationBehavior(3);
    }
    public void record(String event,String user,UUID token,String operation,String tool,String status,String code,long duration,String changes) {
        try {
            transaction.executeWithoutResult(tx-> {
                var a=new SystemMcpAudit();a.setEventType(event);a.setUsername(user);a.setTokenId(token);a.setOperationId(operation);a.setToolName(tool);a.setStatus(status);a.setErrorCode(code);a.setDurationMs(duration);a.setChangesJson(changes);repository.save(a);
            });
        }
        catch(RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("System MCP audit persistence failed",e);
        }
    }
    @Scheduled(fixedDelay=3600000) public void cleanup() {
        try {
            transaction.executeWithoutResult(tx->repository.deleteByCreatedAtBefore(Instant.now().minus(properties.getAuditRetention())));
        }
        catch(RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("System MCP audit cleanup failed",e);
        }
    }
}
