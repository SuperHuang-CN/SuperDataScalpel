import type { CanvasMetadataReference } from '../nodes/metadataReferences';

export type CanvasMetadataReferenceKind = CanvasMetadataReference['kind'];

export interface CanvasMetadataProvider<R extends CanvasMetadataReference> {
  kind: R['kind'];
  key(reference: R): string;
}

export type AnyCanvasMetadataProvider = {
  [K in CanvasMetadataReferenceKind]: CanvasMetadataProvider<
    Extract<CanvasMetadataReference, { kind: K }>
  >;
}[CanvasMetadataReferenceKind];

export interface CanvasMetadataProviderRegistry {
  require<K extends CanvasMetadataReferenceKind>(
    kind: K,
  ): CanvasMetadataProvider<Extract<CanvasMetadataReference, { kind: K }>>;
  resourceKey(reference: CanvasMetadataReference): string;
  deduplicate(
    references: readonly CanvasMetadataReference[],
  ): readonly CanvasMetadataReference[];
}

export const createCanvasMetadataProviderRegistry = (
  providers: readonly AnyCanvasMetadataProvider[],
): CanvasMetadataProviderRegistry => {
  const byKind = new Map<CanvasMetadataReferenceKind, AnyCanvasMetadataProvider>();
  providers.forEach((provider) => {
    if (byKind.has(provider.kind)) {
      throw new Error(`Canvas Metadata Provider 重复注册：${provider.kind}`);
    }
    byKind.set(provider.kind, provider);
  });

  const expectedKinds: readonly CanvasMetadataReferenceKind[] = [
    'JDBC_TABLE',
    'JDBC_QUERY_SOURCE',
    'MODEL',
    'FILE_DATASET_TABLE',
    'HTTP_API_RESOURCE',
    'SPATIAL_SERVICE_RESOURCE',
    'KAFKA_TOPIC',
    'TDENGINE_TMQ_TOPIC',
    'S3_TARGET',
  ];
  const missingKinds = expectedKinds.filter((kind) => !byKind.has(kind));
  if (missingKinds.length > 0) {
    throw new Error(`Canvas Metadata Provider 缺失：${missingKinds.join(', ')}`);
  }

  const requireProvider = <K extends CanvasMetadataReferenceKind>(kind: K) => {
    const provider = byKind.get(kind);
    if (!provider) throw new Error(`未知 Canvas Metadata Provider：${kind}`);
    return provider as CanvasMetadataProvider<
      Extract<CanvasMetadataReference, { kind: K }>
    >;
  };

  const resourceKey = (reference: CanvasMetadataReference) => {
    const provider = requireProvider(reference.kind);
    return `${reference.kind}\u0000${(
      provider.key as (candidate: CanvasMetadataReference) => string
    )(reference)}`;
  };

  return {
    require: requireProvider,
    resourceKey,
    deduplicate: (references) => [
      ...new Map(references.map((reference) => [resourceKey(reference), reference])).values(),
    ],
  };
};

export const canvasMetadataProviderRegistry = createCanvasMetadataProviderRegistry([
  {
    kind: 'JDBC_TABLE',
    key: (reference) => `${reference.dataSourceId}\u0000${reference.tableName}`,
  },
  {
    kind: 'JDBC_QUERY_SOURCE',
    key: (reference) => reference.dataSourceId,
  },
  {
    kind: 'MODEL',
    key: (reference) => reference.modelId,
  },
  {
    kind: 'FILE_DATASET_TABLE',
    key: (reference) => reference.fileDatasetTableId,
  },
  {
    kind: 'HTTP_API_RESOURCE',
    key: (reference) => `${reference.dataSourceId}\u0000${reference.resourceId}`,
  },
  {
    kind: 'SPATIAL_SERVICE_RESOURCE',
    key: (reference) => `${reference.dataSourceId}\u0000${reference.resourceId}`,
  },
  {
    kind: 'KAFKA_TOPIC',
    key: (reference) => `${reference.dataSourceId}\u0000${reference.topic}`,
  },
  {
    kind: 'TDENGINE_TMQ_TOPIC',
    key: (reference) => `${reference.dataSourceId}\u0000${reference.topicName}`,
  },
  {
    kind: 'S3_TARGET',
    key: (reference) => reference.dataSourceId,
  },
]);
