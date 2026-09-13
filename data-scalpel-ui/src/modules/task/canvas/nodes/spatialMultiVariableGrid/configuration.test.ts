import { describe, expect, it } from 'vitest';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { createSpatialMultiVariableGridConfiguration } from '../nodeDefaults';

const definition = (schemaMinorVersion: number, configuration: unknown) => ({
  schemaVersion: 4,
  schemaMinorVersion,
  nodes: [{
    id: '11111111-1111-4111-8111-111111111111',
    type: CanvasNodeType.SpatialMultiVariableGrid,
    name: '构建多变量格网',
    layout: { x: 10, y: 20, width: 384, height: 244 },
    configuration,
  }],
  edges: [],
});

describe('spatial multi-variable grid configuration', () => {
  it('round trips at 4.70 and rejects the node below its introduction version', () => {
    const configuration = createSpatialMultiVariableGridConfiguration();
    configuration.outputTableName = 'multi_variable_grid';
    configuration.variables = [{
      variableId: '22222222-2222-4222-8222-222222222222',
      sourceTableName: 'facilities',
      geometryColumnName: 'shape',
      kind: 'DISTANCE_TO_NEAREST',
      attributeColumnName: null,
      statisticKind: null,
      statisticColumnName: null,
      searchDistance: 2,
      searchDistanceUnit: 'KILOMETERS',
      filter: {
        kind: 'PREDICATE',
        columnName: 'status',
        operator: 'EQUALS',
        values: [{ dataType: 'STRING', value: 'active' }],
      },
      outputColumnName: 'facility_distance',
    }];

    const parsed = parseCanvasDefinition(definition(CANVAS_SCHEMA_MINOR_VERSION, configuration));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    expect(parseCanvasDefinition(definition(69, configuration)).success).toBe(false);
  });

  it('retains empty business drafts but rejects unsafe arrays and enums', () => {
    const draft = createSpatialMultiVariableGridConfiguration();
    expect(parseCanvasDefinition(definition(70, draft)).success).toBe(true);
    expect(parseCanvasDefinition(definition(70, { ...draft, variables: 'facilities' })).success)
      .toBe(false);
    expect(parseCanvasDefinition(definition(70, {
      ...draft,
      variables: [{
        variableId: '22222222-2222-4222-8222-222222222222',
        sourceTableName: '', geometryColumnName: '', kind: 'RASTER_BAND',
        attributeColumnName: null, statisticKind: null, statisticColumnName: null,
        searchDistance: null, searchDistanceUnit: null, filter: null, outputColumnName: '',
      }],
    })).success).toBe(false);
  });
});
