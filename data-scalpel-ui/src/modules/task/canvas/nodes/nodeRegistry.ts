import type { ComponentType } from 'react';
import {
  CANVAS_SCHEMA_MINOR_VERSION,
  CanvasNodeCategory,
  CanvasNodeType,
  type CanvasExecutionMode,
  type CanvasNodeDefinition,
  type CanvasNodeCategory as CanvasNodeCategoryValue,
  type CanvasNodeConfiguration,
  type CanvasNodeRuntimeData,
  type CanvasNodeType as CanvasNodeTypeValue,
} from '../canvasTypes';
import { aggregateSpec } from './aggregate/spec';
import { deduplicateSpec } from './deduplicate/spec';
import { deriveColumnsSpec } from './deriveColumns/spec';
import { fileDatasetInputSpec } from './fileDatasetInput/spec';
import { fileOutputSpec } from './fileOutput/spec';
import { filterSpec } from './filter/spec';
import { sqlTransformSpec } from './sqlTransform/spec';
import { geometryConstructSpec } from './geometryConstruct/spec';
import { geometryBufferSpec } from './geometryBuffer/spec';
import { geometryExplodeSpec } from './geometryExplode/spec';
import { geometryRepairSpec } from './geometryRepair/spec';
import { geometryDeriveSpec } from './geometryDerive/spec';
import { geometrySimplifySpec } from './geometrySimplify/spec';
import { spatialNearestSpec } from './spatialNearest/spec';
import { spatialSummarizeWithinSpec } from './spatialSummarizeWithin/spec';
import { spatialOverlaySpec } from './spatialOverlay/spec';
import { trackReconstructSpec } from './trackReconstruct/spec';
import { trackMotionStatisticsSpec } from './trackMotionStatistics/spec';
import { trackFindDwellSpec } from './trackFindDwell/spec';
import { trackDetectIncidentsSpec } from './trackDetectIncidents/spec';
import { spatialBinAggregateSpec } from './spatialBinAggregate/spec';
import { spatialPointClusterSpec } from './spatialPointCluster/spec';
import { spatialCenterDispersionSpec } from './spatialCenterDispersion/spec';
import { spatialDensitySpec } from './spatialDensity/spec';
import { spatialHotSpotsSpec } from './spatialHotSpots/spec';
import { spatialMultiVariableGridSpec } from './spatialMultiVariableGrid/spec';
import { spatialSimilarLocationsSpec } from './spatialSimilarLocations/spec';
import { spatialDescribeDatasetSpec } from './spatialDescribeDataset/spec';
import { spatialEnrichFromGridSpec } from './spatialEnrichFromGrid/spec';
import { spatialGroupByProximitySpec } from './spatialGroupByProximity/spec';
import { traceProximityEventsSpec } from './traceProximityEvents/spec';
import { snapTracksSpec } from './snapTracks/spec';
import { geometrySerializeSpec } from './geometrySerialize/spec';
import { geometryValidateSpec } from './geometryValidate/spec';
import { httpApiInputSpec } from './httpApiInput/spec';
import { spatialServiceInputSpec } from './spatialServiceInput/spec';
import { jdbcInputSpec } from './jdbcInput/spec';
import { jdbcIncrementalInputSpec } from './jdbcIncrementalInput/spec';
import { jdbcQueryInputSpec } from './jdbcQueryInput/spec';
import { jdbcOutputSpec } from './jdbcOutput/spec';
import { jdbcSnapshotSyncOutputSpec } from './jdbcSnapshotSyncOutput/spec';
import { joinSpec } from './join/spec';
import { jsonExtractSpec } from './jsonExtract/spec';
import { kafkaInputSpec } from './kafkaInput/spec';
import { tdEngineTmqInputSpec } from './tdEngineTmqInput/spec';
import { kafkaOutputSpec } from './kafkaOutput/spec';
import { modelInputSpec } from './modelInput/spec';
import { modelOutputSpec } from './modelOutput/spec';
import { modelSnapshotSyncOutputSpec } from './modelSnapshotSyncOutput/spec';
import { maskFieldsSpec } from './maskFields/spec';
import { nullHandlingSpec } from './nullHandling/spec';
import { canvasNodeGroup } from './nodeGroups';
import type { CanvasNodeSpec } from './nodeSpec';
import type { CanvasNodeSize } from './nodeSpec';
import type { CanvasMetadataReference } from './metadataReferences';
import { renameSpec } from './rename/spec';
import { selectColumnsSpec } from './selectColumns/spec';
import { spatialJoinSpec } from './spatialJoin/spec';
import { spatialClipSpec } from './spatialClip/spec';
import { spatialAggregateSpec } from './spatialAggregate/spec';
import { spatialMeasureSpec } from './spatialMeasure/spec';
import { spatialTransformSpec } from './spatialTransform/spec';
import { streamJoinSpec } from './streamJoin/spec';
import { topNSpec } from './topN/spec';
import { typeCastSpec } from './typeCast/spec';
import { unionSpec } from './union/spec';
import { valueMappingSpec } from './valueMapping/spec';
import { windowSpec } from './window/spec';

