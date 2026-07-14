package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import org.springframework.stereotype.Component;

import java.util.List;

/** First-version implementation: publishing changes metadata only and intentionally performs no database I/O. */
@Component
public class MetadataOnlyModelPhysicalTablePort implements ModelPhysicalTablePort {

    @Override
    public void prepareForPublish(DataModel model, List<DataModelField> fields) {
        // Physical table creation will be implemented behind this port in a later phase.
    }
}
