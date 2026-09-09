package cn.superhuang.datascalpel.taskengine.canvas;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** HDBSCAN condensed hierarchy and excess-of-mass extraction from an exact mutual-reachability MST.
 * No coordinates, Spark actions or Driver collection. The caller must construct the actual MST;
 * an arbitrary tree, KNN forest or repeated DBSCAN output is not an equivalent input.
 */
final class HdbscanHierarchy {
    static final int MAX_VERTICES = 1_000_000;
    private HdbscanHierarchy() { }

    record Edge(long first, long second, double distance) { }
    record Assignment(long vertex, Long cluster, double probability, double outlier,
                      boolean exemplar, Double stability) { }
    record Result(List<Assignment> assignments, int selectedClusters) {
        Result { assignments = List.copyOf(assignments); }
    }

    static Result extract(List<Long> vertices, List<Edge> edges, int minimumFeatures) {
        if (minimumFeatures < 2 || minimumFeatures > 100_000) throw failure("INVALID_SPATIAL_CLUSTER_MINIMUM_FEATURES");
        if (vertices == null || edges == null) throw failure("SPATIAL_HDBSCAN_TREE_INVALID");
        int count = vertices.size();
        if (count > MAX_VERTICES) throw failure("SPATIAL_HDBSCAN_HIERARCHY_LIMIT_EXCEEDED");
        long[] ids = new long[count];
        var unique = new HashSet<Long>();
        for (int i = 0; i < count; i++) {
            Long id = vertices.get(i);
            if (id == null || !unique.add(id)) throw failure("SPATIAL_HDBSCAN_TREE_INVALID");
            ids[i] = id;
        }
        Arrays.sort(ids);
        if (edges.size() != Math.max(0, count - 1)) throw failure("SPATIAL_HDBSCAN_TREE_INVALID");
        var index = new HashMap<Long, Integer>();
        var hierarchy = new ArrayList<Branch>();
        for (int i = 0; i < count; i++) { index.put(ids[i], i); hierarchy.add(new Branch(i, 1, 0, new int[0])); }
        var sorted = new ArrayList<IndexedEdge>(edges.size());
        var treeCheck = new DisjointSet(count);
        double scale = Double.POSITIVE_INFINITY;
        for (Edge edge : edges) {
            if (edge == null || !Double.isFinite(edge.distance()) || edge.distance() < 0)
                throw failure("SPATIAL_HDBSCAN_TREE_INVALID");
            Integer a = index.get(edge.first()), b = index.get(edge.second());
            if (a == null || b == null || a.equals(b) || !treeCheck.join(a, b))
                throw failure("SPATIAL_HDBSCAN_TREE_INVALID");
            // Normalize signed zero so equal-weight topology is independent of its encoding.
            double distance = edge.distance() == 0 ? 0 : edge.distance();
            sorted.add(new IndexedEdge(a, b, distance));
            if (distance > 0) scale = Math.min(scale, distance);
        }
        if (count < minimumFeatures) return noise(ids);
        sorted.sort(Comparator.comparingDouble(IndexedEdge::distance));
        int root = buildHierarchy(hierarchy, sorted, count);
        var clusters = condense(hierarchy, root, count, minimumFeatures, scale);
        return extractAssignments(ids, clusters);
    }

    private static int buildHierarchy(List<Branch> hierarchy, List<IndexedEdge> edges, int count) {
        var components = new DisjointSet(count);
        int[] branchOf = new int[count];
        for (int i = 0; i < count; i++) branchOf[i] = i;
        for (int start = 0; start < edges.size();) {
            int end = start + 1;
            while (end < edges.size() && edges.get(end).distance() == edges.get(start).distance()) end++;
            // All edges at one distance are merged simultaneously. Artificial binary merges
            // at tied heights must not create zero-lifetime clusters or depend on MST edge order.
            var touched = new HashMap<Integer, Integer>();
            for (int i = start; i < end; i++) {
                var edge = edges.get(i);
                int a = components.find(edge.a()), b = components.find(edge.b());
                touched.putIfAbsent(a, touched.size()); touched.putIfAbsent(b, touched.size());
            }
            var batch = new DisjointSet(touched.size());
            for (int i = start; i < end; i++) {
                var edge = edges.get(i);
                batch.join(touched.get(components.find(edge.a())), touched.get(components.find(edge.b())));
            }
            Map<Integer, List<Integer>> groups = new HashMap<>();
            touched.forEach((component, local) -> groups.computeIfAbsent(batch.find(local), ignored -> new ArrayList<>()).add(component));
            var orderedGroups = new ArrayList<>(groups.values());
            orderedGroups.forEach(group -> group.sort(Comparator.comparingInt(component -> hierarchy.get(branchOf[component]).firstVertex())));
            orderedGroups.sort(Comparator.comparingInt(group -> hierarchy.get(branchOf[group.getFirst()]).firstVertex()));
            for (var group : orderedGroups) {
                int[] children = group.stream().mapToInt(component -> branchOf[component]).toArray();
                int size = Arrays.stream(children).map(child -> hierarchy.get(child).size()).sum();
                int first = hierarchy.get(children[0]).firstVertex();
                int branch = hierarchy.size();
                hierarchy.add(new Branch(first, size, edges.get(start).distance(), children));
                int component = group.getFirst();
                for (int other : group) components.join(component, other);
                branchOf[components.find(component)] = branch;
            }
            start = end;
        }
        return branchOf[components.find(0)];
    }