export type AnyCanvasNodeSpec = {
  [T in CanvasNodeTypeValue]: CanvasNodeSpec<T>;
}[CanvasNodeTypeValue];

const builtinSpecs = [
  modelInputSpec,
  jdbcInputSpec,
  jdbcIncrementalInputSpec,
  jdbcQueryInputSpec,
  fileDatasetInputSpec,
  httpApiInputSpec,
  spatialServiceInputSpec,
  kafkaInputSpec,
  tdEngineTmqInputSpec,
  filterSpec,
  sqlTransformSpec,
  deduplicateSpec,
  nullHandlingSpec,
  valueMappingSpec,
  maskFieldsSpec,
  jsonExtractSpec,
  renameSpec,
  selectColumnsSpec,
  deriveColumnsSpec,
  typeCastSpec,
  joinSpec,
  geometryConstructSpec,
  spatialTransformSpec,
  geometryValidateSpec,
  geometryRepairSpec,
  geometryDeriveSpec,
  geometrySimplifySpec,
  spatialNearestSpec,
  spatialSummarizeWithinSpec,
  spatialOverlaySpec,
  trackReconstructSpec,
  trackMotionStatisticsSpec,
  trackFindDwellSpec,
  trackDetectIncidentsSpec,
  spatialBinAggregateSpec,
  spatialPointClusterSpec,
  spatialCenterDispersionSpec,
  spatialDensitySpec,
  spatialHotSpotsSpec,
  spatialMultiVariableGridSpec,
  spatialSimilarLocationsSpec,
  spatialDescribeDatasetSpec,
  spatialEnrichFromGridSpec,
  spatialGroupByProximitySpec,
  traceProximityEventsSpec,
  snapTracksSpec,
  geometryBufferSpec,
  geometryExplodeSpec,
  spatialMeasureSpec,
  geometrySerializeSpec,
  spatialClipSpec,
  spatialAggregateSpec,
  spatialJoinSpec,
  unionSpec,
  aggregateSpec,
  windowSpec,
  topNSpec,
  streamJoinSpec,
  modelOutputSpec,
  jdbcOutputSpec,
  modelSnapshotSyncOutputSpec,
  jdbcSnapshotSyncOutputSpec,
  kafkaOutputSpec,
  fileOutputSpec,
] as const satisfies readonly AnyCanvasNodeSpec[];

export interface CanvasNodeRegistry {
  all(): readonly AnyCanvasNodeSpec[];
  require<T extends CanvasNodeTypeValue>(type: T): CanvasNodeSpec<T>;
  createDefaultConfiguration(type: CanvasNodeTypeValue): CanvasNodeConfiguration;
  createRuntimeData(type: CanvasNodeTypeValue, name?: string): CanvasNodeRuntimeData;
  summarize(data: CanvasNodeRuntimeData): string;
  resolveSize(data: CanvasNodeRuntimeData): CanvasNodeSize;
  canvasBody(type: CanvasNodeTypeValue): ComponentType<{ data: CanvasNodeRuntimeData }>;
  collectMetadataReferences(node: CanvasNodeDefinition): readonly CanvasMetadataReference[];
  forCategory(
    category: CanvasNodeCategoryValue,
    mode: CanvasExecutionMode,
  ): readonly AnyCanvasNodeSpec[];
}

const validateGraphCapability = (spec: AnyCanvasNodeSpec) => {
  const { graph } = spec;
  const degrees = [graph.minInputs, graph.minOutputs];
  if (degrees.some((degree) => !Number.isInteger(degree) || degree < 0)) {
    throw new Error(`Canvas 节点 ${spec.type} 的最小度数必须是非负整数`);
  }
  if (graph.maxInputs !== null
    && (!Number.isInteger(graph.maxInputs) || graph.maxInputs < graph.minInputs)) {
    throw new Error(`Canvas 节点 ${spec.type} 的最大入度无效`);
  }
  if (graph.maxOutputs !== null
    && (!Number.isInteger(graph.maxOutputs) || graph.maxOutputs < graph.minOutputs)) {
    throw new Error(`Canvas 节点 ${spec.type} 的最大出度无效`);
  }
  if (spec.category === CanvasNodeCategory.Input && graph.maxInputs !== 0) {
    throw new Error(`输入节点 ${spec.type} 不得声明输入端`);
  }
  if (spec.category === CanvasNodeCategory.Output
    && (graph.minOutputs !== 0 || graph.maxOutputs !== 0)) {
    throw new Error(`输出节点 ${spec.type} 不得声明输出端`);
  }
};

