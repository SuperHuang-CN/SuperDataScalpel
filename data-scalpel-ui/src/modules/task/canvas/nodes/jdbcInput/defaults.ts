import type { JdbcInputConfiguration } from "../../canvasTypes";

export const createJdbcInputConfiguration = (): JdbcInputConfiguration => ({
  dataSourceId: '',
  tables: [],
});
