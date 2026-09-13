package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("计算执行后端类型：LOCAL_DOCKER 本机 Docker；YARN Hadoop YARN；KUBERNETES Kubernetes 集群。")
public enum ExecutionBackendType {
    LOCAL_DOCKER,
    YARN,
    KUBERNETES
}
