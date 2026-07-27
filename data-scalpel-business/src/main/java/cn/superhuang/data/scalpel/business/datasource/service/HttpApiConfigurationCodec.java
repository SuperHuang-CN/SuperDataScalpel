package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Deterministic JSON persistence for typed HTTP API configuration snapshots. */
public final class HttpApiConfigurationCodec {

    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults().build();

    private HttpApiConfigurationCodec() {
    }

    public static String writeConnectionConfiguration(HttpApiContracts.ConnectionConfiguration value) {
        return write(value, "HTTP API 连接配置");
    }

    public static HttpApiContracts.ConnectionConfiguration readConnectionConfiguration(String value) {
        return read(value, HttpApiContracts.ConnectionConfiguration.class, "HTTP API 连接配置");
    }

    public static String writeCredentials(HttpApiContracts.CredentialBundle value) {
        return write(value, "HTTP API 凭据");
    }

    public static HttpApiContracts.CredentialBundle readCredentials(String value) {
        return read(value, HttpApiContracts.CredentialBundle.class, "HTTP API 凭据");
    }

    public static String writeResource(HttpApiContracts.ResourceDefinition value) {
        return write(value, "HTTP API 资源配置");
    }

    public static HttpApiContracts.ResourceDefinition readResource(String value) {
        return read(value, HttpApiContracts.ResourceDefinition.class, "HTTP API 资源配置");
    }

    private static String write(Object value, String label) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("无法序列化" + label, exception);
        }
    }

    private static <T> T read(String value, Class<T> type, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + "不存在");
        }
        try {
            return MAPPER.readValue(value, type);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法读取" + label, exception);
        }
    }
}
