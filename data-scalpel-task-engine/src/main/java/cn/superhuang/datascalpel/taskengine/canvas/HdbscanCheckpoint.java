package cn.superhuang.datascalpel.taskengine.canvas;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.execution.LogicalRDD;
import org.apache.spark.sql.execution.datasources.LogicalRelation;
import org.apache.spark.sql.execution.datasources.HadoopFsRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.rdd.RDD;
import org.apache.spark.rdd.ReliableRDDCheckpointData;
import org.apache.spark.rdd.ReliableRDDCheckpointData$;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.LoggerFactory;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayDeque;

/** Runtime-only boundary for iterative HDBSCAN tables. SQL checkpoint alone copies estimated
 * join statistics into its LogicalRDD; repeated joins can exponentiate those BigIntegers even
 * for a tiny actual tree. Rebinding the checkpoint's distributed rows drops that inherited
 * estimate without inventing a cardinality, collecting data, or changing session-wide rules.
 */
final class HdbscanCheckpoint {
    private HdbscanCheckpoint() { }

    /** Explicit node/stage ownership, never a directory scan or SparkContext-wide setting.
     * Register Spark's allocated path before materialization so a failed write is also owned.
     * A kept final snapshot has severed dependencies on every disposable intermediate.
     */
    static final class Scope implements AutoCloseable {
        // Retaining every Dataset would retain old analyzed plans throughout a deep tree.
        // Only identity lookup for the few returned results is needed; keep weak references.
        private final ArrayList<Binding> datasets = new ArrayList<>();
        private final LinkedHashMap<Path, Configuration> owned = new LinkedHashMap<>();
        private final Set<Path> kept = new HashSet<>();
        private boolean closed;

        Dataset<Row> checkpoint(Dataset<Row> rows) {
            ensureOpen();
            // Same reliable RDD/checkpoint action as Dataset.checkpoint(true), split only to
            // capture ownership before doCheckpoint can fail. No count/collect is added.
            var snapshot = rows.checkpoint(false);
            if (!(snapshot.queryExecution().logical() instanceof LogicalRDD logical)
                    || logical.rdd().checkpointData().isEmpty()
                    || !(logical.rdd().checkpointData().get() instanceof ReliableRDDCheckpointData<?>))
                throw new IllegalStateException("HDBSCAN reliable checkpoint ownership unavailable");
            // Spark exposes getCheckpointDir only after success; use its own allocator for
            // this newly created RDD, not a guessed filename or a scan of shared storage.
            var allocated = ReliableRDDCheckpointData$.MODULE$.checkpointPath(logical.rdd().context(), logical.rdd().id());
            if (allocated.isEmpty()) throw new IllegalStateException("HDBSCAN checkpoint directory unavailable");
            Path path = allocated.get();
            owned.put(path, rows.sparkSession().sparkContext().hadoopConfiguration());
            logical.rdd().doCheckpoint();
            datasets.add(new Binding(new WeakReference<>(snapshot), path));
            return snapshot;
        }

        Dataset<Row> bind(Dataset<Row> rows) {
            var snapshot = checkpoint(rows);
            var result = rows.sparkSession().createDataFrame(snapshot.javaRDD(), snapshot.schema());
            datasets.add(new Binding(new WeakReference<>(result), pathOf(snapshot)));
            return result;
        }

        Dataset<Row> keep(Dataset<Row> result) {
            ensureOpen();
            Path path = pathOf(result);
            if (path == null) throw new IllegalArgumentException("HDBSCAN result must be an owned checkpoint");
            kept.add(path);
            return result;
        }

