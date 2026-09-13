package cn.superhuang.data.scalpel.business.dsh.web.request;
import java.util.UUID;
import jakarta.validation.constraints.*;
public record CreateDshSessionRequest(@NotNull UUID clientSessionId) {}
