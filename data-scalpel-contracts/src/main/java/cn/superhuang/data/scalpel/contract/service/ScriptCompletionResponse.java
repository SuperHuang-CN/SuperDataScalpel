package cn.superhuang.data.scalpel.contract.service;

import java.util.List;
import java.util.Map;

/** Completion data consumed by the shared API Studio script workbench. */
public record ScriptCompletionResponse(
        Map<String, List<ScriptCompletionMethod>> clazzs,
        Map<String, String> variables,
        Map<String, String> syntax,
        Map<String, List<Map<String, Object>>> dbInfos,
        String dataSourceId
) {

    public record ScriptCompletionMethod(
            String type,
            String varName,
            String resultType,
            String params,
            String docs
    ) {
    }
}