        /** The bundled GraphFrames implementation returns plans backed by its own Parquet
         * iteration directory (or reliable RDD checkpoints with AQE). Adopt only storage
         * actually referenced by that successful result, excluding every input dependency.
         * Never infer ownership from a before/after listing of the shared checkpoint root.
         */
        void trackGraphResult(Dataset<Row> result, Dataset<Row> vertices, Dataset<Row> edges) {
            ensureOpen();
            var context = result.sparkSession().sparkContext();
            if (context.getCheckpointDir().isEmpty()) return;
            Path root = new Path(context.getCheckpointDir().get());
            var inputs = storageRoots(vertices); inputs.addAll(storageRoots(edges));
            for (Path path : storageRoots(result)) {
                if (inputs.contains(path) || owned.containsKey(path)) continue;
                Path direct = directPath(path, root);
                if (!root.equals(direct.getParent())) continue;
                boolean graphDirectory = direct.getName().matches("connected-components-[0-9a-f]{8}");
                boolean reliableRdd = path.equals(direct) && direct.getName().matches("rdd-[0-9]+");
                if (!graphDirectory && !reliableRdd) continue;
                // A foreign input below the same directory rules out ownership of its parent.
                if (inputs.stream().anyMatch(input -> isWithin(input, direct))) continue;
                owned.put(direct, context.hadoopConfiguration());
            }
        }

        private static Path directPath(Path path, Path root) {
            while (path.getParent() != null && !path.getParent().equals(root)) path = path.getParent();
            return path;
        }

        private static boolean isWithin(Path path, Path parent) {
            for (Path current = path; current != null; current = current.getParent()) if (parent.equals(current)) return true;
            return false;
        }

        private static Set<Path> storageRoots(Dataset<Row> rows) {
            var paths = new HashSet<Path>();
            // A freshly read Dataset can still expose UnresolvedDataSource in logical(),
            // while a projection over it already contains LogicalRelation. Compare both
            // sides after Analyzer resolution or a foreign input could appear newly owned.
            var plans = new ArrayDeque<LogicalPlan>(); plans.add(rows.queryExecution().analyzed());
            var seenPlans = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<LogicalPlan, Boolean>());
            var rdds = new ArrayDeque<RDD<?>>(); var seenRdds = new HashSet<Integer>();
            while (!plans.isEmpty()) {
                var plan = plans.removeLast();
                if (!seenPlans.add(plan)) continue;
                if (plan instanceof LogicalRDD logical) rdds.add(logical.rdd());
                if (plan instanceof LogicalRelation relation && relation.relation() instanceof HadoopFsRelation files) {
                    var roots = files.location().rootPaths().iterator();
                    while (roots.hasNext()) paths.add(roots.next());
                }
                var children = plan.children().iterator(); while (children.hasNext()) plans.add(children.next());
            }
            while (!rdds.isEmpty()) {
                var rdd = rdds.removeLast(); if (!seenRdds.add(rdd.id())) continue;
                var file = rdd.getCheckpointFile();
                if (file.isDefined()) { paths.add(new Path(file.get())); continue; }
                var dependencies = rdd.dependencies().iterator(); while (dependencies.hasNext()) rdds.add(dependencies.next().rdd());
            }
            return paths;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            int failures = 0;
            for (var entry : owned.entrySet()) {
                if (kept.contains(entry.getKey())) continue;
                try {
                    var fs = entry.getKey().getFileSystem(entry.getValue());
                    if (!fs.delete(entry.getKey(), true) && fs.exists(entry.getKey())) failures++;
                } catch (Exception ignored) {
                    // Best effort: preserve the primary computation failure; never log paths
                    // or a storage exception that may contain credentials/connection details.
                    failures++;
                }
            }
            if (failures > 0) LoggerFactory.getLogger(HdbscanCheckpoint.class)
                    .warn("event=HDBSCAN_CHECKPOINT_CLEANUP_INCOMPLETE failedCheckpointCount={}", failures);
            datasets.clear(); owned.clear(); kept.clear();
        }

        private void ensureOpen() {
            if (closed) throw new IllegalStateException("HDBSCAN checkpoint scope is closed");
        }

        private Path pathOf(Dataset<Row> rows) {
            for (int i = datasets.size() - 1; i >= 0; i--) {
                var binding = datasets.get(i);
                if (binding.dataset().get() == rows) return binding.path();
            }
            return null;
        }

        private record Binding(WeakReference<Dataset<Row>> dataset, Path path) { }
    }
}
