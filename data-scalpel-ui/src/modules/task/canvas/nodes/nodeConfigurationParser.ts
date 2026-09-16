import type { CanvasNodeType } from '../canvasTypes';
import { canvasNodeRegistry } from './nodeRegistry';

/** Compatibility entry point; each registered node owns its configuration parser. */
export const parseCanvasNodeConfiguration = <T extends CanvasNodeType>(type: T, value: unknown, path: string) => (
  canvasNodeRegistry.require(type).parseConfiguration(value, path)
);
