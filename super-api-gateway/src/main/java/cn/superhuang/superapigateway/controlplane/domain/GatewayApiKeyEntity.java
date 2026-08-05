package cn.superhuang.superapigateway.controlplane.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "sag_api_key",
        schema = "super_api_gateway",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sag_api_key_hash", columnNames = "secret_hash"),
                @UniqueConstraint(name = "uk_sag_api_key_name", columnNames = {"consumer_id", "name"}),
                @UniqueConstraint(name = "uk_sag_api_key_external", columnNames = {"source", "external_id"})
        }
)
public class GatewayApiKeyEntity extends BaseEntity {

    @Column(name = "consumer_id", nullable = false)
    private UUID consumerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "secret_hash", nullable = false, length = 64)
    private String secretHash;

    @Column(name = "secret_prefix", nullable = false, length = 16)
    private String secretPrefix;

    @Column(name = "secret_last_four", nullable = false, length = 4)
    private String secretLastFour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApiKeyStatus status;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "external_id", length = 128)
    private String externalId;

    protected GatewayApiKeyEntity() {
    }

    public GatewayApiKeyEntity(
            UUID consumerId,
            String name,
            String secretHash,
            String secretPrefix,
            String secretLastFour,
            String source,
            String externalId
    ) {
        this.consumerId = consumerId;
        this.name = name;
        this.source = source;
        this.externalId = externalId;
        replaceSecret(secretHash, secretPrefix, secretLastFour);
    }

    public void replaceSecret(String secretHash, String secretPrefix, String secretLastFour) {
        this.secretHash = secretHash;
        this.secretPrefix = secretPrefix;
        this.secretLastFour = secretLastFour;
        this.status = ApiKeyStatus.ACTIVE;
        this.rotatedAt = Instant.now();
    }

    public void revoke() {
        status = ApiKeyStatus.REVOKED;
    }

    public UUID getConsumerId() { return consumerId; }
    public String getName() { return name; }
    public String getSecretHash() { return secretHash; }
    public String getSecretPrefix() { return secretPrefix; }
    public String getSecretLastFour() { return secretLastFour; }
    public ApiKeyStatus getStatus() { return status; }
    public Instant getRotatedAt() { return rotatedAt; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
}
