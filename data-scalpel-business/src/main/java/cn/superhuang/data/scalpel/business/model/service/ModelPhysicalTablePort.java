package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;

import java.util.List;

/**
 * Explicit boundary for the later physical-table phase.
 *
 * <p>Version one supplies a metadata-only implementation and never executes DDL.</p>
 */
public interface ModelPhysicalTablePort {

    void prepareForPublish(DataModel model, List<DataModelField> fields);
}
