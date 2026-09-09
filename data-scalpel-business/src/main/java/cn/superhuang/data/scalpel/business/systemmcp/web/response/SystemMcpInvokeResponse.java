package cn.superhuang.data.scalpel.business.systemmcp.web.response;
public record SystemMcpInvokeResponse(String operationId, String executionStatus, Integer httpStatus, tools.jackson.databind.JsonNode data, tools.jackson.databind.JsonNode problem, String code, String message) {
}
