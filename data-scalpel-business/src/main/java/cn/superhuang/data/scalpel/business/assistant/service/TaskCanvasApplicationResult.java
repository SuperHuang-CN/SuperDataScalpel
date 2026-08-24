package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;

import java.util.UUID;

public record TaskCanvasApplicationResult(
        UUID taskId,
        String mode,
        CanvasDefinition definition
) {
}
