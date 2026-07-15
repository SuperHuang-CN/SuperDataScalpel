package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableStructureDifference;

import java.util.List;

/** A live, non-persisted inspection result for one model target table. */
public record ModelPhysicalTableInspection(
        TableIdentifier table,
        PhysicalTableState state,
        boolean createSupported,
        String message,
        List<TableStructureDifference> differences
) {
    public ModelPhysicalTableInspection {
        differences = List.copyOf(differences);
    }

    public boolean exists() {
        return state == PhysicalTableState.MATCHED || state == PhysicalTableState.DRIFTED;
    }

    public boolean compatible() {
        return state == PhysicalTableState.MATCHED;
    }
}
