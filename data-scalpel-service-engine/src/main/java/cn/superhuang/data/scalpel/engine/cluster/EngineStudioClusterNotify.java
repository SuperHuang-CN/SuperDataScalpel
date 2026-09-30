package cn.superhuang.data.scalpel.engine.cluster;

import cn.superhuang.superops.api.studio.extend.IClusterNotify;
import cn.superhuang.superops.api.studio.entity.vo.NotifyEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Reuses Studio's existing extension point; no Redis, payload broadcast or delivery dependency. */
@Component
@ConditionalOnProperty(name="data-scalpel.engine.cluster.enabled", matchIfMissing=true)
public class EngineStudioClusterNotify implements IClusterNotify {
    private final EngineClusterClock clock;
    public EngineStudioClusterNotify(EngineClusterClock clock) {this.clock=clock;}
    @Override public void sendNotify(NotifyEntity event) { clock.changed(); }
    @Override public void receiveNotify(NotifyEntity event) { /* Version polling is authoritative. */ }
}
