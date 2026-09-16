import type { ModelOutputConfiguration } from "../../canvasTypes";

export const createModelOutputConfiguration = (): ModelOutputConfiguration => ({
  writes: [],
  sourceTableName: '', targetModelId: '', writeMode: 'OVERWRITE', columnMappings: [],
});
