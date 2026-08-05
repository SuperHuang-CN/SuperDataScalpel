package cn.superhuang.superapigateway.runtime;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PostgresConfigurationNotificationListener {

    private static final Logger log =
            LoggerFactory.getLogger(PostgresConfigurationNotificationListener.class);

    private final DataSource dataSource;
    private final GatewayRuntimeCoordinator coordinator;
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread listenerThread;

    public PostgresConfigurationNotificationListener(
            DataSource dataSource,
            GatewayRuntimeCoordinator coordinator
    ) {
        this.dataSource = dataSource;
        this.coordinator = coordinator;
    }

    @PostConstruct
    void start() {
        running.set(true);
        listenerThread = Thread.ofPlatform()
                .name("gateway-postgres-notify")
                .daemon(true)
                .start(this::listenLoop);
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (listenerThread != null) listenerThread.interrupt();
    }

    private void listenLoop() {
        while (running.get()) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                connection.setAutoCommit(true);
                statement.execute("LISTEN super_api_gateway_config_changed");
                PGConnection pgConnection = connection.unwrap(PGConnection.class);
                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    var notifications = pgConnection.getNotifications(1000);
                    if (notifications != null && notifications.length > 0) {
                        coordinator.requestReload();
                    }
                }
            } catch (Exception exception) {
                if (!running.get()) return;
                log.warn("PostgreSQL configuration notification listener disconnected: {}",
                        exception.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
