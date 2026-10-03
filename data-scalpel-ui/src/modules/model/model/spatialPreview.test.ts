import { describe, expect, it } from 'vitest';
import { covers, expandBounds, PreviewImageCache, toX, toY, toLatitude, toLongitude, WORLD, type PreviewImage } from './spatialPreview';
describe('spatial image reuse', () => {
  it('reuses overlap only within the same generation and adequate resolution', () => {
    const cache = new PreviewImageCache();
    const image: PreviewImage = { blob: new Blob(['png']), bounds: [0, 0, 100, 100], generation: 'a', overview: false, degraded: false, resolution: 1 };
    cache.put(image);
    expect(cache.find('a', [10, 10, 90, 90], 1)).toBe(image);
    expect(cache.find('b', [10, 10, 90, 90], 1)).toBeUndefined();
    expect(cache.find('a', [10, 10, 90, 90], 0.1)).toBeUndefined();
    expect(cache.find('a', [-1, 10, 90, 90], 1)).toBeUndefined();
    cache.clear(); expect(cache.find('a', [10, 10, 90, 90], 1)).toBeUndefined();
  });
  it('bounds external requests and roundtrips map coordinates', () => {
    expect(covers(expandBounds([0, 0, 100, 100]), [0, 0, 100, 100])).toBe(true);
    expect(expandBounds([-WORLD, -WORLD, WORLD, WORLD])).toEqual([-WORLD, -WORLD, WORLD, WORLD]);
    expect(toLongitude(toX(116.3))).toBeCloseTo(116.3, 8); expect(toLatitude(toY(39.8))).toBeCloseTo(39.8, 8);
  });
});
