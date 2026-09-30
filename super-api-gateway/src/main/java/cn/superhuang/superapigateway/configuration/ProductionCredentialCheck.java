package cn.superhuang.superapigateway.configuration;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class ProductionCredentialCheck {
    public ProductionCredentialCheck(SuperApiGatewayProperties properties, Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("local", "test"))) return;
        var admin = properties.admin();
        if ("admin123456".equals(admin.password()) || admin.password().length() < 12
                || admin.jwtSecret().startsWith("change-me") || admin.machineToken().startsWith("change-me")
                || admin.machineToken().length() < 32) {
            throw new IllegalStateException("非 local/test 环境必须设置独立的管理员密码（至少 12 位）、JWT Secret 和 Machine Token（至少 32 位），禁止默认凭据");
        }
    }
}
