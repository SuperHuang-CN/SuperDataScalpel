package cn.superhuang.superapigateway.runtime;

import cn.superhuang.superapigateway.controlplane.web.request.TrafficPolicyRequest;
import java.net.InetAddress;
import java.util.List;

public record RuntimeTrafficPolicy(TrafficPolicyRequest settings, List<IpNetwork> allowed, List<IpNetwork> denied) {
    public static RuntimeTrafficPolicy compile(TrafficPolicyRequest request) {
        return new RuntimeTrafficPolicy(request, request.allowedCidrs().stream().map(IpNetwork::new).toList(),
                request.deniedCidrs().stream().map(IpNetwork::new).toList());
    }
    public boolean permits(InetAddress address) {
        return denied.stream().noneMatch(network -> network.contains(address))
                && (allowed.isEmpty() || allowed.stream().anyMatch(network -> network.contains(address)));
    }
}
