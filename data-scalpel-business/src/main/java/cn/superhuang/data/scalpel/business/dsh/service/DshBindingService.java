package cn.superhuang.data.scalpel.business.dsh.service;

import cn.superhuang.data.scalpel.business.dsh.domain.DshUserBinding;
import cn.superhuang.data.scalpel.business.dsh.repository.DshUserBindingRepository;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemUser;
import cn.superhuang.data.scalpel.business.systemmcp.service.SystemMcpTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.UUID;
@Service
public class DshBindingService {
    private final DshUserBindingRepository bindings;
    private final SystemMcpTokenService tokens;
    private final DshCredentialCipher cipher;
    private final EntityManager em;
    private final cn.superhuang.data.scalpel.business.systemmcp.service.SystemMcpAuditService audit;
    public DshBindingService(DshUserBindingRepository b,SystemMcpTokenService t,DshCredentialCipher c,EntityManager em,cn.superhuang.data.scalpel.business.systemmcp.service.SystemMcpAuditService audit) { bindings=b;tokens=t;cipher=c;this.em=em;this.audit=audit; }
    public record Credential(UUID userId, UUID tokenId,long revision,String secret) {
        @Override public String toString() { return "DshCredential[redacted]"; }
    }
    @Transactional
    public Credential prepare(UUID userId) {
        // Serialize only local credential provisioning; never hold this lock across HTTP.
        var user=em.find(SystemUser.class,userId,LockModeType.PESSIMISTIC_WRITE);
        if(user==null || !user.isEnabled()) throw DshProblems.error(401,"DSH_USER_UNAVAILABLE","用户已停用或不存在。");
        var binding=bindings.findByUserId(userId).orElse(null);
        if(binding==null) {
            var issued=tokens.createManaged(userId);
            binding=bindings.saveAndFlush(new DshUserBinding(userId,issued.token().id(),cipher.encrypt(userId,issued.secret())));
            var tokenId=issued.token().id();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { audit.record("DSH_TOKEN_CREATED",user.getUsername(),tokenId,null,null,"SUCCESS",null,0,"{\"managed\":true}"); }
            });
        }
        var token=tokens.get(binding.getTokenId());
        String secret=cipher.decrypt(userId,binding.getTokenCiphertext());
        if(!token.isManaged() || !token.getUserId().equals(userId) || !token.getTokenDigest().equals(SystemMcpTokenService.digest(secret)))
            throw DshProblems.error(503,"DSH_CREDENTIAL_UNAVAILABLE","托管凭据状态不一致，请检查配置。");
        if(!token.getEnabled()) { token.setEnabled(true);stateAudit(user.getUsername(),token.getId(),true); }
        return new Credential(userId,token.getId(),token.getRevision(),secret);
    }
    /** Managed status is a projection of the current user lifecycle; no secret is replaced. */
    public void reconcileManagedStates() {
        for(var change:tokens.reconcileManagedStates()) {
            audit.record("DSH_TOKEN_STATE_CHANGED",change.username(),change.tokenId(),null,null,"SUCCESS",null,0,"{\"enabled\":"+change.enabled()+"}");
        }
    }
    private void stateAudit(String username,UUID tokenId,boolean enabled) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCommit() { audit.record("DSH_TOKEN_STATE_CHANGED",username,tokenId,null,null,"SUCCESS",null,0,"{\"enabled\":"+enabled+"}"); }
        });
    }
    @Transactional public void ready(UUID userId,String workspaceId) {
        var binding=bindings.findByUserId(userId).orElseThrow();
        if(binding.getWorkspaceId()!=null&&!binding.getWorkspaceId().equals(workspaceId)) throw DshProblems.error(409,"DSH_WORKSPACE_CHANGED","工作区标识发生变化。");
        binding.ready(workspaceId);
    }
}
