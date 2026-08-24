package cn.superhuang.data.scalpel.business.assistant.web.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AcceptTaskCanvasProposalRequest(@NotNull UUID taskId) {
}
