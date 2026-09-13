package cn.superhuang.datascalpel.taskengine.compiler.lineage;

import cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.AttributeReference;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.RowNumber;
import org.apache.spark.sql.catalyst.expressions.WindowExpression;
import org.apache.spark.sql.catalyst.expressions.ArraySort;
import org.apache.spark.sql.catalyst.expressions.ArrayTransform;
import org.apache.spark.sql.catalyst.expressions.HigherOrderFunction;
import org.apache.spark.sql.catalyst.expressions.LambdaFunction;
import org.apache.spark.sql.catalyst.expressions.NamedLambdaVariable;
import org.apache.spark.sql.catalyst.expressions.RaiseError;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.CTERelationDef;
import org.apache.spark.sql.catalyst.plans.logical.CTERelationRef;
import org.apache.spark.sql.catalyst.plans.logical.Deduplicate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Expand;
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
import java.util.Arrays;
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
 * Reads stable identity markers from an analyzed Catalyst plan and emits
 * Spark-free lineage evidence. This analyzer never executes a Spark action.
 */
public final class CatalystLineageAnalyzer {
    private static final Set<String> TRANSPARENT_PLAN_TYPES = Set.of(
            "SubqueryAlias", "GlobalLimit", "LocalLimit", "Repartition",
            "RepartitionByExpression", "ResolvedHint"
    );
    private final CatalystLineageAnalysisLimits limits;

    public CatalystLineageAnalyzer() {
        this(CatalystLineageAnalysisLimits.canvasDefaults());
    }

    public CatalystLineageAnalyzer(CatalystLineageAnalysisLimits limits) {
        this.limits = Objects.requireNonNull(limits);
    }

