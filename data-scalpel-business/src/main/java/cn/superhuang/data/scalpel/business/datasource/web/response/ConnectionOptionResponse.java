package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionDefinition;

import java.util.List;

public record ConnectionOptionResponse(
        String key,
        String label,
        String type,
        String defaultValue,
        List<ConnectionOptionChoiceResponse> choices
) {
    static ConnectionOptionResponse from(ConnectionOptionDefinition definition) {
        return new ConnectionOptionResponse(
                definition.key(),
                definition.label(),
                definition.type().name(),
                definition.defaultValue(),
                definition.choices().stream().map(ConnectionOptionChoiceResponse::from).toList()
        );
    }
}
