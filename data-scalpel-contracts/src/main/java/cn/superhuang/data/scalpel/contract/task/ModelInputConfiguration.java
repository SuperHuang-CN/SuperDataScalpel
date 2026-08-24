package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record ModelInputConfiguration(List<ModelInputSelection> models) {
    public ModelInputConfiguration {
        models = models == null ? List.of() : List.copyOf(models);
    }

    public ModelInputConfiguration(String modelId) {
        this(modelId == null ? List.of() : List.of(new ModelInputSelection(modelId)));
    }
}
