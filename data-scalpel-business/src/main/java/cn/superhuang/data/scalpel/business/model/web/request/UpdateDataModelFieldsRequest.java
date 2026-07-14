package cn.superhuang.data.scalpel.business.model.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateDataModelFieldsRequest(
        @NotNull @Size(max = 500) List<@Valid DataModelFieldInput> fields
) {
}
