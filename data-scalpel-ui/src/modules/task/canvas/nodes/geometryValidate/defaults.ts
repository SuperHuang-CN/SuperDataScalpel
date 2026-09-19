import type { GeometryValidateConfiguration } from "../../canvasTypes";

export const createGeometryValidateConfiguration = (): GeometryValidateConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  geometryColumnName: '',
  validColumnName: 'geometry_valid',
  reasonColumnName: null,
});
