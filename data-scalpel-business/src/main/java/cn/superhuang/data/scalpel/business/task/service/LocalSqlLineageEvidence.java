package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.dialect.query.lineage.SqlLineageAnalysis;

import java.util.List;

/** Internal AST evidence retained alongside JDBC inspection results. */
public record LocalSqlLineageEvidence(SqlLineageAnalysis analysis) {

    public static LocalSqlLineageEvidence unavailable(String code, String message) {
        return new LocalSqlLineageEvidence(new SqlLineageAnalysis(
                SqlLineageAnalysis.AnalysisStatus.UNAVAILABLE,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(new SqlLineageAnalysis.Warning(code, message, null))
        ));
    }
}
