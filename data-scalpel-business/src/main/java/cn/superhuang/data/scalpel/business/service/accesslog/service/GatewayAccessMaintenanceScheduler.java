package cn.superhuang.data.scalpel.business.service.accesslog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "data-scalpel.gateway-access",
        name = "enabled",
        havingValue = "true"
)
public class GatewayAccessMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(GatewayAccessMaintenanceScheduler.class);

    private final GatewayAccessArchiveService archiveService;

    public GatewayAccessMaintenanceScheduler(GatewayAccessArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @Scheduled(
            cron = "${data-scalpel.gateway-access.archive-cron:0 5 * * * *}",
            zone = "UTC"
    )
    public void archiveClosedHours() {
        try {
            int archived = archiveService.archiveClosedHours();
            if (archived > 0) {
                log.info("已完成 {} 个网关访问日志小时归档", archived);
            }
        } catch (RuntimeException exception) {
            log.warn("网关访问日志小时归档调度失败，下轮将自动重试", exception);
        }
    }

    @Scheduled(
            cron = "${data-scalpel.gateway-access.raw-cleanup-cron:0 25 * * * *}",
            zone = "UTC"
    )
    public void cleanExpiredRawLogs() {
        try {
            int deleted = archiveService.cleanExpiredRawLogs();
            if (deleted > 0) {
                log.info("已清理 {} 条到期网关访问明细", deleted);
            }
        } catch (RuntimeException exception) {
            log.warn("网关访问明细清理失败，下轮将自动重试", exception);
        }
    }

    @Scheduled(
            cron = "${data-scalpel.gateway-access.hourly-cleanup-cron:0 35 2 * * *}",
            zone = "UTC"
    )
    public void cleanExpiredHourlyStatistics() {
        try {
            int deleted = archiveService.cleanExpiredHourlyStatistics();
            if (deleted > 0) {
                log.info("已清理 {} 条到期网关访问小时统计及状态", deleted);
            }
        } catch (RuntimeException exception) {
            log.warn("网关访问小时统计清理失败，下轮将自动重试", exception);
        }
    }
}
