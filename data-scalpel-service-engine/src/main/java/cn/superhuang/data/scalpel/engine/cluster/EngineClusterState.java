package cn.superhuang.data.scalpel.engine.cluster;

import jakarta.persistence.*;

/** One logical Engine group per runtime database, including its embedded API Studio tables. */
@Entity
@Table(name = "ds_engine_cluster_state")
class EngineClusterState {
    @Id @Column(length = 32) private String id;
    @Column(name = "engine_code", nullable = false, length = 64) private String engineCode;
    @Column(name = "studio_service", nullable = false, length = 128) private String studioService;
    @Column(nullable = false) private long revision;
    protected EngineClusterState() {}
}
