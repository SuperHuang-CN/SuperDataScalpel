package cn.superhuang.data.scalpel.business.datasource.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "data-scalpel.datasource")
public record DataSourceSecurityProperties(String credentialKey) {
}
