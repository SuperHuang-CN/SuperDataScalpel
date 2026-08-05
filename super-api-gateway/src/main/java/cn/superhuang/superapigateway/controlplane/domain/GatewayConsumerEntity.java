package cn.superhuang.superapigateway.controlplane.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sag_consumer",
        schema = "super_api_gateway",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sag_consumer_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_sag_consumer_external", columnNames = {"source", "external_id"})
        }
)
public class GatewayConsumerEntity extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(nullable = false)
    private long revision;

    protected GatewayConsumerEntity() {
    }

    public GatewayConsumerEntity(
            String code,
            String name,
            boolean enabled,
            String description,
            String source,
            String externalId
    ) {
        this.code = code;
        this.source = source;
        this.externalId = externalId;
        update(name, enabled, description);
        this.revision = 1;
    }

    public void update(String name, boolean enabled, String description) {
        this.name = name;
        this.enabled = enabled;
        this.description = description;
        if (revision > 0) revision++;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            revision++;
        }
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public String getDescription() { return description; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
    public long getRevision() { return revision; }
}
