package cn.superhuang.data.scalpel.engine.cluster;

import cn.superhuang.data.scalpel.engine.config.EngineProperties;
import cn.superhuang.superops.api.studio.config.SuperApiStudioProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="data-scalpel.engine.cluster.enabled", matchIfMissing=true)
public class EngineClusterClock {
    private final JdbcTemplate jdbc;
    private final EngineProperties engine;
    private final SuperApiStudioProperties studio;
    public EngineClusterClock(JdbcTemplate jdbc, EngineProperties engine, SuperApiStudioProperties studio) {
        this.jdbc=new JdbcTemplate(java.util.Objects.requireNonNull(jdbc.getDataSource()));
        this.jdbc.setQueryTimeout(3);this.engine=engine;this.studio=studio;
    }
    public long read() {
        if(studio.getClusterType()!=cn.superhuang.superops.api.studio.entity.vo.ClusterType.None)
            throw new IllegalStateException("Engine 使用 PostgreSQL 协调，API Studio cluster-type 必须为 None，无需 Redis");
        var rows=jdbc.query("select engine_code, studio_service, revision from ds_engine_cluster_state where id='runtime'", (rs,n)-> {
            if (!engine.code().equals(rs.getString(1)) || !studio.getServiceName().equals(rs.getString(2)))
                throw new IllegalStateException("同一 Engine 运行库必须使用相同 engine.code 和 API Studio service-name；不同 Engine 组必须分库");
            return rs.getLong(3);
        });
        if (rows.isEmpty()) {
            jdbc.update("insert into ds_engine_cluster_state(id,engine_code,studio_service,revision) values ('runtime',?,?,0) on conflict (id) do nothing",engine.code(),studio.getServiceName());
            return read();
        }
        return rows.getFirst();
    }
    /** JdbcTemplate participates in the publisher's transaction, so rollback cannot publish a revision. */
    public void changed() {
        jdbc.update("update ds_engine_cluster_state set revision=revision+1 where id='runtime'");
    }
}
