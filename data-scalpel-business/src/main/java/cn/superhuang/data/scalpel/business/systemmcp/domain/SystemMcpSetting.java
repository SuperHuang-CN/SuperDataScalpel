package cn.superhuang.data.scalpel.business.systemmcp.domain;
import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "ds_system_mcp_setting")
public class SystemMcpSetting extends BaseEntity {
    @Column(nullable = false, unique = true, length = 32)
    private String settingKey;
    @Column(nullable = false)
    private boolean enabled;
    public SystemMcpSetting() {
    }
    public String getSettingKey() {
        return settingKey;
    }
    public void setSettingKey(String value) {
        settingKey = value;
    }
    public boolean getEnabled() {
        return enabled;
    }
    public void setEnabled(boolean value) {
        enabled = value;
    }
}
