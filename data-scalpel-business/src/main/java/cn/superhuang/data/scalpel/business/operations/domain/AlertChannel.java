package cn.superhuang.data.scalpel.business.operations.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import cn.superhuang.data.scalpel.search.SearchExcluded;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ops_alert_channel")
public class AlertChannel extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 2000)
    private String url;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private long configurationVersion;

    @SearchExcluded
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column()
    private String bearerCiphertext;

    @SearchExcluded
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column()
    private String hmacCiphertext;

    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getUrl() { return url; }
    public void setUrl(String value) { url = value; }
    public boolean getEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public long getConfigurationVersion() { return configurationVersion; }
    public void setConfigurationVersion(long value) { configurationVersion = value; }
    public String getBearerCiphertext() { return bearerCiphertext; }
    public void setBearerCiphertext(String value) { bearerCiphertext = value; }
    public String getHmacCiphertext() { return hmacCiphertext; }
    public void setHmacCiphertext(String value) { hmacCiphertext = value; }
}
