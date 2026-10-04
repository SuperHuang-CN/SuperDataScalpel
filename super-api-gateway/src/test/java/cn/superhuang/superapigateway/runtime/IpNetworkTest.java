package cn.superhuang.superapigateway.runtime;

import cn.superhuang.superapigateway.controlplane.web.request.TrafficPolicyRequest;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class IpNetworkTest {
    @Test void supportsLiteralIpv4AndIpv6() throws Exception {
        assertThat(new IpNetwork("10.2.0.0/16").contains(InetAddress.getByName("10.2.3.4"))).isTrue();
        assertThat(new IpNetwork("10.2.0.0/16").contains(InetAddress.getByName("10.3.3.4"))).isFalse();
        assertThat(new IpNetwork("2001:db8::/32").contains(InetAddress.getByName("2001:db8::1"))).isTrue();
        assertThatThrownBy(() -> new IpNetwork("example.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IpNetwork("127.0.0.1/33")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void denyWinsAndMissingAddressFailsAllowList() throws Exception {
        var policy = RuntimeTrafficPolicy.compile(new TrafficPolicyRequest(0, 0, 0, 0,
                List.of("10.0.0.0/8"), List.of("10.1.1.1")));
        assertThat(policy.permits(InetAddress.getByName("10.1.1.1"))).isFalse();
        assertThat(policy.permits(InetAddress.getByName("10.1.1.2"))).isTrue();
        assertThat(policy.permits(null)).isFalse();
    }
}
