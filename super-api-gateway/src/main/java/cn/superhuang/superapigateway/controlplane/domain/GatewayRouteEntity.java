package cn.superhuang.superapigateway.controlplane.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "sag_route",
        schema = "super_api_gateway",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sag_route_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_sag_route_path", columnNames = "path_pattern"),
                @UniqueConstraint(name = "uk_sag_route_external", columnNames = {"source", "external_id"})
        }
)
public class GatewayRouteEntity extends BaseEntity {

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "path_pattern", nullable = false, length = 500)
    private String pathPattern;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "sag_route_method",
            schema = "super_api_gateway",
            joinColumns = @JoinColumn(name = "route_id")
    )
    @Column(name = "http_method", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private Set<GatewayHttpMethod> methods = new LinkedHashSet<>();

    @Column(name = "route_order", nullable = false)
    private int order;

    @Column(name = "strip_prefix_segments", nullable = false)
    private int stripPrefixSegments;

    @Column(name = "upstream_path", length = 500)
    private String upstreamPath;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(nullable = false)
    private long revision;

    protected GatewayRouteEntity() {
    }

    public GatewayRouteEntity(
            UUID serviceId,
            String code,
            String name,
            String pathPattern,
            Set<GatewayHttpMethod> methods,
            int order,
            int stripPrefixSegments,
            String upstreamPath,
            boolean enabled,
            String source,
            String externalId
    ) {
        this.serviceId = serviceId;
        this.code = code;
        this.source = source;
        this.externalId = externalId;
        update(name, pathPattern, methods, order, stripPrefixSegments, upstreamPath, enabled);
        this.revision = 1;
    }

    public void update(
            String name,
            String pathPattern,
            Set<GatewayHttpMethod> methods,
            int order,
            int stripPrefixSegments,
            String upstreamPath,
            boolean enabled
    ) {
        this.name = name;
        this.pathPattern = pathPattern;
        this.methods.clear();
        this.methods.addAll(methods);
        this.order = order;
        this.stripPrefixSegments = stripPrefixSegments;
        this.upstreamPath = upstreamPath;
        this.enabled = enabled;
        if (revision > 0) revision++;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            revision++;
        }
    }

    public UUID getServiceId() { return serviceId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getPathPattern() { return pathPattern; }
    public Set<GatewayHttpMethod> getMethods() { return Set.copyOf(methods); }
    public int getOrder() { return order; }
    public int getStripPrefixSegments() { return stripPrefixSegments; }
    public String getUpstreamPath() { return upstreamPath; }
    public boolean isEnabled() { return enabled; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
    public long getRevision() { return revision; }
}
