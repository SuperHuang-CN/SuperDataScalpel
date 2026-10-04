package cn.superhuang.superapigateway.controlplane.web.response;

import cn.superhuang.superapigateway.controlplane.web.request.TrafficPolicyRequest;
public record TrafficPolicyResponse(TrafficPolicyRequest policy, long targetRevision, long loadedRevision, String scope) {}
