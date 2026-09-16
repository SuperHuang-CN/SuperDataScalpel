export type RelationCardinality = 'ONE_TO_ONE' | 'ONE_TO_MANY' | 'MANY_TO_ONE';
export type CapabilityKind = 'QUERY' | 'CALCULATION' | 'ACTION';

export interface BusinessObjectFilter {
  fieldId: string | null;
  operator: string;
  value?: unknown;
  secondValue?: unknown;
  values?: unknown[];
}

export interface BusinessObjectMainSource {
  modelId: string | null;
  identityFieldId: string | null;
  titleFieldId: string | null;
  fixedFilters: BusinessObjectFilter[];
}

export interface BusinessObjectFieldMapping {
  mainFieldId: string | null;
  supplementFieldId: string | null;
}

export interface BusinessObjectSupplementSource {
  id: string;
  name: string | null;
  modelId: string | null;
  keyMappings: BusinessObjectFieldMapping[];
  fixedFilters: BusinessObjectFilter[];
  dataTimeFieldId: string | null;
}

export interface BusinessObjectPropertyGroup {
  id: string;
  name: string;
  sortOrder: number | null;
}

export interface BusinessObjectProperty {
  id: string;
  code: string;
  name: string;
  description: string | null;
  unit: string | null;
  groupId: string | null;
  sourceModelId: string | null;
  fieldId: string | null;
  sortOrder: number | null;
}

export interface BusinessObjectRelation {
  id: string;
  code: string;
  forwardName: string;
  forwardAccessCode: string;
  reverseName: string;
  reverseAccessCode: string;
  description: string | null;
  targetObjectTypeId: string | null;
  cardinality: RelationCardinality | null;
  sourceFieldId: string | null;
  targetFieldId: string | null;
}

export interface BusinessObjectCapabilityField {
  code: string;
  name: string;
  fieldType: string | null;
  description: string | null;
  required: boolean | null;
}

export interface BusinessObjectCapability {
  id: string;
  code: string;
  name: string;
  kind: CapabilityKind | null;
  description: string | null;
  inputs: BusinessObjectCapabilityField[];
  outputs: BusinessObjectCapabilityField[];
  preconditions: string | null;
  expectedEffect: string | null;
}

export interface BusinessObjectTypeDefinition {
  mainSource: BusinessObjectMainSource | null;
  supplements: BusinessObjectSupplementSource[];
  groups: BusinessObjectPropertyGroup[];
  properties: BusinessObjectProperty[];
  relations: BusinessObjectRelation[];
  capabilities: BusinessObjectCapability[];
}

export interface BusinessObjectTypeIssue { code: string; path: string; message: string; blocking: boolean }
export interface BusinessObjectTypeValidation { canPreview: boolean; issues: BusinessObjectTypeIssue[] }

export interface BusinessObjectType {
  id: string;
  code: string;
  name: string;
  directoryId: string | null;
  ownerName: string | null;
  summary: string | null;
  enabled: boolean;
  mainSourceModelName: string | null;
  propertyCount: number;
  relationCount: number;
  definition: BusinessObjectTypeDefinition;
  health: BusinessObjectTypeValidation;
  createdAt: string;
  updatedAt: string;
}

export interface BusinessObjectTypeGraphNode {
  id: string;
  name: string;
  code: string;
  directoryId: string | null;
  enabled: boolean;
  mainSourceModelId: string | null;
  mainSourceModelName: string | null;
  mainSourceModelCode: string | null;
  propertyCount: number;
  groupCount: number;
  supplementCount: number;
  capabilityCount: number;
  updatedAt: string;
}

export interface BusinessObjectTypeGraphRelation {
  id: string | null;
  code: string | null;
  sourceObjectTypeId: string;
  sourceObjectTypeName: string;
  targetObjectTypeId: string | null;
  targetObjectTypeName: string | null;
  forwardName: string | null;
  forwardAccessCode: string | null;
  reverseName: string | null;
  reverseAccessCode: string | null;
  description: string | null;
  cardinality: RelationCardinality | null;
  sourceFieldId: string | null;
  sourceFieldName: string | null;
  sourceFieldCode: string | null;
  targetFieldId: string | null;
  targetFieldName: string | null;
  targetFieldCode: string | null;
}

export interface BusinessObjectTypeGraph {
  nodes: BusinessObjectTypeGraphNode[];
  relations: BusinessObjectTypeGraphRelation[];
  queriedAt: string;
}

export interface BusinessObjectTypeBasics {
  name: string;
  directoryId: string | null;
  ownerName: string | null;
  summary: string | null;
}

export interface BusinessObjectRelationSummary {
  id: string; code: string; name: string; accessCode: string; reverseName: string; reverseAccessCode: string; description: string | null;
  cardinality: RelationCardinality; direction: 'OUTBOUND' | 'INBOUND';
  sourceObjectTypeId: string; sourceObjectTypeName: string; targetObjectTypeId: string; targetObjectTypeName: string;
}

export interface BusinessObjectCandidate { objectKey: string; title: string }
export interface BusinessObjectCandidates { items: BusinessObjectCandidate[]; pageNo: number; pageSize: number; hasNext: boolean }
export interface BusinessObjectPropertyValue {
  propertyId: string; code: string; name: string; groupId: string | null; unit: string | null;
  fieldType: string | null; value: unknown; sourceStatus: 'MATCHED' | 'NULL' | 'NO_MATCH' | 'ERROR';
  sourceModelId: string | null; sourceModelName: string | null; sourceFieldId: string | null;
  sourceFieldCode: string | null; sourceFieldName: string | null; sourceDataTime: unknown; diagnostic: string | null;
}
export interface BusinessObjectPreview {
  object: { objectTypeId: string; objectTypeCode: string; objectTypeName: string; objectKey: string; title: string };
  properties: BusinessObjectPropertyValue[]; diagnostics: BusinessObjectTypeIssue[]; queriedAt: string;
}
export interface BusinessObjectRelatedPreview {
  relation: BusinessObjectRelationSummary; items: BusinessObjectCandidate[]; pageNo: number; pageSize: number; hasNext: boolean; diagnostics: BusinessObjectTypeIssue[];
}

export const emptyBusinessObjectDefinition = (): BusinessObjectTypeDefinition => ({
  mainSource: null, supplements: [], groups: [], properties: [], relations: [], capabilities: [],
});

export const relationCardinalityLabels: Record<RelationCardinality, string> = {
  ONE_TO_ONE: '一对一', ONE_TO_MANY: '一对多', MANY_TO_ONE: '多对一',
};

export const capabilityKindLabels: Record<CapabilityKind, string> = {
  QUERY: '查询', CALCULATION: '计算', ACTION: 'Action',
};
