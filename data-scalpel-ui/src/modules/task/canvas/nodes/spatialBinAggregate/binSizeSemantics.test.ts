import { describe, expect, it } from 'vitest';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { CanvasNodeType } from '../../canvasTypes';
import { createSpatialBinAggregateConfiguration } from '../nodeDefaults';
import { binSizeLabel, parseBinSizeSemantics } from './binSizeSemantics';

describe('explicit hexagon size semantics', () => {
  it('defaults new nodes to flat-to-flat but labels absent legacy semantics as side length', () => {
    expect(createSpatialBinAggregateConfiguration().binSizeSemantics).toBe('HEXAGON_FLAT_TO_FLAT');
    expect(binSizeLabel({ binShape: 'HEXAGON' })).toBe('边长（旧版）');
    expect(binSizeLabel({ binShape: 'HEXAGON', binSizeSemantics: 'HEXAGON_FLAT_TO_FLAT' })).toBe('对边距离');
    expect(binSizeLabel({ binShape: 'SQUARE', binSizeSemantics: 'HEXAGON_FLAT_TO_FLAT' })).toBe('边长');
    const errors: string[] = [];
    parseBinSizeSemantics('DIAMETER', 'configuration.binSizeSemantics', errors);
    expect(errors).toHaveLength(1);
  });

  it('round-trips the explicit option only from 4.21 and preserves old missing options', () => {
    const configuration = createSpatialBinAggregateConfiguration();
    const definition = { schemaVersion: 4, schemaMinorVersion: 21, edges: [], nodes: [{
      id: '11111111-1111-4111-8111-111111111111', type: CanvasNodeType.SpatialBinAggregate, name: '格网',
      layout: { x: 80, y: 90, width: 320, height: 200 }, configuration,
    }] };
    expect(parseCanvasDefinition(definition).success).toBe(true);
    expect(parseCanvasDefinition({ ...definition, schemaMinorVersion: 20 }).success).toBe(false);
    delete configuration.binSizeSemantics;
    const legacy = parseCanvasDefinition({ ...definition, schemaMinorVersion: 18 });
    expect(legacy.success).toBe(true);
    if (legacy.success) expect(legacy.definition.nodes[0].configuration).not.toHaveProperty('binSizeSemantics');
  });
});
