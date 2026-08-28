package cn.superhuang.data.scalpel.contract.execution;

/** Safe readiness result for one Dispatcher dependency. */
public record DispatcherRuntimeDependency(String name, String state, String detail) {
}
