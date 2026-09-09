package cn.superhuang.data.scalpel.contract.task;

/** Explicit projection of one attribute from the selected original central feature. */
public record SpatialCenterFeatureColumn(String sourceColumnName, String outputColumnName,
        @com.fasterxml.jackson.annotation.JsonProperty(required = true) boolean included) { }
