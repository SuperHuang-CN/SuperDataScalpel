package cn.superhuang.data.scalpel.business.mcp.web.response;

import java.util.List;

public record McpAccessTokenDetailResponse(McpAccessTokenResponse token, List<McpAuthorizedServerResponse> authorizedServers) {}
