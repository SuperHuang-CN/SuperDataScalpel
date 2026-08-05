package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.contract.task.*;
import jakarta.validation.constraints.NotNull;

public record UpdateCanvasTaskDefinitionRequest(@NotNull CanvasDefinition definition) {
}
