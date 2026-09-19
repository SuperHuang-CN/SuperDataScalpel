import { describe, expect, it } from 'vitest';
import type { DataModelField } from '../../model';
import {
  modelFieldsToKafkaValueSchema,
  parseKafkaJsonSchema,
} from './kafkaValueSchema';

const modelField = (
  overrides: Partial<DataModelField>,
): DataModelField => ({
  id: '50e3d5d6-d7fc-4215-a5ae-9db7cc89e4ce',
  modelId: '51e9828b-ed7d-4817-bebb-d8ce01b849e9',
  code: 'event_id',
  name: '事件 ID',
  fieldType: 'LONG',
  length: null,
  precision: null,
  scale: null,
  nullable: false,
  primaryKey: false,
  sortOrder: 0,
  description: null,
  createdAt: '2026-07-24T00:00:00Z',
  updatedAt: '2026-07-24T00:00:00Z',
  ...overrides,
});

describe('Kafka Value Schema', () => {
  it('copies the current model fields by sort order without retaining a model reference', () => {
    const schema = modelFieldsToKafkaValueSchema([
      modelField({
        id: 'd3f0a3d2-f14f-4078-87d9-bf7afbb1ea4d',
        code: 'description',
        name: '说明',
        fieldType: 'STRING',
        length: 128,
        nullable: true,
        sortOrder: 1,
        description: '事件说明',
      }),
      modelField({ sortOrder: 0 }),
    ]);

    expect(schema).toEqual({
      columns: [{
        name: 'event_id',
        fieldType: 'LONG',
        length: null,
        precision: null,
        scale: null,
        nullable: false,
        comment: null,
      }, {
        name: 'description',
        fieldType: 'STRING',
        length: 128,
        precision: null,
        scale: null,
        nullable: true,
        comment: '事件说明',
      }],
    });
    expect(schema).not.toHaveProperty('modelId');
  });

  it('imports a flat JSON Schema into platform scalar fields', () => {
    const result = parseKafkaJsonSchema(JSON.stringify({
      type: 'object',
      required: ['id', 'created_at'],
      properties: {
        id: { type: 'integer', format: 'int64', description: '事件 ID' },
        score: { type: ['number', 'null'], format: 'float' },
        name: { type: 'string', maxLength: 64 },
        created_at: { type: 'string', format: 'date-time' },
      },
    }));

    expect(result).toEqual({
      success: true,
      schema: {
        columns: [{
          name: 'id',
          fieldType: 'LONG',
          length: null,
          precision: null,
          scale: null,
          nullable: false,
          comment: '事件 ID',
        }, {
          name: 'score',
          fieldType: 'FLOAT',
          length: null,
          precision: null,
          scale: null,
          nullable: true,
          comment: null,
        }, {
          name: 'name',
          fieldType: 'STRING',
          length: 64,
          precision: null,
          scale: null,
          nullable: true,
          comment: null,
        }, {
          name: 'created_at',
          fieldType: 'TIMESTAMP',
          length: null,
          precision: null,
          scale: null,
          nullable: false,
          comment: null,
        }],
      },
    });
  });

  it('rejects malformed JSON and nested object or array fields', () => {
    expect(parseKafkaJsonSchema('{')).toEqual(expect.objectContaining({ success: false }));

    const nested = parseKafkaJsonSchema(JSON.stringify({
      type: 'object',
      properties: {
        customer: { type: 'object', properties: { id: { type: 'integer' } } },
        lines: { type: 'array', items: { type: 'string' } },
      },
    }));

    expect(nested.success).toBe(false);
    if (!nested.success) {
      expect(nested.errors).toEqual([
        'properties.customer 暂不支持嵌套 object 或 array',
        'properties.lines 暂不支持嵌套 object 或 array',
      ]);
    }
  });
});
