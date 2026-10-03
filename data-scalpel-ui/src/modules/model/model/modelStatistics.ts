export interface ModelStatistics {
 collectedAt: string; published: number; draft: number; managed: number; external: number;
 layers: { id: string | null; name: string; code: string | null; count: number }[];
}
