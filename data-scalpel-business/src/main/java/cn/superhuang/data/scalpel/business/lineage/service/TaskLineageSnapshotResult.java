package cn.superhuang.data.scalpel.business.lineage.service;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;

import java.util.UUID;

public record TaskLineageSnapshotResult(
        UUID snapshotId,
        UUID taskId,
        int definitionVersion,
        int generation,
        LineageCoverage coverage,
        String contentSha256,
        boolean created
) {
}
