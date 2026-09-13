package cn.superhuang.data.scalpel.business.dsh.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
@Entity
@Table(name = "ds_dsh_user_binding")
public class DshUserBinding extends BaseEntity {
    @Column(nullable = false, unique = true) private UUID userId;
    @Column(unique = true) private String workspaceId;
    @Column(nullable = false, unique = true) private UUID tokenId;
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) @Column(nullable = false) private String tokenCiphertext;
    @Column(nullable = false) private String state = "PENDING";
    protected DshUserBinding() {}
    public DshUserBinding(UUID userId, UUID tokenId, String ciphertext) { this.userId=userId; this.tokenId=tokenId; this.tokenCiphertext=ciphertext; }
    public UUID getUserId() { return userId; }
    public String getWorkspaceId() { return workspaceId; }
    public UUID getTokenId() { return tokenId; }
    public String getTokenCiphertext() { return tokenCiphertext; }
    public String getState() { return state; }
    public void ready(String id) { workspaceId=id; state="READY"; }
}
