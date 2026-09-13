package cn.superhuang.data.scalpel.business.dsh.web.request;

import jakarta.validation.constraints.*;
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record DshQuestionAnswerRequest(@NotBlank String id, @NotNull java.util.List<String> selected, String custom) {}
