package cn.superhuang.data.scalpel.business.compute.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "计算后端类型：LOCAL_DOCKER 在 Dispatcher 主机以 Docker 运行；YARN 以 cluster 模式提交；KUBERNETES 提交到 Kubernetes 集群。")
public enum ComputeBackendType {
    LOCAL_DOCKER,
    YARN,
    KUBERNETES
}
