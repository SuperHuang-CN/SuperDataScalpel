package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Bounded, restartable repair of the file reference projection for existing definitions. */
@Component
public class CanvasFileReferenceIndexInitializer implements ApplicationRunner {
    private final CanvasTaskDefinitionRepository definitions;
    private final CanvasFileDatasetReferenceService references;

    public CanvasFileReferenceIndexInitializer(CanvasTaskDefinitionRepository definitions,
                                              CanvasFileDatasetReferenceService references) {
        this.definitions = definitions;
        this.references = references;
    }

    @Override
    public void run(ApplicationArguments args) {
        var page = PageRequest.of(0, 100);
        while (true) {
            var ids = definitions.findTaskIds(page);
            ids.forEach(references::rebuild);
            if (!ids.hasNext()) return;
            page = page.next();
        }
    }
}
