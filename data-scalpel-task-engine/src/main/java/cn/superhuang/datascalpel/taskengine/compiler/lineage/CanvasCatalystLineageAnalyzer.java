package cn.superhuang.datascalpel.taskengine.compiler.lineage;

import cn.superhuang.data.scalpel.contract.task.CanvasLineageCompilation;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasLineageOutputCandidate;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.AttributeReference;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.WindowExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.CTERelationDef;
import org.apache.spark.sql.catalyst.plans.logical.CTERelationRef;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Generate;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.plans.logical.Sort;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.apache.spark.sql.catalyst.plans.logical.WithCTE;
import org.apache.spark.sql.catalyst.plans.logical.Window;
import org.apache.spark.sql.types.Metadata;
import scala.jdk.javaapi.CollectionConverters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads compiler-only identity markers from an analyzed Catalyst plan and emits
 * Spark-free static lineage evidence. This analyzer never executes a Spark action.
 */
public final class CanvasCatalystLineageAnalyzer {
    private static final Set<String> TRANSPARENT_PLAN_TYPES = Set.of(
            "SubqueryAlias", "GlobalLimit", "LocalLimit", "Repartition", "ResolvedHint"
    );

    public CanvasLineageCompilation analyze(List<CanvasLineageOutputCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return CanvasLineageCompilation.unavailable(List.of(
                    warning("NO_OUTPUT_FLOW", "Canvas 中没有可分析的输出节点", null, null, null)
            ));
        }
        List<CanvasLineageCompilation.Flow> flows = new ArrayList<>();
        List<CanvasLineageCompilation.Warning> warnings = new ArrayList<>();
        for (CanvasLineageOutputCandidate candidate : candidates) {
            try {
                CanvasLineageCompilation.Flow flow = analyzeFlow(candidate);
                flows.add(flow);
                warnings.addAll(flow.warnings());
            } catch (RuntimeException exception) {
                String flowKey = flowKey(candidate);
                CanvasLineageCompilation.Warning warning = warning(
                        "CATALYST_LINEAGE_UNAVAILABLE",
                        "Catalyst 计划可执行，但当前分析器无法完整解释该输出链路",
                        candidate.node().id(), flowKey, null);
                warnings.add(warning);
                flows.add(new CanvasLineageCompilation.Flow(
                        flowKey, candidate.node().id(), candidate.node().nodeType(),
                        CanvasLineageCompilation.Coverage.MODEL_ONLY,
                        candidate.asset(), fallbackInputAssets(candidate),
                        List.of(), List.of(), List.of(), List.of(warning)
                ));
            }
        }
        CanvasLineageCompilation.Coverage coverage = flows.stream()
                .map(CanvasLineageCompilation.Flow::coverage)
                .min(Comparator.comparingInt(CanvasCatalystLineageAnalyzer::coverageRank))
                .orElse(CanvasLineageCompilation.Coverage.MODEL_ONLY);
        CanvasLineageCompilation.AnalysisStatus status = coverage == CanvasLineageCompilation.Coverage.FIELD_COMPLETE
                ? CanvasLineageCompilation.AnalysisStatus.COMPLETE
                : CanvasLineageCompilation.AnalysisStatus.PARTIAL;
        return new CanvasLineageCompilation(status, coverage, flows, warnings);
    }

    private CanvasLineageCompilation.Flow analyzeFlow(CanvasLineageOutputCandidate candidate) {
        String flowKey = flowKey(candidate);
        FlowState state = new FlowState(flowKey, candidate.node().id());
        LogicalPlan plan = candidate.dataset().queryExecution().analyzed();
        PlanEvidence evidence = analyzePlan(plan, null, state);

        List<Attribute> planOutputs = attributes(plan.output());
        Map<String, ValueEvidence> outputsByName = new LinkedHashMap<>();
        for (Attribute attribute : planOutputs) {
            ValueEvidence value = evidence.values().get(key(attribute));
            if (value != null) outputsByName.putIfAbsent(attribute.name(), value);
        }

        List<CanvasLineageCompilation.Field> fields = new ArrayList<>(state.inputFields.values());
        List<CanvasLineageCompilation.FieldEdge> edges = new ArrayList<>();
        boolean partial = state.partial;
        for (int ordinal = 0; ordinal < candidate.targetFields().size(); ordinal++) {
            CanvasLineageOutputCandidate.TargetField target = candidate.targetFields().get(ordinal);
            ValueEvidence value = outputsByName.get(target.columnCode());
            CanvasLineageCompilation.OutputEffect effect;
            if (value == null) {
                effect = target.missingOutputEffect();
            } else if (value.unknown()) {
                effect = CanvasLineageCompilation.OutputEffect.WRITTEN_UNKNOWN_SOURCE;
                partial = true;
                state.warn("OUTPUT_SOURCE_UNKNOWN", "无法可靠解析输出字段来源", ordinal + 1);
            } else if (!value.sources().isEmpty()) {
                effect = CanvasLineageCompilation.OutputEffect.DERIVED;
            } else {
                effect = value.effect();
            }
            CanvasLineageCompilation.Field outputField = new CanvasLineageCompilation.Field(
                    candidate.asset().localAssetKey(), target.localFieldKey(), target.modelFieldId(),
                    target.columnCode(), target.columnName(), ordinal, effect);
            fields.add(outputField);
            if (value == null || value.unknown() || value.sources().isEmpty()) continue;

            String nodeKey = value.nodeKey() == null
                    ? "canvas:output:" + candidate.node().id()
                    : value.nodeKey();
            String derivationKey = sha256(
                    target.localFieldKey() + "|" + nodeKey + "|" + value.fingerprint());
            CanvasLineageCompilation.FieldReference targetReference =
                    new CanvasLineageCompilation.FieldReference(
                            candidate.asset().localAssetKey(), target.localFieldKey());
            for (CanvasLineageCompilation.FieldReference source : value.sources()) {
                edges.add(new CanvasLineageCompilation.FieldEdge(
                        source, targetReference, derivationKey, value.derivationType(), nodeKey));
            }
        }

        List<CanvasLineageCompilation.Asset> inputAssets = state.reachableAssetKeys().stream()
                .map(state.assets::get)
                .filter(Objects::nonNull)
                .toList();
        CanvasLineageCompilation.Coverage coverage = partial
                ? CanvasLineageCompilation.Coverage.FIELD_PARTIAL
                : CanvasLineageCompilation.Coverage.FIELD_COMPLETE;
        return new CanvasLineageCompilation.Flow(
                flowKey, candidate.node().id(), candidate.node().nodeType(), coverage,
                candidate.asset(), inputAssets, fields, edges,
                List.copyOf(state.usages.values()), List.copyOf(state.warnings));
    }

    private PlanEvidence analyzePlan(LogicalPlan plan, String inheritedNodeKey, FlowState state) {
        String boundaryNodeId = boundaryNodeId(plan.output());
        String activeNodeKey = boundaryNodeId == null
                ? inheritedNodeKey
                : "canvas:node:" + boundaryNodeId;
        PlanEvidence input = inputEvidence(plan, state);
        if (input != null) return input;

        if (plan instanceof WithCTE withCte) {
            return analyzeWithCte(withCte, activeNodeKey, state);
        }
        if (plan instanceof CTERelationRef cteReference) {
            return analyzeCteReference(cteReference, activeNodeKey, state);
        }

        if (plan instanceof Union union) {
            return analyzeUnion(union, activeNodeKey, state);
        }
        List<LogicalPlan> children = plans(plan.children());
        List<PlanEvidence> childEvidence = children.stream()
                .map(child -> analyzePlan(child, activeNodeKey, state))
                .toList();
        Map<ExpressionKey, ValueEvidence> available = merge(childEvidence);

        if (plan instanceof Filter filter) {
            addUsage(filter.condition(), available, activeNodeKey,
                    CanvasLineageCompilation.UsageType.FILTER_CONDITION, state);
            return projectOutputs(plan, available, activeNodeKey, state, false);
        }
        if (plan instanceof Join join) {
            if (join.condition().isDefined()) {
                addUsage(join.condition().get(), available, activeNodeKey,
                        CanvasLineageCompilation.UsageType.JOIN_KEY, state);
            }
            return projectOutputs(plan, available, activeNodeKey, state, false);
        }
        if (plan instanceof Sort sort) {
            for (Expression expression : expressions(sort.order())) {
                addUsage(expression, available, activeNodeKey,
                        CanvasLineageCompilation.UsageType.SORT_KEY, state);
            }
            return projectOutputs(plan, available, activeNodeKey, state, false);
        }
        if (plan instanceof Aggregate aggregate) {
            for (Expression expression : expressions(aggregate.groupingExpressions())) {
                addUsage(expression, available, activeNodeKey,
                        CanvasLineageCompilation.UsageType.GROUP_KEY, state);
            }
            return namedOutputs(aggregate.aggregateExpressions(), available, activeNodeKey, state);
        }
        if (plan instanceof Window window) {
            for (Expression expression : expressions(window.partitionSpec())) {
                addUsage(expression, available, activeNodeKey,
                        CanvasLineageCompilation.UsageType.PARTITION_KEY, state);
            }
            for (Expression expression : expressions(window.orderSpec())) {
                addUsage(expression, available, activeNodeKey,
                        CanvasLineageCompilation.UsageType.SORT_KEY, state);
            }
            Map<ExpressionKey, ValueEvidence> output = new LinkedHashMap<>(available);
            output.putAll(namedOutputs(
                    window.windowExpressions(), available, activeNodeKey, state).values());
            return alignOutputs(plan, output, state);
        }
        if (plan instanceof Project project) {
            return namedOutputs(project.projectList(), available, activeNodeKey, state);
        }
        if (plan instanceof Generate generate) {
            ValueEvidence generated = generate.generator() instanceof Expression expression
                    ? expressionEvidence(expression, available, activeNodeKey)
                    : ValueEvidence.unknown(activeNodeKey, "generator:" + generate.generator().getClass().getName());
            Map<ExpressionKey, ValueEvidence> output = new LinkedHashMap<>(available);
            for (Attribute attribute : attributes(generate.generatorOutput())) {
                output.put(key(attribute), generated.asCalculated(activeNodeKey));
            }
            return alignOutputs(plan, output, state);
        }
        return projectOutputs(plan, available, activeNodeKey, state, true);
    }

    private PlanEvidence analyzeWithCte(
            WithCTE withCte,
            String activeNodeKey,
            FlowState state
    ) {
        for (CTERelationDef definition : CollectionConverters.asJava(withCte.cteDefs())) {
            PlanEvidence evidence = analyzePlan(definition.child(), activeNodeKey, state);
            List<ValueEvidence> values = new ArrayList<>();
            for (Attribute attribute : attributes(definition.output())) {
                values.add(evidence.values().getOrDefault(
                        key(attribute),
                        ValueEvidence.unknown(activeNodeKey, "cte-definition-output:" + definition.id())
                ));
            }
            state.cteValues.put(definition.id(), List.copyOf(values));
        }
        return analyzePlan(withCte.plan(), activeNodeKey, state);
    }

    private PlanEvidence analyzeCteReference(
            CTERelationRef reference,
            String activeNodeKey,
            FlowState state
    ) {
        List<Attribute> outputs = attributes(reference.output());
        List<ValueEvidence> values = state.cteValues.get(reference.cteId());
        if (values == null) {
            state.partial = true;
            state.warn("CTE_REFERENCE_UNRESOLVED", "无法可靠解析 CTE 引用", null);
            Map<ExpressionKey, ValueEvidence> unknown = new LinkedHashMap<>();
            for (Attribute attribute : outputs) {
                unknown.put(key(attribute), ValueEvidence.unknown(
                        activeNodeKey, "cte-reference:" + reference.cteId()));
            }
            return new PlanEvidence(unknown);
        }
        Map<ExpressionKey, ValueEvidence> mapped = new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < outputs.size(); ordinal++) {
            ValueEvidence value = ordinal < values.size()
                    ? values.get(ordinal)
                    : ValueEvidence.unknown(activeNodeKey, "cte-column-count:" + reference.cteId());
            mapped.put(key(outputs.get(ordinal)), value);
        }
        if (outputs.size() != values.size()) {
            state.partial = true;
            state.warn("CTE_REFERENCE_UNRESOLVED", "CTE 字段无法完整映射", null);
        }
        return new PlanEvidence(mapped);
    }

    private PlanEvidence analyzeUnion(Union union, String activeNodeKey, FlowState state) {
        List<PlanEvidence> branches = plans(union.children()).stream()
                .map(child -> analyzePlan(child, activeNodeKey, state)).toList();
        List<Attribute> outputs = attributes(union.output());
        List<List<Attribute>> branchOutputs = plans(union.children()).stream()
                .map(child -> attributes(child.output())).toList();
        Map<ExpressionKey, ValueEvidence> values = new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < outputs.size(); ordinal++) {
            List<ValueEvidence> parts = new ArrayList<>();
            for (int branch = 0; branch < branches.size(); branch++) {
                if (ordinal >= branchOutputs.get(branch).size()) {
                    parts.add(ValueEvidence.unknown(activeNodeKey, "union-column-count"));
                } else {
                    parts.add(branches.get(branch).values().getOrDefault(
                            key(branchOutputs.get(branch).get(ordinal)),
                            ValueEvidence.unknown(activeNodeKey, "union-source")));
                }
            }
            values.put(key(outputs.get(ordinal)), ValueEvidence.merge(
                    parts, activeNodeKey, compositeFingerprint("union", parts)));
        }
        return new PlanEvidence(values);
    }

    private PlanEvidence inputEvidence(LogicalPlan plan, FlowState state) {
        List<Attribute> outputs = attributes(plan.output());
        if (outputs.isEmpty() || outputs.stream().noneMatch(attribute ->
                CanvasLineageMetadata.isInput(attribute.metadata()))) return null;
        boolean markerInheritedFromChild = plans(plan.children()).stream()
                .flatMap(child -> attributes(child.output()).stream())
                .anyMatch(attribute -> CanvasLineageMetadata.isInput(attribute.metadata()));
        if (markerInheritedFromChild) return null;
        Map<ExpressionKey, ValueEvidence> values = new LinkedHashMap<>();
        Map<String, Integer> ordinals = new LinkedHashMap<>();
        for (Attribute attribute : outputs) {
            Metadata metadata = attribute.metadata();
            if (!CanvasLineageMetadata.isInput(metadata)) continue;
            String localAssetKey = CanvasLineageMetadata.inputAssetKey(metadata);
            CanvasLineageCompilation.Asset asset = CanvasLineageMetadata.readAsset(metadata, localAssetKey);
            state.assets.putIfAbsent(localAssetKey, asset);
            int ordinal = ordinals.merge(localAssetKey, 1, Integer::sum) - 1;
            CanvasLineageCompilation.Field field = new CanvasLineageCompilation.Field(
                    localAssetKey, CanvasLineageMetadata.columnKey(metadata),
                    CanvasLineageMetadata.modelFieldId(metadata), attribute.name(), attribute.name(),
                    ordinal, null);
            CanvasLineageCompilation.FieldReference reference =
                    new CanvasLineageCompilation.FieldReference(localAssetKey, field.localFieldKey());
            state.inputFields.putIfAbsent(reference, field);
            values.put(key(attribute), ValueEvidence.direct(reference));
        }
        return new PlanEvidence(values);
    }

    private PlanEvidence namedOutputs(
            scala.collection.immutable.Seq<? extends NamedExpression> namedExpressions,
            Map<ExpressionKey, ValueEvidence> available,
            String activeNodeKey,
            FlowState state
    ) {
        Map<ExpressionKey, ValueEvidence> values = new LinkedHashMap<>();
        for (NamedExpression named : CollectionConverters.asJava(namedExpressions)) {
            Expression expression = named instanceof Alias alias ? alias.child() : (Expression) named;
            ValueEvidence value = expressionEvidence(expression, available, activeNodeKey);
            if (CanvasLineageMetadata.isBoundary(named.metadata())) {
                value = value.withNodeKey("canvas:node:" + CanvasLineageMetadata.boundaryNodeId(named.metadata()));
            }
            values.put(key(named), value);
        }
        return new PlanEvidence(values);
    }

    private PlanEvidence projectOutputs(
            LogicalPlan plan,
            Map<ExpressionKey, ValueEvidence> available,
            String activeNodeKey,
            FlowState state,
            boolean warnUnknownPlan
    ) {
        if (warnUnknownPlan && plans(plan.children()).size() > 1) {
            state.partial = true;
            state.warn("UNSUPPORTED_CATALYST_PLAN", "无法可靠解释 Catalyst 节点：" + plan.nodeName(), null);
        } else if (warnUnknownPlan
                && !TRANSPARENT_PLAN_TYPES.contains(plan.getClass().getSimpleName())) {
            state.partial = true;
            state.warn("UNSUPPORTED_CATALYST_PLAN", "无法可靠解释 Catalyst 节点：" + plan.nodeName(), null);
        }
        return alignOutputs(plan, available, state);
    }

    private PlanEvidence alignOutputs(
            LogicalPlan plan,
            Map<ExpressionKey, ValueEvidence> available,
            FlowState state
    ) {
        Map<ExpressionKey, ValueEvidence> values = new LinkedHashMap<>();
        for (Attribute attribute : attributes(plan.output())) {
            ValueEvidence value = available.get(key(attribute));
            if (value != null) values.put(key(attribute), value);
        }
        if (values.size() != attributes(plan.output()).size() && !attributes(plan.output()).isEmpty()) {
            state.partial = true;
            state.warn("CATALYST_OUTPUT_UNRESOLVED", "部分 Catalyst 输出属性无法继续反向解析", null);
        }
        return new PlanEvidence(values);
    }

    private ValueEvidence expressionEvidence(
            Expression expression,
            Map<ExpressionKey, ValueEvidence> available,
            String activeNodeKey
    ) {
        if (expression instanceof AttributeReference attribute) {
            return available.getOrDefault(key(attribute),
                    ValueEvidence.unknown(activeNodeKey, "attribute:" + attribute.name()));
        }
        if (expression instanceof Alias alias) {
            return expressionEvidence(alias.child(), available, activeNodeKey);
        }
        if (expression instanceof Literal literal) {
            return literal.value() == null
                    ? ValueEvidence.nullValue(expressionFingerprint(expression, List.of()))
                    : ValueEvidence.constant(expressionFingerprint(expression, List.of()));
        }
        if (expression instanceof WindowExpression windowExpression) {
            ValueEvidence function = expressionEvidence(
                    windowExpression.windowFunction(), available, activeNodeKey);
            return function.sources().isEmpty()
                    ? ValueEvidence.unknown(activeNodeKey, expressionFingerprint(expression, List.of()))
                    : function.asWindowDerived(activeNodeKey);
        }
        List<ValueEvidence> children = expressions(expression.children()).stream()
                .map(child -> expressionEvidence(child, available, activeNodeKey)).toList();
        if (children.isEmpty()) {
            return ValueEvidence.unknown(activeNodeKey, expressionFingerprint(expression, List.of()));
        }
        boolean aggregate = expression instanceof AggregateExpression
                || children.stream().anyMatch(value ->
                value.derivationType() == CanvasLineageCompilation.DerivationType.AGGREGATED);
        ValueEvidence merged = ValueEvidence.merge(
                children, activeNodeKey, expressionFingerprint(expression, children));
        if (merged.unknown()) return merged;
        if (aggregate && merged.sources().isEmpty()) {
            return ValueEvidence.unknown(activeNodeKey, merged.fingerprint());
        }
        if (merged.sources().isEmpty()) {
            if (children.stream().allMatch(value ->
                    value.effect() == CanvasLineageCompilation.OutputEffect.NULL_FILLED)) {
                return ValueEvidence.nullValue(merged.fingerprint());
            }
            if (children.stream().allMatch(value ->
                    value.effect() == CanvasLineageCompilation.OutputEffect.CONSTANT
                            || value.effect() == CanvasLineageCompilation.OutputEffect.NULL_FILLED)) {
                return ValueEvidence.constant(merged.fingerprint());
            }
            return ValueEvidence.unknown(activeNodeKey, merged.fingerprint());
        }
        return merged.withDerivation(aggregate
                ? CanvasLineageCompilation.DerivationType.AGGREGATED
                : CanvasLineageCompilation.DerivationType.CALCULATED);
    }

    private void addUsage(
            Expression expression,
            Map<ExpressionKey, ValueEvidence> available,
            String nodeKey,
            CanvasLineageCompilation.UsageType usageType,
            FlowState state
    ) {
        ValueEvidence evidence = expressionEvidence(expression, available, nodeKey);
        if (evidence.unknown()) {
            state.partial = true;
            state.warn("FIELD_USAGE_UNRESOLVED", "无法可靠解析字段用途：" + usageType, null);
            return;
        }
        for (CanvasLineageCompilation.FieldReference source : evidence.sources()) {
            FieldUsageKey key = new FieldUsageKey(source, nodeKey, usageType);
            state.usages.putIfAbsent(key,
                    new CanvasLineageCompilation.FieldUsage(source, nodeKey, usageType));
        }
    }

    private static String boundaryNodeId(scala.collection.immutable.Seq<Attribute> output) {
        for (Attribute attribute : CollectionConverters.asJava(output)) {
            if (CanvasLineageMetadata.isBoundary(attribute.metadata())) {
                return CanvasLineageMetadata.boundaryNodeId(attribute.metadata());
            }
        }
        return null;
    }

    private static Map<ExpressionKey, ValueEvidence> merge(Collection<PlanEvidence> evidence) {
        Map<ExpressionKey, ValueEvidence> merged = new LinkedHashMap<>();
        evidence.forEach(item -> merged.putAll(item.values()));
        return merged;
    }

    private static List<Attribute> attributes(scala.collection.immutable.Seq<Attribute> values) {
        return List.copyOf(CollectionConverters.asJava(values));
    }

    private static List<LogicalPlan> plans(scala.collection.immutable.Seq<LogicalPlan> values) {
        return List.copyOf(CollectionConverters.asJava(values));
    }

    private static List<Expression> expressions(
            scala.collection.immutable.Seq<? extends Expression> values
    ) {
        return List.copyOf(CollectionConverters.asJava(values));
    }

    private static ExpressionKey key(NamedExpression expression) {
        return new ExpressionKey(expression.exprId().id(), expression.exprId().jvmId());
    }

    private static String flowKey(CanvasLineageOutputCandidate candidate) {
        return candidate.outputWriteId() == null
                ? "canvas:" + candidate.node().id()
                : "canvas:" + candidate.node().id() + ":output-write:" + candidate.outputWriteId();
    }

    private static int coverageRank(CanvasLineageCompilation.Coverage coverage) {
        return switch (coverage) {
            case MODEL_ONLY -> 0;
            case FIELD_PARTIAL -> 1;
            case FIELD_COMPLETE -> 2;
        };
    }

    private static String expressionFingerprint(Expression expression, List<ValueEvidence> children) {
        String childFingerprint = children.stream()
                .map(ValueEvidence::fingerprint).reduce("", (left, right) -> left + "|" + right);
        String literalKind = expression instanceof Literal literal
                ? (literal.value() == null ? "null" : "value") : "";
        return sha256(expression.getClass().getName() + "|" + expression.prettyName()
                + "|" + expression.dataType().typeName()
                + "|" + literalKind + "|" + childFingerprint);
    }

    private static String compositeFingerprint(String kind, List<ValueEvidence> values) {
        return sha256(kind + values.stream()
                .map(ValueEvidence::fingerprint)
                .reduce("", (left, right) -> left + "|" + right));
    }

    private static List<CanvasLineageCompilation.Asset> fallbackInputAssets(
            CanvasLineageOutputCandidate candidate
    ) {
        try {
            Map<String, CanvasLineageCompilation.Asset> assets = new LinkedHashMap<>();
            collectInputAssets(candidate.dataset().queryExecution().analyzed(), assets);
            return List.copyOf(assets.values());
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static void collectInputAssets(
            LogicalPlan plan,
            Map<String, CanvasLineageCompilation.Asset> assets
    ) {
        for (Attribute attribute : attributes(plan.output())) {
            Metadata metadata = attribute.metadata();
            if (!CanvasLineageMetadata.isInput(metadata)) continue;
            String localAssetKey = CanvasLineageMetadata.inputAssetKey(metadata);
            assets.putIfAbsent(localAssetKey, CanvasLineageMetadata.readAsset(metadata, localAssetKey));
        }
        for (LogicalPlan child : plans(plan.children())) {
            collectInputAssets(child, assets);
        }
    }

    private static CanvasLineageCompilation.Warning warning(
            String code, String message, String nodeId, String flowKey, Integer outputOrdinal
    ) {
        return new CanvasLineageCompilation.Warning(code, message, nodeId, flowKey, outputOrdinal);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ExpressionKey(long id, java.util.UUID jvmId) {
    }

    private record PlanEvidence(Map<ExpressionKey, ValueEvidence> values) {
    }

    private record FieldUsageKey(
            CanvasLineageCompilation.FieldReference field,
            String nodeKey,
            CanvasLineageCompilation.UsageType type
    ) {
    }

    private record ValueEvidence(
            Set<CanvasLineageCompilation.FieldReference> sources,
            boolean unknown,
            CanvasLineageCompilation.OutputEffect effect,
            CanvasLineageCompilation.DerivationType derivationType,
            String nodeKey,
            String fingerprint
    ) {
        private static ValueEvidence direct(CanvasLineageCompilation.FieldReference source) {
            return new ValueEvidence(Set.of(source), false,
                    CanvasLineageCompilation.OutputEffect.DERIVED,
                    CanvasLineageCompilation.DerivationType.DIRECT, null,
                    sha256(source.localAssetKey() + "|" + source.localFieldKey()));
        }

        private static ValueEvidence unknown(String nodeKey, String fingerprint) {
            return new ValueEvidence(Set.of(), true,
                    CanvasLineageCompilation.OutputEffect.WRITTEN_UNKNOWN_SOURCE,
                    CanvasLineageCompilation.DerivationType.CALCULATED, nodeKey, sha256(fingerprint));
        }

        private static ValueEvidence constant(String fingerprint) {
            return new ValueEvidence(Set.of(), false,
                    CanvasLineageCompilation.OutputEffect.CONSTANT,
                    CanvasLineageCompilation.DerivationType.CALCULATED, null, fingerprint);
        }

        private static ValueEvidence nullValue(String fingerprint) {
            return new ValueEvidence(Set.of(), false,
                    CanvasLineageCompilation.OutputEffect.NULL_FILLED,
                    CanvasLineageCompilation.DerivationType.CALCULATED, null, fingerprint);
        }

        private static ValueEvidence merge(
                List<ValueEvidence> values, String nodeKey, String fingerprint
        ) {
            Set<CanvasLineageCompilation.FieldReference> sources = new LinkedHashSet<>();
            boolean unknown = false;
            CanvasLineageCompilation.DerivationType type = CanvasLineageCompilation.DerivationType.DIRECT;
            for (ValueEvidence value : values) {
                sources.addAll(value.sources());
                unknown |= value.unknown();
                if (value.derivationType() == CanvasLineageCompilation.DerivationType.AGGREGATED) {
                    type = CanvasLineageCompilation.DerivationType.AGGREGATED;
                } else if (value.derivationType() == CanvasLineageCompilation.DerivationType.CALCULATED
                        && type == CanvasLineageCompilation.DerivationType.DIRECT) {
                    type = CanvasLineageCompilation.DerivationType.CALCULATED;
                }
            }
            return new ValueEvidence(Set.copyOf(sources), unknown,
                    sources.isEmpty() ? CanvasLineageCompilation.OutputEffect.WRITTEN_UNKNOWN_SOURCE
                            : CanvasLineageCompilation.OutputEffect.DERIVED,
                    type, nodeKey, fingerprint);
        }

        private ValueEvidence withNodeKey(String newNodeKey) {
            return new ValueEvidence(sources, unknown, effect, derivationType, newNodeKey, fingerprint);
        }

        private ValueEvidence withDerivation(CanvasLineageCompilation.DerivationType type) {
            return new ValueEvidence(sources, unknown, effect, type, nodeKey, fingerprint);
        }

        private ValueEvidence asCalculated(String newNodeKey) {
            return new ValueEvidence(sources, unknown, effect,
                    CanvasLineageCompilation.DerivationType.CALCULATED, newNodeKey, fingerprint);
        }

        private ValueEvidence asWindowDerived(String newNodeKey) {
            CanvasLineageCompilation.DerivationType type =
                    derivationType == CanvasLineageCompilation.DerivationType.AGGREGATED
                            ? CanvasLineageCompilation.DerivationType.AGGREGATED
                            : CanvasLineageCompilation.DerivationType.CALCULATED;
            return new ValueEvidence(sources, unknown, effect, type, newNodeKey, fingerprint);
        }
    }

    private static final class FlowState {
        private final String flowKey;
        private final String outputNodeId;
        private final Map<String, CanvasLineageCompilation.Asset> assets = new LinkedHashMap<>();
        private final Map<CanvasLineageCompilation.FieldReference, CanvasLineageCompilation.Field> inputFields =
                new LinkedHashMap<>();
        private final Map<Long, List<ValueEvidence>> cteValues = new LinkedHashMap<>();
        private final Map<FieldUsageKey, CanvasLineageCompilation.FieldUsage> usages = new LinkedHashMap<>();
        private final List<CanvasLineageCompilation.Warning> warnings = new ArrayList<>();
        private boolean partial;

        private FlowState(String flowKey, String outputNodeId) {
            this.flowKey = flowKey;
            this.outputNodeId = outputNodeId;
        }

        private void warn(String code, String message, Integer outputOrdinal) {
            warnings.add(warning(code, message, outputNodeId, flowKey, outputOrdinal));
        }

        private Set<String> reachableAssetKeys() {
            Set<String> keys = new LinkedHashSet<>();
            inputFields.keySet().forEach(field -> keys.add(field.localAssetKey()));
            usages.values().forEach(usage -> keys.add(usage.field().localAssetKey()));
            return keys;
        }
    }
}
