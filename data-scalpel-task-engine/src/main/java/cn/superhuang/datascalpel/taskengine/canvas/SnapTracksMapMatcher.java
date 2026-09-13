package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import net.sf.geographiclib.Geodesic;
import org.locationtech.jts.geom.Point;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Executor-side Viterbi matcher. Candidate transitions require the same line or one shared
 * network node, so sparse observations never silently fall back to independent nearest lines.
 */
final class SnapTracksMapMatcher implements Serializable {
    private static final double UNMATCHED_COST = 2.5d;
    private static final double RESTART_COST = 1.0d;
    private static final double EPSILON = 1e-12;

    private final SpatialDistanceMethod method;
    private final double searchDistanceMetres;
    private final double sourceUnitsPerMetre;

    SnapTracksMapMatcher(
            SpatialDistanceMethod method,
            double searchDistanceMetres,
            double sourceUnitsPerMetre
    ) {
        this.method = Objects.requireNonNull(method);
        this.searchDistanceMetres = searchDistanceMetres;
        this.sourceUnitsPerMetre = sourceUnitsPerMetre;
    }

    List<Choice> match(List<Observation> input) {
        List<Observation> observations = input.stream()
                .sorted(Comparator.comparingLong(Observation::order)).toList();
        if (observations.size() < 2) {
            return observations.stream().map(value -> new Choice(value.observationId(), null)).toList();
        }

        List<List<State>> states = new ArrayList<>(observations.size());
        for (Observation observation : observations) {
            List<State> row = new ArrayList<>();
            observation.candidates().stream()
                    .sorted(Comparator.comparing(Candidate::stableKey)
                            .thenComparingLong(Candidate::lineRowId))
                    .forEach(candidate -> row.add(new State(candidate)));
            row.add(new State(null));
            states.add(row);
        }

        double[][] costs = new double[states.size()][];
        int[][] previous = new int[states.size()][];
        costs[0] = new double[states.getFirst().size()];
        previous[0] = new int[states.getFirst().size()];
        for (int state = 0; state < states.getFirst().size(); state++) {
            Candidate candidate = states.getFirst().get(state).candidate();
            costs[0][state] = candidate == null ? UNMATCHED_COST : emission(candidate);
            previous[0][state] = -1;
        }

        for (int observationIndex = 1; observationIndex < observations.size(); observationIndex++) {
            List<State> currentStates = states.get(observationIndex);
            List<State> priorStates = states.get(observationIndex - 1);
            costs[observationIndex] = new double[currentStates.size()];
            previous[observationIndex] = new int[currentStates.size()];
            double observedDistance = observedDistance(
                    observations.get(observationIndex - 1).point(),
                    observations.get(observationIndex).point());
            for (int current = 0; current < currentStates.size(); current++) {
                Candidate currentCandidate = currentStates.get(current).candidate();
                double best = Double.POSITIVE_INFINITY;
                int bestPrevious = -1;
                for (int prior = 0; prior < priorStates.size(); prior++) {
                    if (!Double.isFinite(costs[observationIndex - 1][prior])) continue;
                    Candidate priorCandidate = priorStates.get(prior).candidate();
                    double transition = transition(priorCandidate, currentCandidate, observedDistance);
                    if (!Double.isFinite(transition)) continue;
                    double score = costs[observationIndex - 1][prior] + transition;
                    if (score < best - EPSILON) {
                        best = score;
                        bestPrevious = prior;
                    }
                }
                costs[observationIndex][current] = best;
                previous[observationIndex][current] = bestPrevious;
            }
        }

        int selected = 0;
        double best = costs[costs.length - 1][0];
        for (int state = 1; state < costs[costs.length - 1].length; state++) {
            if (costs[costs.length - 1][state] < best - EPSILON) {
                best = costs[costs.length - 1][state];
                selected = state;
            }
        }
        List<Choice> reversed = new ArrayList<>(observations.size());
        for (int index = observations.size() - 1; index >= 0; index--) {
            Candidate candidate = states.get(index).get(selected).candidate();
            reversed.add(new Choice(observations.get(index).observationId(),
                    candidate == null ? null : candidate.lineRowId()));
            selected = previous[index][selected];
            if (index > 0 && selected < 0) {
                throw new IllegalArgumentException("SNAP_TRACKS_MATCH_STATE_INVALID");
            }
        }
        List<Choice> result = new ArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) result.add(reversed.get(index));
        return result;
    }

    private double emission(Candidate candidate) {
        double normalized = candidate.distanceMetres() / searchDistanceMetres;
        return 0.5d * normalized * normalized;
    }

    private double transition(Candidate previous, Candidate current, double observedDistance) {
        if (previous == null && current == null) return UNMATCHED_COST;
        if (current == null) return UNMATCHED_COST;
        if (previous == null) return RESTART_COST + emission(current);
        double networkDistance = networkDistance(previous, current);
        if (!Double.isFinite(networkDistance)) return Double.POSITIVE_INFINITY;
        double scale = Math.max(searchDistanceMetres, Math.max(1d, observedDistance));
        return emission(current) + Math.abs(networkDistance - observedDistance) / scale;
    }

    private static double networkDistance(Candidate previous, Candidate current) {
        if (previous.lineRowId() == current.lineRowId()) {
            double delta = current.fraction() - previous.fraction();
            if (!allows(previous.direction(), delta) || !allows(current.direction(), delta)) {
                return Double.POSITIVE_INFINITY;
            }
            return Math.abs(delta) * previous.lineLengthMetres();
        }
        double best = Double.POSITIVE_INFINITY;
        for (Endpoint exit : exits(previous)) {
            for (Endpoint entry : entries(current)) {
                if (Objects.equals(exit.node(), entry.node())) {
                    best = Math.min(best, exit.distanceMetres() + entry.distanceMetres());
                }
            }
        }
        return best;
    }

    private double observedDistance(Point first, Point second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) return 0d;
        if (method == SpatialDistanceMethod.GEODESIC) {
            return Geodesic.WGS84.Inverse(first.getY(), first.getX(), second.getY(), second.getX()).s12;
        }
        return first.distance(second) / sourceUnitsPerMetre;
    }

    private static boolean allows(Direction direction, double delta) {
        if (Math.abs(delta) <= EPSILON) return direction != Direction.NONE;
        return delta > 0d ? direction.forward() : direction.backward();
    }

    private static List<Endpoint> exits(Candidate candidate) {
        List<Endpoint> result = new ArrayList<>(2);
        if (candidate.direction().backward()) {
            result.add(new Endpoint(candidate.fromNode(),
                    candidate.fraction() * candidate.lineLengthMetres()));
        }
        if (candidate.direction().forward()) {
            result.add(new Endpoint(candidate.toNode(),
                    (1d - candidate.fraction()) * candidate.lineLengthMetres()));
        }
        return result;
    }

    private static List<Endpoint> entries(Candidate candidate) {
        List<Endpoint> result = new ArrayList<>(2);
        if (candidate.direction().forward()) {
            result.add(new Endpoint(candidate.fromNode(),
                    candidate.fraction() * candidate.lineLengthMetres()));
        }
        if (candidate.direction().backward()) {
            result.add(new Endpoint(candidate.toNode(),
                    (1d - candidate.fraction()) * candidate.lineLengthMetres()));
        }
        return result;
    }

    enum Direction {
        FORWARD,
        BACKWARD,
        BOTH,
        NONE;

        boolean forward() {
            return this == FORWARD || this == BOTH;
        }

        boolean backward() {
            return this == BACKWARD || this == BOTH;
        }
    }

    record Candidate(
            long lineRowId,
            String stableKey,
            Object fromNode,
            Object toNode,
            Direction direction,
            double fraction,
            double lineLengthMetres,
            double distanceMetres
    ) implements Serializable {
    }

    record Observation(
            long observationId,
            long order,
            Point point,
            List<Candidate> candidates
    ) implements Serializable {
        Observation {
            candidates = List.copyOf(candidates);
        }
    }

    record Choice(long observationId, Long lineRowId) implements Serializable {
    }

    private record State(Candidate candidate) {
    }

    private record Endpoint(Object node, double distanceMetres) {
    }
}
