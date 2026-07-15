package cn.superhuang.data.scalpel.business.model.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Target field structure that a user wants to apply to an existing managed physical table. */
public record CreatePhysicalTableChangePlanRequest(
        @NotNull @NotEmpty @Size(max = 500) List<@Valid DataModelFieldInput> fields
) {
}
