package cn.superhuang.data.scalpel.business.task.web.response;

import java.util.UUID;

public record TaskCompilationCancellationResponse(UUID requestId, String state) {
}
