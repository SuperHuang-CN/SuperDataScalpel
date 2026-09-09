package cn.superhuang.datascalpel.taskengine.canvas;

import java.util.ArrayList;
import java.util.List;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

/** Append-only HDBSCAN stage history. Binary carries keep only O(log batches) live plan
 * handles and rewrite each batch at most O(log batches) times, not once per later round.
 * These are distributed tables, not collected cluster/member lists. Checkpoint files belong
 * to the enclosing node/stage scope and are released after its final result is materialized.
 */
final class HdbscanRowHistory {
    private final Dataset<Row> empty;
    private final HdbscanCheckpoint.Scope scope;
    private final int partitions;
    private final List<Dataset<Row>> levels = new ArrayList<>();

    HdbscanRowHistory(Dataset<Row> empty, HdbscanCheckpoint.Scope scope) {
        this.scope = scope;
        this.empty = empty.limit(0);
        this.partitions = Integer.parseInt(empty.sparkSession().conf().get("spark.sql.shuffle.partitions"));
    }

    /** Caller skips known empty rounds. The returned batch is bound once for other consumers. */
    Dataset<Row> append(Dataset<Row> rows) {
        var batch = scope.bind(rows.coalesce(partitions));
        var carry = batch;
        int level = 0;
        while (level < levels.size() && levels.get(level) != null) {
            carry = scope.bind(levels.get(level).unionByName(carry).coalesce(partitions));
            levels.set(level, null);
            level++;
        }
        if (level == levels.size()) levels.add(carry);
        else levels.set(level, carry);
        return batch;
    }

    Dataset<Row> materialize() {
        Dataset<Row> result = null;
        // High-to-low is batch chronology; Spark row order itself remains unspecified.
        for (int level = levels.size() - 1; level >= 0; level--) {
            var batch = levels.get(level);
            if (batch != null) result = result == null ? batch : result.unionByName(batch);
        }
        return scope.bind((result == null ? empty : result).coalesce(partitions));
    }

    int retainedBatchCount() {
        return (int) levels.stream().filter(java.util.Objects::nonNull).count();
    }
}
