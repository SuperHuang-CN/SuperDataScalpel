package cn.superhuang.data.scalpel.engine.cluster;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Control-plane only. Session advisory locks do not hold a database transaction over external I/O. */
@Component
@ConditionalOnProperty(name="data-scalpel.engine.cluster.enabled", matchIfMissing=true)
public class EngineClusterCoordinator {
    private static final long LOCK_KEY=0x4453454e47494e45L;
    private final DataSource dataSource;
    private final EngineClusterClock clock;
    private final EngineClusterProperties properties;
    private final ObjectProvider<EngineRuntimeReconciler> reconciler;
    private final ReentrantLock local=new ReentrantLock();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("engine-config-sync").factory());
    private final String instanceId=UUID.randomUUID().toString();
    private volatile long loaded=-1,target=-1,confirmedNanos;
    private volatile Instant lastConfirmedAt;
    private volatile boolean applying=true;
    private volatile String error;
    private volatile boolean started;
    public EngineClusterCoordinator(DataSource dataSource, EngineClusterClock clock, EngineClusterProperties properties,
                                    ObjectProvider<EngineRuntimeReconciler> reconciler) {
        this.dataSource=dataSource;this.clock=clock;this.properties=properties;this.reconciler=reconciler;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        started=true;
        tick();
        worker.scheduleWithFixedDelay(this::tick,properties.pollIntervalMs(),properties.pollIntervalMs(),TimeUnit.MILLISECONDS);
    }
    public boolean ready() {
        return started && !applying && error==null && loaded>=0 && loaded==target && confirmedNanos!=0
                && System.nanoTime()-confirmedNanos < TimeUnit.MILLISECONDS.toNanos(properties.maxStaleMs());
    }
    public Snapshot snapshot() { return new Snapshot(instanceId,ready(),loaded,target,lastConfirmedAt,error,properties.pollIntervalMs(),properties.maxStaleMs()); }
    public record Snapshot(String instanceId,boolean ready,long loadedRevision,long targetRevision,Instant lastConfirmedAt,
                           String error,long pollIntervalMs,long maxStaleMs) {}

    public <T> T command(Supplier<T> operation) {
        if (local.isHeldByCurrentThread()) return operation.get();
        if (!local.tryLock()) throw busy();
        T result = null;
        RuntimeException operationFailure = null;
        boolean lockBusy = false;
        try (var lock=databaseLock()) {
            if(lock==null) {
                lockBusy = true;
                throw busy();
            }
            applying=true;
            clock.read();clock.changed(); // durable invalidation survives a process dying halfway through a command
            try { result = operation.get(); }
            catch (RuntimeException e) { operationFailure = e; }
            finally {
                clock.changed();
                synchronize();
            }
        } catch(Exception e) {
            if (lockBusy && e instanceof ResponseStatusException response) throw response;
            if (operationFailure != null) e.addSuppressed(operationFailure);
            fail(e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Engine 配置结果尚未确认，请刷新节点状态后核对，不要假定已生效",e);
        }
        finally {local.unlock();}
        // Configuration is confirmed even if input validation or the business operation failed.
        // Preserve its original HTTP/error contract without falsely marking this node out of service.
        if (operationFailure != null) throw operationFailure;
        return result;
    }
    private void tick() {
        if(!local.tryLock())return;
        try {
            target=clock.read();
            if(target==loaded && !applying && error==null){confirmed();return;}
            applying=true;
            try(var lock=databaseLock()){if(lock!=null)synchronize();}
        }catch(Exception e){fail(e);}
        finally{local.unlock();}
    }
    private void synchronize() {
        applying=true;
        long before=clock.read();
        reconciler.getObject().reconcile();
        target=clock.read();loaded=before;
        error=null;applying=loaded!=target;
        if(!applying)confirmed();
    }
    private void confirmed(){confirmedNanos=System.nanoTime();lastConfirmedAt=Instant.now();}
    private void fail(Exception e) {
        if(error==null)LoggerFactory.getLogger(getClass()).error("Engine 配置同步失败，节点停止接收新业务请求",e);
        error="配置同步失败，请检查节点日志、数据库及服务依赖";applying=true;
    }
    private ResponseStatusException busy(){return new ResponseStatusException(HttpStatus.CONFLICT,"Engine 正在同步或处理另一项配置操作，请稍后重试");}
    private DatabaseLock databaseLock() throws Exception {
        Connection c=dataSource.getConnection();
        try {
            if(!"PostgreSQL".equals(c.getMetaData().getDatabaseProductName()))throw new IllegalStateException("Engine 集群协调只支持 PostgreSQL；隔离 H2 测试须显式关闭 cluster.enabled");
            try(var s=c.prepareStatement("select pg_try_advisory_lock(?)")) {
                s.setQueryTimeout(3);s.setLong(1,LOCK_KEY);
                try(var r=s.executeQuery()){r.next();if(r.getBoolean(1))return new DatabaseLock(c);}
            }
            c.close();return null;
        }catch(Exception e){c.close();throw e;}
    }
    private record DatabaseLock(Connection connection) implements AutoCloseable {
        @Override public void close() throws Exception {
            try(var s=connection.prepareStatement("select pg_advisory_unlock(?)")){s.setQueryTimeout(3);s.setLong(1,LOCK_KEY);s.execute();}
            catch(Exception e){connection.abort(Runnable::run);throw e;}
            finally{connection.close();}
        }
    }
    @PreDestroy public void stop(){started=false;applying=true;worker.shutdownNow();}
}
