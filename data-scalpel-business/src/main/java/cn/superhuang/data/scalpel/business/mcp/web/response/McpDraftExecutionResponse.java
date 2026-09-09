package cn.superhuang.data.scalpel.business.mcp.web.response;

public record McpDraftExecutionResponse(boolean success, Object structuredContent, String text, long durationMillis,
                                        String error, java.util.List<String> logs) {}
