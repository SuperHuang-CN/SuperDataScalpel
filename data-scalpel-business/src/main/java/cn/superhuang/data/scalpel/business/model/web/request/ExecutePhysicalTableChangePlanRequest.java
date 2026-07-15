package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import jakarta.validation.constraints.NotNull;

/** Explicit user choice of one dialect-rendered change execution option. */
public record ExecutePhysicalTableChangePlanRequest(@NotNull TableChangeExecutionMode executionMode) {
}
