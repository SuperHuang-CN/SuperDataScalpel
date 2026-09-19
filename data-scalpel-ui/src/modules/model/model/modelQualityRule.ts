import type { PlatformDataType } from './dataModel';
import type { StandardDictionarySummary } from '../../standard';

export type ModelQualityRuleType =
  | 'NOT_NULL'
  | 'UNIQUE'
  | 'VALUE_RANGE'
  | 'STRING_LENGTH'
  | 'DICTIONARY_MEMBERSHIP'
  | 'ROW_COUNT'
  | 'FRESHNESS'
  | 'GEOMETRY_VALID'
  | 'GEOMETRY_NON_EMPTY'
  | 'FORMAT_PATTERN'
  | 'CONDITIONAL_NOT_NULL'
  | 'FIELD_COMPARISON'
  | 'REFERENCE_EXISTS';

export type ModelQualityRuleSeverity = 'CRITICAL' | 'MAJOR' | 'MINOR';
export type ViolationMetric = 'COUNT' | 'PERCENT';

export interface ViolationTolerance {
  metric: ViolationMetric;
  value: number;
}

export type FormatPatternKind = 'PRESET' | 'REGEX';
export type FormatPatternPreset =
  | 'RESIDENT_ID_CARD'
  | 'UNIFIED_SOCIAL_CREDIT_CODE'
  | 'MAINLAND_MOBILE_PHONE'
  | 'EMAIL'
  | 'ADMINISTRATIVE_DIVISION_CODE';
export type QualityConditionOperator =
  | 'EQ'
  | 'NE'
  | 'IN'
  | 'NOT_IN'
  | 'IS_NULL'
  | 'IS_NOT_NULL'
  | 'IS_EMPTY'
  | 'IS_NOT_EMPTY';
export type QualityFieldComparisonOperator = 'EQ' | 'NE' | 'LT' | 'LE' | 'GT' | 'GE';

export type ModelQualityRuleDefinition =
  | { type: 'NOT_NULL'; fieldId: string; tolerance: ViolationTolerance }
  | { type: 'UNIQUE'; fieldIds: string[]; tolerance: ViolationTolerance }
  | {
      type: 'VALUE_RANGE';
      fieldId: string;
      minimum?: string;
      maximum?: string;
      minimumInclusive: boolean;
      maximumInclusive: boolean;
      tolerance: ViolationTolerance;
    }
  | {
      type: 'STRING_LENGTH';
      fieldId: string;
      minimumLength?: number;
      maximumLength?: number;
      tolerance: ViolationTolerance;
    }
  | { type: 'DICTIONARY_MEMBERSHIP'; fieldId: string; tolerance: ViolationTolerance }
  | { type: 'ROW_COUNT'; minimumRowCount: number }
  | { type: 'FRESHNESS'; fieldId: string; maximumDelayMinutes: number }
  | { type: 'GEOMETRY_VALID'; fieldId: string; tolerance: ViolationTolerance }
  | { type: 'GEOMETRY_NON_EMPTY'; fieldId: string; tolerance: ViolationTolerance }
  | {
      type: 'FORMAT_PATTERN';
      fieldId: string;
      patternKind: FormatPatternKind;
      preset?: FormatPatternPreset;
      regex?: string;
      tolerance: ViolationTolerance;
    }
  | {
      type: 'CONDITIONAL_NOT_NULL';
      targetFieldId: string;
      condition: { fieldId: string; operator: QualityConditionOperator; values: string[] };
      tolerance: ViolationTolerance;
    }
  | {
      type: 'FIELD_COMPARISON';
      leftFieldId: string;
      operator: QualityFieldComparisonOperator;
      rightFieldId: string;
      tolerance: ViolationTolerance;
    }
  | {
      type: 'REFERENCE_EXISTS';
      targetModelId: string;
      mappings: Array<{ sourceFieldId: string; targetFieldId: string }>;
      tolerance: ViolationTolerance;
    };

export interface ModelQualityRuleField {
  id: string;
  code: string;
  name: string;
  fieldType: PlatformDataType;
  standardDictionary?: StandardDictionarySummary | null;
}

export interface ModelQualityRule {
  id: string;
  modelId: string;
  name: string;
  description: string | null;
  ruleType: ModelQualityRuleType;
  severity: ModelQualityRuleSeverity;
  enabled: boolean;
  invalidCode: string | null;
  invalidReason: string | null;
  definition: ModelQualityRuleDefinition;
  fields: ModelQualityRuleField[];
  referenceTarget: ModelQualityRuleReferenceTarget | null;
  createdAt: string;
  updatedAt: string;
}

export interface ModelQualityRuleReferenceTarget {
  id: string;
  code: string;
  name: string;
  status: 'DRAFT' | 'PUBLISHED' | 'DISABLED';
  fields: ModelQualityRuleField[];
}

export interface ModelQualityRuleSuggestion {
  key: string;
  name: string;
  reason: string;
  ruleType: ModelQualityRuleType;
  severity: ModelQualityRuleSeverity;
  definition: ModelQualityRuleDefinition;
  fields: ModelQualityRuleField[];
}

export interface CreateModelQualityRuleRequest {
  name: string;
  description?: string;
  severity: ModelQualityRuleSeverity;
  enabled: boolean;
  definition: ModelQualityRuleDefinition;
}

export type UpdateModelQualityRuleRequest = Omit<CreateModelQualityRuleRequest, 'enabled'>;

export const modelQualityRuleTypeLabels: Record<ModelQualityRuleType, string> = {
  NOT_NULL: '非空',
  UNIQUE: '唯一性',
  VALUE_RANGE: '取值范围',
  STRING_LENGTH: '字符串长度',
  DICTIONARY_MEMBERSHIP: '码表成员',
  ROW_COUNT: '最小行数',
  FRESHNESS: '新鲜度',
  GEOMETRY_VALID: '空间有效',
  GEOMETRY_NON_EMPTY: '空间非空',
  FORMAT_PATTERN: '格式校验',
  CONDITIONAL_NOT_NULL: '条件必填',
  FIELD_COMPARISON: '字段比较',
  REFERENCE_EXISTS: '引用存在',
};

export const formatPatternPresetLabels: Record<FormatPatternPreset, string> = {
  RESIDENT_ID_CARD: '居民身份证',
  UNIFIED_SOCIAL_CREDIT_CODE: '统一社会信用代码',
  MAINLAND_MOBILE_PHONE: '中国大陆手机号',
  EMAIL: '邮箱',
  ADMINISTRATIVE_DIVISION_CODE: '行政区划代码',
};

export const qualityConditionOperatorLabels: Record<QualityConditionOperator, string> = {
  EQ: '等于',
  NE: '不等于',
  IN: '属于',
  NOT_IN: '不属于',
  IS_NULL: '为空值',
  IS_NOT_NULL: '非空值',
  IS_EMPTY: '为空串',
  IS_NOT_EMPTY: '非空串',
};

export const qualityFieldComparisonOperatorLabels: Record<QualityFieldComparisonOperator, string> = {
  EQ: '=',
  NE: '≠',
  LT: '<',
  LE: '≤',
  GT: '>',
  GE: '≥',
};

export const modelQualityRuleSeverityLabels: Record<ModelQualityRuleSeverity, string> = {
  CRITICAL: '关键',
  MAJOR: '重要',
  MINOR: '一般',
};
