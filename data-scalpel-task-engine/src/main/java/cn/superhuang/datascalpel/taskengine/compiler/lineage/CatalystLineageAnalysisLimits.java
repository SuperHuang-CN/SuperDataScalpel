package cn.superhuang.datascalpel.taskengine.compiler.lineage;

import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import scala.jdk.javaapi.CollectionConverters;

import java.util.ArrayDeque;

/** Bounds best-effort plan inspection without executing the plan. */
public record CatalystLineageAnalysisLimits(
        int maximumPlanNodes,
        int maximumPlanDepth,
        int maximumExpressionNodes
) {
    public CatalystLineageAnalysisLimits {
        if (maximumPlanNodes < 1 || maximumPlanDepth < 1 || maximumExpressionNodes < 1) {
            throw new IllegalArgumentException("Catalyst lineage limits must be positive");
        }
    }

    public static CatalystLineageAnalysisLimits canvasDefaults() {
        return new CatalystLineageAnalysisLimits(
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public static CatalystLineageAnalysisLimits jarRuntimeDefaults() {
        return new CatalystLineageAnalysisLimits(2_000, 200, 20_000);
    }

    void validate(LogicalPlan root) {
        int planNodes = 0;
        int expressionNodes = 0;
        ArrayDeque<PlanAtDepth> plans = new ArrayDeque<>();
        plans.add(new PlanAtDepth(root, 1));
        while (!plans.isEmpty()) {
            PlanAtDepth item = plans.removeFirst();
            if (++planNodes > maximumPlanNodes || item.depth() > maximumPlanDepth) {
                throw new CatalystLineageLimitExceededException();
            }
            for (LogicalPlan child : CollectionConverters.asJava(item.plan().children())) {
                plans.addLast(new PlanAtDepth(child, item.depth() + 1));
            }
            ArrayDeque<Expression> expressions = new ArrayDeque<>(
                    CollectionConverters.asJava(item.plan().expressions()));
            while (!expressions.isEmpty()) {
                Expression expression = expressions.removeFirst();
                if (++expressionNodes > maximumExpressionNodes) {
                    throw new CatalystLineageLimitExceededException();
                }
                expressions.addAll(CollectionConverters.asJava(expression.children()));
            }
        }
    }

    private record PlanAtDepth(LogicalPlan plan, int depth) {
    }
}

final class CatalystLineageLimitExceededException extends RuntimeException {
}
