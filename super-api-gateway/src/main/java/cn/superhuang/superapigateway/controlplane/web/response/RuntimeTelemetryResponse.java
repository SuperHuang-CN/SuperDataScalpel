package cn.superhuang.superapigateway.controlplane.web.response;

import cn.superhuang.superapigateway.accesslog.AccessLogPublisher;
import cn.superhuang.superapigateway.runtime.GatewayTelemetry;
public record RuntimeTelemetryResponse(GatewayTelemetry.Snapshot traffic, AccessLogPublisher.DeliveryStatus delivery) {}
