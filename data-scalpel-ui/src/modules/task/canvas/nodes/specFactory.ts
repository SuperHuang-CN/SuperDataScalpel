import type { CanvasNodeType } from '../canvasTypes';
import { parseCanvasNodeConfiguration } from './nodeConfigurationParser';
import { defineCanvasNodeSpec, type CanvasNodeSpec } from './nodeSpec';

type CanvasNodeSpecDeclaration<T extends CanvasNodeType> =
  Omit<CanvasNodeSpec<T>, 'parseConfiguration'>;

export const createCanvasNodeSpec = <T extends CanvasNodeType>(
  declaration: CanvasNodeSpecDeclaration<T>,
): CanvasNodeSpec<T> => defineCanvasNodeSpec({
  ...declaration,
  parseConfiguration: (value, path) => parseCanvasNodeConfiguration(
    declaration.type,
    value,
    path,
  ),
});
