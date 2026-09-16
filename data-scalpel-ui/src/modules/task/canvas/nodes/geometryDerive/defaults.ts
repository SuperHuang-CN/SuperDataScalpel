import type { GeometryDeriveConfiguration } from "../../canvasTypes";

export const createGeometryDeriveConfiguration = (): GeometryDeriveConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  derivations: [],
});
