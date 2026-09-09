package cn.superhuang.data.scalpel.business.service.domain;

public enum SpatialStyleMode {
    /** Legacy V1 value. New responses normalize this to CARTOGRAPHY. */
    SIMPLE,
    CARTOGRAPHY,
    UPLOADED_SLD
}
