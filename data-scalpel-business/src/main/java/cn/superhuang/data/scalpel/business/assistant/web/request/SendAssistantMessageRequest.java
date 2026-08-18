package cn.superhuang.data.scalpel.business.assistant.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendAssistantMessageRequest(
        @NotBlank @Size(max = 8000) String content,
        @Size(max = 80) String pageKey,
        boolean sidebarCollapsed
) {
}