    public TaskLineageEvidence analyze(List<CatalystLineageOutputCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return TaskLineageEvidence.unavailable(
                    "NO_OUTPUT_FLOW", "没有可分析的输出写入");
        }
        List<TaskLineageEvidence.Flow> flows = new ArrayList<>();
        List<TaskLineageEvidence.Warning> warnings = new ArrayList<>();
        for (CatalystLineageOutputCandidate candidate : candidates) {
            try {
                TaskLineageEvidence.Flow flow = analyzeFlow(candidate);
                flows.add(flow);
                warnings.addAll(flow.warnings());
            } catch (RuntimeException exception) {
                String flowKey = candidate.flowKey();
                boolean limitExceeded = exception instanceof CatalystLineageLimitExceededException;
                TaskLineageEvidence.Warning warning = warning(
                        limitExceeded ? "LINEAGE_LIMIT_EXCEEDED" : "CATALYST_LINEAGE_UNAVAILABLE",
                        limitExceeded
                                ? "Catalyst 计划超过运行血缘安全分析上限，已保留资产级流向"
                                : "Catalyst 计划可执行，但当前分析器无法完整解释该输出链路",
                        candidate.producerKey(), flowKey, null);
                warnings.add(warning);
                flows.add(new TaskLineageEvidence.Flow(
                        flowKey, candidate.producerKey(), candidate.producerType(),
                        TaskLineageEvidence.Coverage.MODEL_ONLY,
                        candidate.asset(), fallbackInputAssets(candidate),
                        List.of(), List.of(), List.of(), List.of(warning)
                ));
            }
        }
        TaskLineageEvidence.Coverage coverage = flows.stream()
                .map(TaskLineageEvidence.Flow::coverage)
                .min(Comparator.comparingInt(CatalystLineageAnalyzer::coverageRank))
                .orElse(TaskLineageEvidence.Coverage.MODEL_ONLY);
        TaskLineageEvidence.AnalysisStatus status = coverage == TaskLineageEvidence.Coverage.FIELD_COMPLETE
                ? TaskLineageEvidence.AnalysisStatus.COMPLETE
                : TaskLineageEvidence.AnalysisStatus.PARTIAL;
        return new TaskLineageEvidence(status, coverage, flows, warnings);
    }

    private TaskLineageEvidence.Flow analyzeFlow(CatalystLineageOutputCandidate candidate) {
        String flowKey = candidate.flowKey();
        FlowState state = new FlowState(
                flowKey, candidate.producerKey(), candidate.outputTransformKey());
        LogicalPlan plan = candidate.dataset().queryExecution().analyzed();
        limits.validate(plan);
        PlanEvidence evidence = analyzePlan(plan, null, state);

        List<Attribute> planOutputs = attributes(plan.output());
        Map<String, ValueEvidence> outputsByName = new LinkedHashMap<>();
        for (Attribute attribute : planOutputs) {
            ValueEvidence value = evidence.values().get(key(attribute));
            if (value != null) outputsByName.putIfAbsent(attribute.name(), value);
        }

        List<TaskLineageEvidence.Field> fields = new ArrayList<>(state.inputFields.values());
        List<TaskLineageEvidence.FieldEdge> edges = new ArrayList<>();
        boolean partial = state.partial;
        for (int ordinal = 0; ordinal < candidate.targetFields().size(); ordinal++) {
            CatalystLineageOutputCandidate.TargetField target = candidate.targetFields().get(ordinal);
            ValueEvidence value = outputsByName.get(target.columnCode());
            TaskLineageEvidence.OutputEffect effect;
            if (value == null) {
                effect = target.missingOutputEffect();
            } else if (value.unknown()) {
                effect = TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE;
                partial = true;
                state.warn("OUTPUT_SOURCE_UNKNOWN", "无法可靠解析输出字段来源", ordinal + 1);
            } else if (!value.sources().isEmpty()) {
                effect = TaskLineageEvidence.OutputEffect.DERIVED;
            } else {
                effect = value.effect();
            }
            TaskLineageEvidence.Field outputField = new TaskLineageEvidence.Field(
                    candidate.asset().localAssetKey(), target.localFieldKey(), target.modelFieldId(),
                    target.columnCode(), target.columnName(), ordinal, effect);
            fields.add(outputField);
            if (value == null || value.unknown() || value.sources().isEmpty()) continue;

            String nodeKey = value.nodeKey() == null
                    ? candidate.outputTransformKey()
                    : value.nodeKey();
            String derivationKey = sha256(
                    target.localFieldKey() + "|" + nodeKey + "|" + value.fingerprint());
            TaskLineageEvidence.FieldReference targetReference =
                    new TaskLineageEvidence.FieldReference(
                            candidate.asset().localAssetKey(), target.localFieldKey());
            for (TaskLineageEvidence.FieldReference source : value.sources()) {
                edges.add(new TaskLineageEvidence.FieldEdge(
                        source, targetReference, derivationKey, value.derivationType(), nodeKey));
            }
        }

        List<TaskLineageEvidence.Asset> inputAssets = state.reachableAssetKeys().stream()
                .map(state.assets::get)
                .filter(Objects::nonNull)
                .toList();
        TaskLineageEvidence.Coverage coverage = partial
                ? TaskLineageEvidence.Coverage.FIELD_PARTIAL
                : TaskLineageEvidence.Coverage.FIELD_COMPLETE;
        return new TaskLineageEvidence.Flow(
                flowKey, candidate.producerKey(), candidate.producerType(), coverage,
                candidate.asset(), inputAssets, fields, edges,
                List.copyOf(state.usages.values()), List.copyOf(state.warnings));
    }

    private PlanEvidence analyzePlan(LogicalPlan plan, String inheritedNodeKey, FlowState state) {
        String boundaryNodeKey = boundaryNodeKey(plan.output());
        String activeNodeKey = boundaryNodeKey == null
                ? inheritedNodeKey
                : boundaryNodeKey;
        PlanEvidence input = inputEvidence(plan, state);
        if (input != null) return input;
        PlanEvidence opaqueTransform = rowPreservingOpaqueTransformEvidence(
                plan, activeNodeKey, state);
        if (opaqueTransform != null) return opaqueTransform;

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

        if (plan instanceof Expand expand) {
            // Sliding time windows project one input row into several window rows. Expand is
            // not transparent: each output ordinal must be traced through every projection.
            List<List<Expression>> projections = CollectionConverters.asJava(expand.projections()).stream()
                    .map(CatalystLineageAnalyzer::expressions).toList();
            List<Attribute> outputs = attributes(expand.output());
            Map<ExpressionKey, ValueEvidence> values = new LinkedHashMap<>();
            for (int ordinal = 0; ordinal < outputs.size(); ordinal++) {
                List<ValueEvidence> parts = new ArrayList<>();
                for (List<Expression> projection : projections) {
                    parts.add(ordinal < projection.size() ? expressionEvidence(projection.get(ordinal), available, activeNodeKey)
                            : ValueEvidence.unknown(activeNodeKey, "expand-column-count"));
                }
                String fingerprint = compositeFingerprint("expand", parts);
                ValueEvidence value = parts.isEmpty() ? ValueEvidence.unknown(activeNodeKey, "expand-empty")
                        : ValueEvidence.merge(parts, activeNodeKey, fingerprint);
                if (!parts.isEmpty() && parts.stream().allMatch(v -> !v.unknown() && v.sources().isEmpty()
                        && (v.effect() == TaskLineageEvidence.OutputEffect.CONSTANT || v.effect() == TaskLineageEvidence.OutputEffect.NULL_FILLED))) {
                    value = parts.stream().allMatch(v -> v.effect() == TaskLineageEvidence.OutputEffect.NULL_FILLED)
                            ? ValueEvidence.nullValue(fingerprint) : ValueEvidence.constant(fingerprint);
                }
                values.put(key(outputs.get(ordinal)), value);
            }
            return new PlanEvidence(values);
        }

        if (plan instanceof Filter filter) {
            addUsage(filter.condition(), available, activeNodeKey,
                    TaskLineageEvidence.UsageType.FILTER_CONDITION, state);
            return projectOutputs(plan, available, activeNodeKey, state, false);
        }
        if (plan instanceof Join join) {
            if (join.condition().isDefined()) {
                addUsage(join.condition().get(), available, activeNodeKey,
                        TaskLineageEvidence.UsageType.JOIN_KEY, state);
            }
            return projectOutputs(plan, available, activeNodeKey, state, false);
        }
        if (plan instanceof Sort sort) {
            for (Expression expression : expressions(sort.order())) {
                addUsage(expression, available, activeNodeKey,
                        TaskLineageEvidence.UsageType.SORT_KEY, state);
            }
            return projectOutputs(plan, available, activeNodeKey, state, false);
        }
        if (plan instanceof Deduplicate deduplicate) {
            for (Attribute attribute : attributes(deduplicate.keys())) {
                addUsage(attribute, available, activeNodeKey,
                        TaskLineageEvidence.UsageType.GROUP_KEY, state);
            }
            return projectOutputs(plan, available, activeNodeKey, state, false);
        }
        if (plan instanceof Aggregate aggregate) {
            for (Expression expression : expressions(aggregate.groupingExpressions())) {
                addUsage(expression, available, activeNodeKey,
                        TaskLineageEvidence.UsageType.GROUP_KEY, state);
            }
            return namedOutputs(aggregate.aggregateExpressions(), available, activeNodeKey, state);
        }
        if (plan instanceof Window window) {
            for (Expression expression : expressions(window.partitionSpec())) {
                addUsage(expression, available, activeNodeKey,
                        TaskLineageEvidence.UsageType.PARTITION_KEY, state);
            }
            for (Expression expression : expressions(window.orderSpec())) {
                addUsage(expression, available, activeNodeKey,
                        TaskLineageEvidence.UsageType.SORT_KEY, state);
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

    private PlanEvidence rowPreservingOpaqueTransformEvidence(
            LogicalPlan plan,
            String activeNodeKey,
            FlowState state
    ) {
        List<Attribute> outputs = attributes(plan.output());
        if (outputs.isEmpty() || outputs.stream().noneMatch(attribute ->
                CatalystLineageMetadata.isRowPreservingOpaqueTransform(attribute.metadata()))) {
            return null;
        }
        List<LogicalPlan> directChildren = plans(plan.children());
        if (!(plan instanceof Project) || directChildren.size() != 1
                || !"SerializeFromObject".equals(directChildren.getFirst().nodeName())) {
            // Spark carries attribute metadata through later projections. Only the explicit
            // marker projection immediately above the encoder is the trust boundary.
            return null;
        }
        LogicalPlan inputPlan = opaqueTransformInput(plan);
        if (inputPlan == null) {
            state.partial = true;
            state.warn("OPAQUE_TRANSFORM_INPUT_UNRESOLVED",
                    "受控行变换未找到可验证的输入边界", null);
            return null;
        }
        PlanEvidence input = analyzePlan(inputPlan, activeNodeKey, state);
        Map<String, ValueEvidence> inputsByName = new LinkedHashMap<>();
        for (Attribute attribute : attributes(inputPlan.output())) {
            ValueEvidence value = input.values().get(key(attribute));
            if (value != null) inputsByName.putIfAbsent(attribute.name(), value);
        }
        Map<ExpressionKey, ValueEvidence> values = new LinkedHashMap<>();
        for (Attribute output : outputs) {
            Metadata metadata = output.metadata();
            if (!CatalystLineageMetadata.isRowPreservingOpaqueTransform(metadata)) continue;
            ValueEvidence value;
            if (metadata.contains(CatalystLineageMetadata.OPAQUE_DERIVED_SOURCE_COLUMNS)) {
                List<ValueEvidence> dependencies = Arrays.stream(metadata.getStringArray(
                                CatalystLineageMetadata.OPAQUE_DERIVED_SOURCE_COLUMNS))
                        .map(inputsByName::get)
                        .filter(Objects::nonNull)
                        .toList();
                int expected = metadata.getStringArray(
                        CatalystLineageMetadata.OPAQUE_DERIVED_SOURCE_COLUMNS).length;
                value = dependencies.size() == expected && expected > 0
                        ? ValueEvidence.merge(dependencies, activeNodeKey,
                        compositeFingerprint("opaque-row-derived", dependencies))
                        .asCalculated(activeNodeKey)
                        : ValueEvidence.unknown(activeNodeKey,
                        "opaque-row-derived:" + output.name());
            } else {
                value = inputsByName.getOrDefault(output.name(),
                        ValueEvidence.unknown(activeNodeKey,
                                "opaque-row-passthrough:" + output.name()));
            }
            values.put(key(output), value);
        }
        return new PlanEvidence(values);
    }

    private static LogicalPlan opaqueTransformInput(LogicalPlan boundary) {
        LogicalPlan current = boundary;
        for (int depth = 0; depth < 8; depth++) {
            List<LogicalPlan> children = plans(current.children());
            if ("DeserializeToObject".equals(current.nodeName())) {
                return children.size() == 1 ? children.getFirst() : null;
            }
            if (children.size() != 1) return null;
            current = children.getFirst();
        }
        return null;
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
                CatalystLineageMetadata.isInput(attribute.metadata()))) return null;
        boolean markerInheritedFromChild = plans(plan.children()).stream()
                .flatMap(child -> attributes(child.output()).stream())
                .anyMatch(attribute -> CatalystLineageMetadata.isInput(attribute.metadata()));
        if (markerInheritedFromChild) return null;
        Map<ExpressionKey, ValueEvidence> values = new LinkedHashMap<>();
        Map<String, Integer> ordinals = new LinkedHashMap<>();
        for (Attribute attribute : outputs) {
            Metadata metadata = attribute.metadata();
            if (!CatalystLineageMetadata.isInput(metadata)) continue;
            String localAssetKey = CatalystLineageMetadata.inputAssetKey(metadata);
            TaskLineageEvidence.Asset asset = CatalystLineageMetadata.readAsset(metadata, localAssetKey);
            state.assets.putIfAbsent(localAssetKey, asset);
            int ordinal = ordinals.merge(localAssetKey, 1, Integer::sum) - 1;
            TaskLineageEvidence.Field field = new TaskLineageEvidence.Field(
                    localAssetKey, CatalystLineageMetadata.columnKey(metadata),
                    CatalystLineageMetadata.modelFieldId(metadata), attribute.name(), attribute.name(),
                    ordinal, null);
            TaskLineageEvidence.FieldReference reference =
                    new TaskLineageEvidence.FieldReference(localAssetKey, field.localFieldKey());
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
            ValueEvidence value = CatalystLineageMetadata.isTechnicalColumn(named.metadata())
                    ? ValueEvidence.constant("technical-column:" + named.name())
                    : expressionEvidence(expression, available, activeNodeKey);
            if (CatalystLineageMetadata.isBoundary(named.metadata())) {
                value = value.withNodeKey(CatalystLineageMetadata.boundaryNodeKey(named.metadata()));
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
        if (expression instanceof NamedLambdaVariable variable) {
            return available.getOrDefault(key(variable), ValueEvidence.unknown(activeNodeKey, "unbound-lambda"));
        }
        if (expression instanceof Alias alias) {
            return expressionEvidence(alias.child(), available, activeNodeKey);
        }
        if (expression instanceof Literal literal) {
            return literal.value() == null
                    ? ValueEvidence.nullValue(expressionFingerprint(expression, List.of()))
                    : ValueEvidence.constant(expressionFingerprint(expression, List.of()));
        }
        if (expression instanceof RaiseError) {
            // raise_error never emits a field value. Treat the failure-only branch as a
            // constant so a guarded CASE/WHEN keeps the lineage of its real value branch.
            return ValueEvidence.constant(expressionFingerprint(expression, List.of()));
        }
        if (expression instanceof WindowExpression windowExpression) {
            if (windowExpression.windowFunction() instanceof RowNumber) {
                // RowNumber has no value arguments: its value depends on the partition and order.
                // Resolve only this known function, not arbitrary sourceless window expressions.
                List<Expression> keys = new ArrayList<>(expressions(windowExpression.windowSpec().partitionSpec()));
                keys.addAll(expressions(windowExpression.windowSpec().orderSpec()));
                List<ValueEvidence> evidence = keys.stream()
                        .map(key -> expressionEvidence(key, available, activeNodeKey)).toList();
                ValueEvidence ranking = ValueEvidence.merge(evidence, activeNodeKey,
                        expressionFingerprint(expression, evidence));
                return ranking.sources().isEmpty()
                        ? ValueEvidence.unknown(activeNodeKey, ranking.fingerprint())
                        : ranking.withDerivation(TaskLineageEvidence.DerivationType.CALCULATED);
            }
            ValueEvidence function = expressionEvidence(
                    windowExpression.windowFunction(), available, activeNodeKey);
            return function.sources().isEmpty()
                    ? ValueEvidence.unknown(activeNodeKey, expressionFingerprint(expression, List.of()))
                    : function.asWindowDerived(activeNodeKey);
        }
        if (expression instanceof ArraySort || expression instanceof ArrayTransform) {
            // The lambda element(s) come from this array, never from a synthetic physical field.
            // Deliberately scoped to these two known operators; unsupported lambdas stay unknown.
            HigherOrderFunction function = (HigherOrderFunction) expression;
            List<ValueEvidence> evidence = new ArrayList<>();
            for (Expression argument : expressions(function.arguments()))
                evidence.add(expressionEvidence(argument, available, activeNodeKey));
            if (evidence.size() != 1) return ValueEvidence.unknown(activeNodeKey, "array-argument-count");
            ValueEvidence element = evidence.getFirst();
            for (Expression body : expressions(function.functions())) {
                if (!(body instanceof LambdaFunction lambda)) return ValueEvidence.unknown(activeNodeKey, "unresolved-array-lambda");
                Map<ExpressionKey, ValueEvidence> bound = new LinkedHashMap<>(available);
                int index = 0;
                for (NamedExpression parameter : CollectionConverters.asJava(lambda.arguments())) {
                    bound.put(key(parameter), expression instanceof ArrayTransform && index == 1
                            ? ValueEvidence.constant("array-element-index") : element);
                    index++;
                }
                evidence.add(expressionEvidence(lambda.function(), bound, activeNodeKey));
            }
            return ValueEvidence.merge(evidence, activeNodeKey, expressionFingerprint(expression, evidence));
        }
        List<ValueEvidence> children = expressions(expression.children()).stream()
                .map(child -> expressionEvidence(child, available, activeNodeKey)).toList();
        if (children.isEmpty()) {
            return ValueEvidence.unknown(activeNodeKey, expressionFingerprint(expression, List.of()));
        }
        boolean aggregate = expression instanceof AggregateExpression
                || children.stream().anyMatch(value ->
                value.derivationType() == TaskLineageEvidence.DerivationType.AGGREGATED);
        ValueEvidence merged = ValueEvidence.merge(
                children, activeNodeKey, expressionFingerprint(expression, children));
        if (merged.unknown()) return merged;
        if (aggregate && merged.sources().isEmpty()) {
            return ValueEvidence.unknown(activeNodeKey, merged.fingerprint());
        }
        if (merged.sources().isEmpty()) {
            if (children.stream().allMatch(value ->
                    value.effect() == TaskLineageEvidence.OutputEffect.NULL_FILLED)) {
                return ValueEvidence.nullValue(merged.fingerprint());
            }
            if (children.stream().allMatch(value ->
                    value.effect() == TaskLineageEvidence.OutputEffect.CONSTANT
                            || value.effect() == TaskLineageEvidence.OutputEffect.NULL_FILLED)) {
                return ValueEvidence.constant(merged.fingerprint());
            }
            return ValueEvidence.unknown(activeNodeKey, merged.fingerprint());
        }
        return merged.withDerivation(aggregate
                ? TaskLineageEvidence.DerivationType.AGGREGATED
                : TaskLineageEvidence.DerivationType.CALCULATED);
    }

    private void addUsage(
            Expression expression,
            Map<ExpressionKey, ValueEvidence> available,
            String nodeKey,
            TaskLineageEvidence.UsageType usageType,
            FlowState state
    ) {
        String effectiveNodeKey = nodeKey == null || nodeKey.isBlank()
                ? state.defaultOperationKey
                : nodeKey;
        ValueEvidence evidence = expressionEvidence(expression, available, effectiveNodeKey);
        if (evidence.unknown()) {
            state.partial = true;
            state.warn("FIELD_USAGE_UNRESOLVED", "无法可靠解析字段用途：" + usageType, null);
            return;
        }
        for (TaskLineageEvidence.FieldReference source : evidence.sources()) {
            FieldUsageKey key = new FieldUsageKey(source, effectiveNodeKey, usageType);
            state.usages.putIfAbsent(key,
                    new TaskLineageEvidence.FieldUsage(source, effectiveNodeKey, usageType));
        }
    }

    private static String boundaryNodeKey(scala.collection.immutable.Seq<Attribute> output) {
        for (Attribute attribute : CollectionConverters.asJava(output)) {
            if (CatalystLineageMetadata.isBoundary(attribute.metadata())) {
                return CatalystLineageMetadata.boundaryNodeKey(attribute.metadata());
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

    private static int coverageRank(TaskLineageEvidence.Coverage coverage) {
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

    private static List<TaskLineageEvidence.Asset> fallbackInputAssets(
            CatalystLineageOutputCandidate candidate
    ) {
        try {
            Map<String, TaskLineageEvidence.Asset> assets = new LinkedHashMap<>();
            collectInputAssets(candidate.dataset().queryExecution().analyzed(), assets);
            return List.copyOf(assets.values());
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static void collectInputAssets(
            LogicalPlan plan,
            Map<String, TaskLineageEvidence.Asset> assets
    ) {
        for (Attribute attribute : attributes(plan.output())) {
            Metadata metadata = attribute.metadata();
            if (!CatalystLineageMetadata.isInput(metadata)) continue;
            String localAssetKey = CatalystLineageMetadata.inputAssetKey(metadata);
            assets.putIfAbsent(localAssetKey, CatalystLineageMetadata.readAsset(metadata, localAssetKey));
        }
        for (LogicalPlan child : plans(plan.children())) {
            collectInputAssets(child, assets);
        }
    }

    private static TaskLineageEvidence.Warning warning(
            String code, String message, String nodeId, String flowKey, Integer outputOrdinal
    ) {
        return new TaskLineageEvidence.Warning(code, message, nodeId, flowKey, outputOrdinal);
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
            TaskLineageEvidence.FieldReference field,
            String nodeKey,
            TaskLineageEvidence.UsageType type
    ) {
    }

    private record ValueEvidence(
            Set<TaskLineageEvidence.FieldReference> sources,
            boolean unknown,
            TaskLineageEvidence.OutputEffect effect,
            TaskLineageEvidence.DerivationType derivationType,
            String nodeKey,
            String fingerprint
    ) {
        private static ValueEvidence direct(TaskLineageEvidence.FieldReference source) {
            return new ValueEvidence(Set.of(source), false,
                    TaskLineageEvidence.OutputEffect.DERIVED,
                    TaskLineageEvidence.DerivationType.DIRECT, null,
                    sha256(source.localAssetKey() + "|" + source.localFieldKey()));
        }

        private static ValueEvidence unknown(String nodeKey, String fingerprint) {
            return new ValueEvidence(Set.of(), true,
                    TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE,
                    TaskLineageEvidence.DerivationType.CALCULATED, nodeKey, sha256(fingerprint));
        }

        private static ValueEvidence constant(String fingerprint) {
            return new ValueEvidence(Set.of(), false,
                    TaskLineageEvidence.OutputEffect.CONSTANT,
                    TaskLineageEvidence.DerivationType.CALCULATED, null, fingerprint);
        }

        private static ValueEvidence nullValue(String fingerprint) {
            return new ValueEvidence(Set.of(), false,
                    TaskLineageEvidence.OutputEffect.NULL_FILLED,
                    TaskLineageEvidence.DerivationType.CALCULATED, null, fingerprint);
        }

        private static ValueEvidence merge(
                List<ValueEvidence> values, String nodeKey, String fingerprint
        ) {
            Set<TaskLineageEvidence.FieldReference> sources = new LinkedHashSet<>();
            boolean unknown = false;
            TaskLineageEvidence.DerivationType type = TaskLineageEvidence.DerivationType.DIRECT;
            for (ValueEvidence value : values) {
                sources.addAll(value.sources());
                unknown |= value.unknown();
                if (value.derivationType() == TaskLineageEvidence.DerivationType.AGGREGATED) {
                    type = TaskLineageEvidence.DerivationType.AGGREGATED;
                } else if (value.derivationType() == TaskLineageEvidence.DerivationType.CALCULATED
                        && type == TaskLineageEvidence.DerivationType.DIRECT) {
                    type = TaskLineageEvidence.DerivationType.CALCULATED;
                }
            }
            return new ValueEvidence(Set.copyOf(sources), unknown,
                    sources.isEmpty() ? TaskLineageEvidence.OutputEffect.WRITTEN_UNKNOWN_SOURCE
                            : TaskLineageEvidence.OutputEffect.DERIVED,
                    type, nodeKey, fingerprint);
        }

        private ValueEvidence withNodeKey(String newNodeKey) {
            return new ValueEvidence(sources, unknown, effect, derivationType, newNodeKey, fingerprint);
        }

        private ValueEvidence withDerivation(TaskLineageEvidence.DerivationType type) {
            return new ValueEvidence(sources, unknown, effect, type, nodeKey, fingerprint);
        }

        private ValueEvidence asCalculated(String newNodeKey) {
            return new ValueEvidence(sources, unknown, effect,
                    TaskLineageEvidence.DerivationType.CALCULATED, newNodeKey, fingerprint);
        }

        private ValueEvidence asWindowDerived(String newNodeKey) {
            TaskLineageEvidence.DerivationType type =
                    derivationType == TaskLineageEvidence.DerivationType.AGGREGATED
                            ? TaskLineageEvidence.DerivationType.AGGREGATED
                            : TaskLineageEvidence.DerivationType.CALCULATED;
            return new ValueEvidence(sources, unknown, effect, type, newNodeKey, fingerprint);
        }
    }

    private static final class FlowState {
        private final String flowKey;
        private final String outputNodeId;
        private final String defaultOperationKey;
        private final Map<String, TaskLineageEvidence.Asset> assets = new LinkedHashMap<>();
        private final Map<TaskLineageEvidence.FieldReference, TaskLineageEvidence.Field> inputFields =
                new LinkedHashMap<>();
        private final Map<Long, List<ValueEvidence>> cteValues = new LinkedHashMap<>();
        private final Map<FieldUsageKey, TaskLineageEvidence.FieldUsage> usages = new LinkedHashMap<>();
        private final List<TaskLineageEvidence.Warning> warnings = new ArrayList<>();
        private boolean partial;

        private FlowState(String flowKey, String outputNodeId, String defaultOperationKey) {
            this.flowKey = flowKey;
            this.outputNodeId = outputNodeId;
            this.defaultOperationKey = defaultOperationKey;
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
