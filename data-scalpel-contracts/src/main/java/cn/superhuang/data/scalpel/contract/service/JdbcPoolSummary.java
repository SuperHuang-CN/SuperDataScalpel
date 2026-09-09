package cn.superhuang.data.scalpel.contract.service;

/** In-memory JDBC pool sample. Null metrics mean unavailable, never zero. */
public record JdbcPoolSummary(
        String poolName,
        Integer maximum,
        Integer total,
        Integer active,
        Integer idle,
        Integer waiting,
        Double utilizationPercent,
        Long acquisitionTimeoutCount,
        String lastSaturationAt,
        Integer executingConnections,
        Integer idleInTransactionConnections,
        Integer borrowedIdleConnections,
        Integer longRunningQueryCount,
        Integer longHeldConnectionCount
) {
}