export const createCanvasNodeRegistry = (
  specs: readonly AnyCanvasNodeSpec[],
): CanvasNodeRegistry => {
  const expectedTypes = new Set(Object.values(CanvasNodeType));
  const byType = new Map<CanvasNodeTypeValue, AnyCanvasNodeSpec>();

  specs.forEach((spec) => {
    if (!expectedTypes.has(spec.type)) throw new Error(`未知 Canvas 节点类型：${spec.type}`);
    if (byType.has(spec.type)) throw new Error(`Canvas 节点类型重复注册：${spec.type}`);
    if (canvasNodeGroup(spec.group).category !== spec.category) {
      throw new Error(`Canvas 节点 ${spec.type} 的分类与分组不匹配`);
    }
    if (spec.supportedModes.length === 0) {
      throw new Error(`Canvas 节点 ${spec.type} 必须声明至少一种执行模式`);
    }
    if (new Set(spec.supportedModes).size !== spec.supportedModes.length) {
      throw new Error(`Canvas 节点 ${spec.type} 重复声明执行模式`);
    }
    if (!Number.isInteger(spec.introducedInMinor)
      || spec.introducedInMinor < 0
      || spec.introducedInMinor > CANVAS_SCHEMA_MINOR_VERSION) {
      throw new Error(`Canvas 节点 ${spec.type} 的协议引入版本无效`);
    }
    const defaultConfiguration = spec.createDefaultConfiguration();
    const baseSize = (spec.canvasView.resolveSize as (
      configuration: CanvasNodeConfiguration,
    ) => CanvasNodeSize)(defaultConfiguration);
    if (!Number.isFinite(baseSize.width)
      || !Number.isFinite(baseSize.height)
      || baseSize.width < 180
      || baseSize.height < 96) {
      throw new Error(`Canvas 节点 ${spec.type} 的语义基础尺寸无效`);
    }
    if (!Number.isInteger(spec.order) || spec.order < 0) {
      throw new Error(`Canvas 节点 ${spec.type} 的排序值无效`);
    }
    validateGraphCapability(spec);
    byType.set(spec.type, spec);
  });

  const missingTypes = [...expectedTypes].filter((type) => !byType.has(type));
  if (missingTypes.length > 0) {
    throw new Error(`Canvas 节点缺少 Spec：${missingTypes.join(', ')}`);
  }

  const ordered = [...specs].sort((left, right) => {
    const groupOrder = canvasNodeGroup(left.group).order - canvasNodeGroup(right.group).order;
    return groupOrder || left.order - right.order || left.type.localeCompare(right.type);
  });

  return {
    all: () => ordered,
    require: <T extends CanvasNodeTypeValue>(type: T) => {
      const spec = byType.get(type);
      if (!spec) throw new Error(`未知 Canvas 节点类型：${type}`);
      return spec as unknown as CanvasNodeSpec<T>;
    },
    createDefaultConfiguration: (type) => {
      const spec = byType.get(type);
      if (!spec) throw new Error(`未知 Canvas 节点类型：${type}`);
      return spec.createDefaultConfiguration();
    },
    createRuntimeData: (type, name) => {
      const spec = byType.get(type);
      if (!spec) throw new Error(`未知 Canvas 节点类型：${type}`);
      return {
        type,
        name: name ?? spec.label,
        configuration: spec.createDefaultConfiguration(),
      } as CanvasNodeRuntimeData;
    },
    summarize: (data) => {
      const spec = byType.get(data.type);
      if (!spec) throw new Error(`未知 Canvas 节点类型：${data.type}`);
      return (spec.summarize as (runtimeData: CanvasNodeRuntimeData) => string)(data);
    },
    resolveSize: (data) => {
      const spec = byType.get(data.type);
      if (!spec) throw new Error(`未知 Canvas 节点类型：${data.type}`);
      return (spec.canvasView.resolveSize as (
        configuration: CanvasNodeConfiguration,
      ) => CanvasNodeSize)(data.configuration);
    },
    canvasBody: (type) => {
      const spec = byType.get(type);
      if (!spec) throw new Error(`未知 Canvas 节点类型：${type}`);
      return spec.canvasView.Body as unknown as ComponentType<{ data: CanvasNodeRuntimeData }>;
    },
    collectMetadataReferences: (node) => {
      const spec = byType.get(node.type);
      if (!spec) throw new Error(`未知 Canvas 节点类型：${node.type}`);
      return (spec.collectMetadataReferences as (
        definition: CanvasNodeDefinition,
      ) => readonly CanvasMetadataReference[])(node);
    },
    forCategory: (category, mode) => ordered.filter(
      (spec) => spec.category === category && spec.supportedModes.includes(mode),
    ),
  };
};

export const canvasNodeRegistry = createCanvasNodeRegistry(builtinSpecs);
