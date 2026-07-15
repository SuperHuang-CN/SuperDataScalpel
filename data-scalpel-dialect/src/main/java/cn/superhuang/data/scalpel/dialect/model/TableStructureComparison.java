package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

public record TableStructureComparison(List<TableStructureDifference> differences) {
    public TableStructureComparison {
        differences = List.copyOf(differences);
    }

    public boolean compatible() {
        return differences.isEmpty();
    }
}
