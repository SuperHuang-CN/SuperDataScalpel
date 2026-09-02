import type { Graph, Node } from '@antv/x6';
import type { CanvasDefinition, CanvasTableSchema } from './canvasTypes';

export interface CanvasNodeTrialTarget {
  taskId: string;
  baseDefinitionVersion: number;
  definition: CanvasDefinition;
  nodeId: string;
  nodeName: string;
  inputTables: readonly CanvasTableSchema[];
  outputTables: readonly CanvasTableSchema[];
  initialTableName?: string;
}

export interface CanvasNodeOutputSchemaTarget {
  nodeId: string;
  nodeName: string;
  inputTables: readonly CanvasTableSchema[];
  outputTables: readonly CanvasTableSchema[];
}

interface CanvasNodeOutputSchemaController {
  openOutputSchema: (node: Node) => void;
}

const controllers = new WeakMap<Graph, CanvasNodeOutputSchemaController>();

export const registerCanvasNodeOutputSchemaController = (
  graph: Graph,
  controller: CanvasNodeOutputSchemaController,
): (() => void) => {
  controllers.set(graph, controller);
  return () => {
    if (controllers.get(graph) === controller) {
      controllers.delete(graph);
    }
  };
};

export const requestCanvasNodeOutputSchema = (
  graph: Graph,
  node: Node,
): void => controllers.get(graph)?.openOutputSchema(node);
