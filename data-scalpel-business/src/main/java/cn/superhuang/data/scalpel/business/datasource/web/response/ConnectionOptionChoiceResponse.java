package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.api.ConnectionOptionChoice;

public record ConnectionOptionChoiceResponse(String value, String label) {
    static ConnectionOptionChoiceResponse from(ConnectionOptionChoice choice) {
        return new ConnectionOptionChoiceResponse(choice.value(), choice.label());
    }
}
