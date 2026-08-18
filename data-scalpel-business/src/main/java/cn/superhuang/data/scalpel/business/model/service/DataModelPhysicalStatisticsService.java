package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalStatistics;
import cn.superhuang.data.scalpel.business.model.repository.DataModelPhysicalStatisticsRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelPhysicalStatisticsResponse;
import cn.superhuang.data.scalpel.dialect.model.TablePhysicalStatistics;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class DataModelPhysicalStatisticsService {

    private static final Duration STATISTICS_TIMEOUT = Duration.ofSeconds(10);

    private final DataModelRepository modelRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DataModelPhysicalStatisticsRepository statisticsRepository;
    private final ModelPhysicalTablePort physicalTablePort;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public DataModelPhysicalStatisticsService(
            DataModelRepository modelRepository,
            DataSourceRepository dataSourceRepository,
            DataModelPhysicalStatisticsRepository statisticsRepository,
            ModelPhysicalTablePort physicalTablePort,
            PlatformTransactionManager transactionManager
    ) {
        this.modelRepository = modelRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.statisticsRepository = statisticsRepository;
        this.physicalTablePort = physicalTablePort;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    public DataModelPhysicalStatisticsResponse refresh(UUID modelId) {
        RefreshSnapshot snapshot = readTransaction.execute(status -> {
            DataModel model = modelRepository.findById(modelId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模型不存在"));
            return new RefreshSnapshot(
                    model,
                    dataSourceRepository.findById(model.getStorageDataSourceId()).orElse(null)
            );
        });
        if (snapshot == null) {
            throw new IllegalStateException("无法读取模型统计刷新快照");
        }

        Instant refreshedAt = Instant.now();
        if (snapshot.dataSource() == null) {
            return persist(modelId, statistics -> statistics.fail(refreshedAt, "关联的数据存储不存在"));
        }
        if (!snapshot.dataSource().isEnabled()) {
            return persist(modelId, statistics -> statistics.fail(refreshedAt, "关联的数据存储已停用"));
        }

        try {
            TablePhysicalStatistics collected = physicalTablePort.readStatistics(
                    snapshot.dataSource(), snapshot.model(), STATISTICS_TIMEOUT
            );
            return persist(modelId, statistics -> statistics.collect(collected, refreshedAt));
        } catch (DatabaseAccessException exception) {
            return persist(modelId, statistics -> statistics.fail(refreshedAt, exception.getMessage()));
        } catch (UnsupportedOperationException | IllegalArgumentException exception) {
            return persist(modelId, statistics -> statistics.collect(
                    TablePhysicalStatistics.unsupported(exception.getMessage()), refreshedAt
            ));
        }
    }

    private DataModelPhysicalStatisticsResponse persist(
            UUID modelId,
            Consumer<DataModelPhysicalStatistics> updater
    ) {
        DataModelPhysicalStatisticsResponse response = writeTransaction.execute(status -> {
            if (!modelRepository.existsById(modelId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模型不存在");
            }
            DataModelPhysicalStatistics statistics = statisticsRepository.findByModelId(modelId)
                    .orElseGet(() -> DataModelPhysicalStatistics.create(modelId));
            updater.accept(statistics);
            return DataModelPhysicalStatisticsResponse.from(statisticsRepository.saveAndFlush(statistics));
        });
        if (response == null) {
            throw new IllegalStateException("无法保存模型物理统计");
        }
        return response;
    }

    private record RefreshSnapshot(DataModel model, DataSource dataSource) {
    }
}
