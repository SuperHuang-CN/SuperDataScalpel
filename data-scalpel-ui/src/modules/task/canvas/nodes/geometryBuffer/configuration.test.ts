import { describe, expect, it } from 'vitest';
import {
  CANVAS_SCHEMA_MINOR_VERSION,
  CanvasNodeType,
} from '../../canvasTypes';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { createGeometryBufferConfiguration } from '../nodeDefaults';

const definition = (configuration: unknown, minor: number = CANVAS_SCHEMA_MINOR_VERSION) => ({
  schemaVersion: 4,
  schemaMinorVersion: minor,
  nodes: [{
    id: '11111111-1111-4111-8111-111111111111',
    type: CanvasNodeType.GeometryBuffer,
    name: 'Geometry Buffer',
    layout: { x: 0, y: 0, width: 320, height: 188 },
    configuration,
  }],
  edges: [],
});

describe('geometry buffer distance source', () => {
  it('defaults new nodes to constant distance and gates explicit sources at 4.52', () => {
    const configuration = createGeometryBufferConfiguration();
    expect(configuration.distanceSource).toBe('CONSTANT');
    expect(parseCanvasDefinition(definition(configuration, 51)).success).toBe(false);
    expect(parseCanvasDefinition(definition(configuration, 52)).success).toBe(true);

    for (const source of ['FIELD', 'EXPRESSION'] as const) {
      expect(parseCanvasDefinition(definition({
        ...configuration,
        distanceSource: source,
        distanceFieldName: source === 'FIELD' ? 'radius' : null,
        distanceExpression: source === 'EXPRESSION' ? 'radius * 2' : null,
      }, 51)).success).toBe(false);
    }
  });

  it('normalizes missing fields to legacy constant semantics and preserves inactive drafts', () => {
    const configuration = createGeometryBufferConfiguration();
    delete configuration.distanceSource;
    delete configuration.distanceFieldName;
    delete configuration.distanceExpression;
    const parsed = parseCanvasDefinition(definition(configuration, 51));
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.definition.nodes[0].configuration).toMatchObject({
        distanceSource: null,
        distanceFieldName: null,
        distanceExpression: null,
      });
    }

    const expression = parseCanvasDefinition(definition({
      ...configuration,
      distanceSource: 'EXPRESSION',
      distanceFieldName: 'saved_radius',
      distanceExpression: 'coalesce(radius, 100)',
    }));
    expect(expression.success).toBe(true);
    if (expression.success) {
      expect(expression.definition.nodes[0].configuration).toMatchObject({
        distanceSource: 'EXPRESSION',
        distanceFieldName: 'saved_radius',
        distanceExpression: 'coalesce(radius, 100)',
      });
    }
  });

  it('rejects malformed source, field and expression structures', () => {
    const configuration = createGeometryBufferConfiguration();
    for (const invalid of [
      { ...configuration, distanceSource: 'AUTO' },
      { ...configuration, distanceFieldName: [] },
      { ...configuration, distanceExpression: {} },
    ]) {
      expect(parseCanvasDefinition(definition(invalid)).success).toBe(false);
    }
  });
});
