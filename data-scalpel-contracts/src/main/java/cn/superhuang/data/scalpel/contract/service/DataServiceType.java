package cn.superhuang.data.scalpel.contract.service;

/** Stable service modes shared by the Admin control plane and Service Engine. */
public enum DataServiceType {
    STANDARD_TABLE,
    SQL_QUERY,
    SCRIPT_API,
    SPATIAL_SERVICE
}
