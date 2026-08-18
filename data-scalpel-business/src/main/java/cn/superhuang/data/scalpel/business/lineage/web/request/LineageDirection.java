package cn.superhuang.data.scalpel.business.lineage.web.request;

public enum LineageDirection {
    UPSTREAM,
    DOWNSTREAM,
    BOTH;

    public boolean includesUpstream() {
        return this == UPSTREAM || this == BOTH;
    }

    public boolean includesDownstream() {
        return this == DOWNSTREAM || this == BOTH;
    }
}
