package cn.superhuang.data.scalpel.business.datasource.service.http;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;

public interface HttpApiConnector {

    String type();

    ConnectionProbeResult testConnection(HttpApiContracts.RuntimeConnection connection);

    PullResult pull(HttpApiContracts.PullRequest request);
}
