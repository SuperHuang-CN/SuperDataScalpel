package cn.superhuang.data.scalpel.business.dsh.web.request;

import jakarta.validation.constraints.*;
public record RespondDshQuestionRequest(@NotEmpty java.util.List<@jakarta.validation.Valid DshQuestionAnswerRequest> answers) {}
