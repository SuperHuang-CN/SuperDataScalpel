import { describe, expect, it } from 'vitest';
import { parseCanvasDefinition } from '../../canvasDefinitionIO';
import { CANVAS_SCHEMA_MINOR_VERSION, CanvasNodeType } from '../../canvasTypes';
import { createSpatialSimilarLocationsConfiguration } from '../nodeDefaults';

const definition = (schemaMinorVersion: number, configuration: unknown) => ({
  schemaVersion: 4,
  schemaMinorVersion,
  nodes: [{
    id: '11111111-1111-4111-8111-111111111111',
    type: CanvasNodeType.SpatialSimilarLocations,
    name: '查找相似位置',
    layout: { x: 10, y: 20, width: 392, height: 244 },
    configuration,
  }],
  edges: [],
});

describe('spatial similar locations configuration', () => {
  it('round trips at 4.75 and rejects the node below its introduction version', () => {
    const configuration = createSpatialSimilarLocationsConfiguration();
    Object.assign(configuration, {
      referenceTableName: 'reference_places',
      referenceIdColumnName: 'reference_id',
      referenceGeometryColumnName: 'shape',
      candidateTableName: 'candidate_places',
      candidateIdColumnName: 'candidate_id',
      candidateGeometryColumnName: 'shape',
      analysisFields: [{ columnName: 'population', outputColumnName: 'population' }],
      appendFields: [{ sourceColumnName: 'name', outputColumnName: 'candidate_name' }],
      resultMode: 'BOTH',
      outputTableName: 'similar_places',
    });

    const parsed = parseCanvasDefinition(definition(CANVAS_SCHEMA_MINOR_VERSION, configuration));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.definition.nodes[0].configuration).toEqual(configuration);
    expect(parseCanvasDefinition(definition(74, configuration)).success).toBe(false);
  });

  it('keeps incomplete business drafts but rejects unsafe structures', () => {
    const draft = createSpatialSimilarLocationsConfiguration();
    expect(parseCanvasDefinition(definition(75, draft)).success).toBe(true);
    expect(parseCanvasDefinition(definition(75, { ...draft, analysisFields: 'population' })).success)
      .toBe(false);
    expect(parseCanvasDefinition(definition(75, { ...draft, matchMethod: 'RANKED_ATTRIBUTE_VALUES' })).success)
      .toBe(false);
    expect(parseCanvasDefinition(definition(75, { ...draft, numberOfResults: 1.5 })).success)
      .toBe(false);
  });
});
