package cn.superhuang.data.scalpel.business.model.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/** Reads only management metadata; known output runs revoke cached images before writing begins. */
@Repository
public class ModelPreviewRevisionRepository {
    private final JdbcTemplate jdbc;
    public ModelPreviewRevisionRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Revision read(UUID modelId) {
        return jdbc.queryForObject("""
            WITH aliases AS (
              SELECT m.id FROM ds_data_model m JOIN ds_data_model original ON original.id=?
              WHERE m.storage_data_source_id=original.storage_data_source_id
                AND m.physical_table_name=original.physical_table_name
                AND m.schema_name IS NOT DISTINCT FROM original.schema_name
                AND m.catalog_name IS NOT DISTINCT FROM original.catalog_name
            ), writers AS (
              SELECT task_id FROM task_canvas_model_reference WHERE model_id IN (SELECT id FROM aliases) AND reference_role='OUTPUT'
              UNION SELECT task_id FROM task_local_sql_definition WHERE output_model_id IN (SELECT id FROM aliases)
              UNION SELECT task_id FROM task_spark_jar_resource_binding WHERE resource_id IN (SELECT id FROM aliases) AND resource_type='MODEL' AND access_mode IN ('WRITE','READ_WRITE')
            )
            SELECT coalesce(bool_or(r.status IN ('QUEUED','RUNNING','CANCEL_REQUESTED','STOP_REQUESTED')),false) AS updating,
                   coalesce(cast(max(r.queued_at) AS text),'') || ':' || coalesce(cast(max(r.ended_at) AS text),'') AS revision
            FROM task_run r JOIN writers w ON r.task_id=w.task_id
            """,(rs,n) -> new Revision(rs.getBoolean("updating"),rs.getString("revision")),modelId);
    }
    public record Revision(boolean updating,String value) { }
}
