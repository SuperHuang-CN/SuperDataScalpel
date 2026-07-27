package cn.superhuang.data.scalpel.business.service.consumer.credential.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/** Local metadata for an API key. The plaintext secret is never persisted. */
@Entity
@Table(name = "ds_api_consumer_credential")
public class ApiConsumerCredential extends BaseEntity {

    @Column(name = "consumer_id", nullable = false, updatable = false)
    private UUID consumerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "secret_digest", nullable = false, length = 64)
    private String secretDigest;

    @Column(name = "secret_hint", nullable = false, length = 32)
    private String secretHint;

    @Column(nullable = false)
    private long revision;

    protected ApiConsumerCredential() {
    }

    private ApiConsumerCredential(UUID consumerId, String name, String secretDigest, String secretHint) {
        this.consumerId = Objects.requireNonNull(consumerId, "Consumer ID is required");
        this.name = required(name, "凭证名称");
        replaceSecret(secretDigest, secretHint);
    }

    public static ApiConsumerCredential create(
            UUID consumerId,
            String name,
            String secretDigest,
            String secretHint
    ) {
        return new ApiConsumerCredential(consumerId, name, secretDigest, secretHint);
    }

    public void rotate(String secretDigest, String secretHint) {
        replaceSecret(secretDigest, secretHint);
    }

    private void replaceSecret(String secretDigest, String secretHint) {
        this.secretDigest = required(secretDigest, "凭证摘要");
        this.secretHint = required(secretHint, "凭证提示");
        revision++;
    }

    public UUID getConsumerId() {
        return consumerId;
    }

    public String getName() {
        return name;
    }

    public String getSecretHint() {
        return secretHint;
    }

    public String getSecretDigest() {
        return secretDigest;
    }

    public long getRevision() {
        return revision;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }
}
