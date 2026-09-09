package cn.superhuang.data.scalpel.business.systemmcp.web.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
public record UpdateSystemMcpConfigurationRequest(Boolean enabled, @Size(max = 2000) List<@NotNull @Valid ApiChange> changes) {
    public record ApiChange(@NotNull UUID id, @NotNull Boolean enabled) {
    }
}