    private static Condensed condense(List<Branch> hierarchy, int root, int count, int minimum, double scale) {
        var clusters = new ArrayList<Cluster>();
        clusters.add(new Cluster(-1, 0, count));
        int[] pointParent = new int[count];
        Arrays.fill(pointParent, -1);
        double[] pointExit = new double[count];
        var queue = new ArrayDeque<Work>();
        queue.add(new Work(root, 0));
        while (!queue.isEmpty()) {
            Work work = queue.removeFirst();
            Branch branch = hierarchy.get(work.branch());
            Cluster cluster = clusters.get(work.cluster());
            double lambda = lambda(branch.distance(), scale);
            if (lambda < cluster.birth) throw failure("SPATIAL_HDBSCAN_TREE_INVALID");
            int largeCount = 0;
            for (int child : branch.children()) if (hierarchy.get(child).size() >= minimum) largeCount++;
            for (int child : branch.children()) {
                Branch childBranch = hierarchy.get(child);
                boolean large = childBranch.size() >= minimum;
                if (large && largeCount == 1) {
                    queue.addLast(new Work(child, work.cluster()));
                } else {
                    cluster.addDeparture(lambda, childBranch.size());
                    if (large) {
                        int childCluster = clusters.size();
                        clusters.add(new Cluster(work.cluster(), lambda, childBranch.size()));
                        cluster.children.add(childCluster);
                        queue.addLast(new Work(child, childCluster));
                    } else {
                        var leaves = new ArrayDeque<Integer>();
                        leaves.add(child);
                        while (!leaves.isEmpty()) {
                            int item = leaves.removeLast();
                            if (item < count) { pointParent[item] = work.cluster(); pointExit[item] = lambda; }
                            else for (int descendant : hierarchy.get(item).children()) leaves.add(descendant);
                        }
                    }
                }
            }
        }
        for (int parent : pointParent) if (parent < 0) throw failure("SPATIAL_HDBSCAN_TREE_INVALID");
        return new Condensed(clusters, pointParent, pointExit);
    }

