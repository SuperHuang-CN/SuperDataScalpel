package cn.superhuang.data.scalpel.dispatcher.domain;

public enum DispatcherOutboxState {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED
}
