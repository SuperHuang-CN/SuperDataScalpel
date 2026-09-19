import type { DeriveColumnsConfiguration } from "../../canvasTypes";

export const createDeriveColumnsConfiguration = (): DeriveColumnsConfiguration => ({
  globalDerivations: [],
  operations: [],
} as unknown as DeriveColumnsConfiguration);