    private static Result extractAssignments(long[] ids, Condensed tree) {
        var clusters = tree.clusters();
        boolean[] choose = new boolean[clusters.size()];
        double[] best = new double[clusters.size()];
        double[] descendantsDeath = new double[clusters.size()];
        double globalDeath = 0;
        for (int i = clusters.size() - 1; i >= 0; i--) {
            Cluster c = clusters.get(i);
            double childrenStability = 0;
            double maximum = c.death;
            for (int child : c.children) {
                childrenStability += best[child];
                maximum = Math.max(maximum, descendantsDeath[child]);
            }
            descendantsDeath[i] = maximum;
            globalDeath = Math.max(globalDeath, c.death);
            // Root is never selected. EOM prefers the parent at an exact stability tie.
            choose[i] = i != 0 && c.stability >= childrenStability;
            best[i] = choose[i] ? c.stability : childrenStability;
        }
        int[] selectedAncestor = new int[clusters.size()];
        Arrays.fill(selectedAncestor, -1);
        int number = 0;
        for (int i = 1; i < clusters.size(); i++) {
            int inherited = selectedAncestor[clusters.get(i).parent];
            if (inherited >= 0) selectedAncestor[i] = inherited;
            else if (choose[i]) { selectedAncestor[i] = i; number++; }
        }
        // Use the smallest internal point identity as the cluster label, not input order.
        long[] labels = new long[clusters.size()];
        Arrays.fill(labels, Long.MAX_VALUE);
        int[] infiniteMembers = new int[clusters.size()];
        for (int i = 0; i < ids.length; i++) {
            int selected = selectedAncestor[tree.pointParent()[i]];
            if (selected >= 0) {
                labels[selected] = Math.min(labels[selected], ids[i]);
                if (Double.isInfinite(tree.pointExit()[i])) infiniteMembers[selected]++;
            }
        }
        var assignments = new ArrayList<Assignment>(ids.length);
        for (int i = 0; i < ids.length; i++) {
            int parent = tree.pointParent()[i];
            int selected = selectedAncestor[parent];
            double exit = tree.pointExit()[i];
            double outlier = 1 - membership(exit, descendantsDeath[parent]);
            if (selected < 0) assignments.add(new Assignment(ids[i], null, 0, outlier, false, null));
            else {
                Cluster c = clusters.get(selected);
                // With coincident points, use the limit as a common finite lambda cap grows.
                // This does not report persistence=1 for every finite-density cluster merely
                // because some unrelated duplicate group has infinite density.
                double stability = Double.isInfinite(globalDeath)
                        ? infiniteMembers[selected] / (double) c.size
                        : c.stability / c.size / globalDeath;
                boolean exemplar = clusters.get(parent).children.isEmpty() && exit == clusters.get(parent).death;
                assignments.add(new Assignment(ids[i], labels[selected], membership(exit, c.death), outlier,
                        exemplar, bounded(stability)));
            }
        }
        return new Result(assignments, number);
    }

    private static double membership(double exit, double death) {
        if (death == 0 || Double.isInfinite(exit)) return 1;
        return bounded(Math.min(exit, death) / death);
    }

    private static double bounded(double value) {
        if (!Double.isFinite(value) || value < -1e-12 || value > 1 + 1e-12)
            throw failure("SPATIAL_HDBSCAN_NUMERIC_RANGE_INVALID");
        return Math.max(0, Math.min(1, value));
    }

    private static double lambda(double distance, double scale) {
        if (distance == 0) return Double.POSITIVE_INFINITY;
        // Scaling all finite lambdas by the smallest positive edge avoids reciprocal and
        // stability-sum overflow without changing EOM or normalized diagnostics.
        double value = scale / distance;
        if (!Double.isFinite(value) || value <= 0) throw failure("SPATIAL_HDBSCAN_NUMERIC_RANGE_INVALID");
        return value;
    }

    private static Result noise(long[] ids) {
        var rows = new ArrayList<Assignment>(ids.length);
        for (long id : ids) rows.add(new Assignment(id, null, 0, 0, false, null));
        return new Result(rows, 0);
    }

    private record IndexedEdge(int a, int b, double distance) { }
    private record Branch(int firstVertex, int size, double distance, int[] children) { }
    private record Work(int branch, int cluster) { }
    private record Condensed(List<Cluster> clusters, int[] pointParent, double[] pointExit) { }

    private static final class Cluster {
        final int parent, size;
        final double birth;
        final List<Integer> children = new ArrayList<>();
        double death, stability, correction;
        Cluster(int parent, double birth, int size) { this.parent = parent; this.birth = birth; this.size = size; }
        void addDeparture(double lambda, int size) {
            death = Math.max(death, lambda);
            if (Double.isInfinite(lambda)) { stability = Double.POSITIVE_INFINITY; return; }
            if (Double.isInfinite(stability)) return;
            double contribution = (lambda - birth) * size - correction;
            double sum = stability + contribution;
            correction = (sum - stability) - contribution;
            stability = sum;
        }
    }

    private static final class DisjointSet {
        final int[] parent, rank;
        DisjointSet(int size) {
            parent = new int[size]; rank = new int[size];
            for (int i = 0; i < size; i++) parent[i] = i;
        }
        int find(int value) {
            int root = value;
            while (parent[root] != root) root = parent[root];
            while (parent[value] != value) { int next = parent[value]; parent[value] = root; value = next; }
            return root;
        }
        boolean join(int a, int b) {
            a = find(a); b = find(b);
            if (a == b) return false;
            if (rank[a] < rank[b]) { int temp = a; a = b; b = temp; }
            parent[b] = a;
            if (rank[a] == rank[b]) rank[a]++;
            return true;
        }
    }

    private static IllegalArgumentException failure(String code) { return new IllegalArgumentException(code); }
}
