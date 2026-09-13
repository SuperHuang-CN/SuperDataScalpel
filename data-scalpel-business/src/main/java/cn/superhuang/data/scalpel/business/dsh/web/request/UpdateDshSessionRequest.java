package cn.superhuang.data.scalpel.business.dsh.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDshSessionRequest(@NotBlank @Size(max = 100) String title) {
    public UpdateDshSessionRequest { if (title != null) title = title.strip(); }
}
