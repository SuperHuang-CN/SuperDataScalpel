package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Idempotently repairs the derived data-source reference index for definitions saved before the index existed. */
@Component
public class TaskDataSourceReferenceIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskDataSourceReferenceIndexInitializer.class);

    private final CanvasTaskDefinitionRepository definitionRepository;
    private final CanvasTaskDefinitionService definitionService;
    private final TaskDataSourceReferenceIndexService indexService;

    public TaskDataSourceReferenceIndexInitializer(
            CanvasTaskDefinitionRepository definitionRepository,
            CanvasTaskDefinitionService definitionService,
            TaskDataSourceReferenceIndexService indexService
    ) {
        this.definitionRepository = definitionRepository;
        this.definitionService = definitionService;
        this.indexService = indexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        definitionRepository.findAll().forEach(definition -> {
            try {
                var canvas = definitionService.readIfCompatible(definition);
                if (canvas.isEmpty()) {
                    log.warn("跳过不兼容 Canvas 定义的数据源引用索引重建，taskId={}，schema={}.{}",
                            definition.getTaskId(),
                            definition.getSchemaVersion(),
                            definition.getSchemaMinorVersion());
                    return;
                }
                indexService.replaceCanvasReferences(
                        definition.getTaskId(),
                        definition.getVersion(),
                        canvas.orElseThrow()
                );
            } catch (RuntimeException exception) {
                log.error("重建任务 {} 的数据源引用索引失败", definition.getTaskId(), exception);
            }
        });
    }
}
