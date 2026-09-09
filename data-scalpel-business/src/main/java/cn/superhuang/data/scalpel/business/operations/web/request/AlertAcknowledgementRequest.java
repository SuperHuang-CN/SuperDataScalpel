package cn.superhuang.data.scalpel.business.operations.web.request;
import jakarta.validation.constraints.Size;
public record AlertAcknowledgementRequest(@Size(max=1000) String reason) {}
