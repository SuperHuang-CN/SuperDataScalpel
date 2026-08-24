package cn.superhuang.data.scalpel.business.lineage.web.response;

public record LineageFieldOwnerResponse(
        String key,
        LineageGraphNodeKind kind,
        String label,
        String subtitle,
        int fieldOrder
) {
}
