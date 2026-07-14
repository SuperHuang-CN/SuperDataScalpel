package cn.superhuang.data.scalpel.dialect.api;

import java.util.List;

public record ConnectionOptionDefinition(
        String key,
        String label,
        ConnectionOptionType type,
        String defaultValue,
        List<ConnectionOptionChoice> choices
) {
    public ConnectionOptionDefinition {
        choices = choices == null ? List.of() : List.copyOf(choices);
    }
}
