package cn.superhuang.data.scalpel.business.quality.web.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AcceptModelQualityRuleSuggestionsRequest(
        @NotEmpty @Size(max = 100) List<@NotBlank String> suggestionKeys
) {
}
