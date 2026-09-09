package cn.superhuang.data.scalpel.contract.task;

/** A scalar/geometry observation at an offset inside the same track and fixed time period. */
public record TrackFieldWindowBinding(String name, String sourceColumnName, Integer offset) { }
