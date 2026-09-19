import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SpatialScaleEditor } from './SpatialScaleEditor';
import { SpatialSymbolSwatch } from './SpatialStyleLegend';
import { buildWmsViewport, documentScaleError, intersectScale, isScaleVisible } from './scale';
import { applyRamp, changeClassBreakRange, changeClassBreakVisualChannel, defaultStyleDocument, defaultSymbol, uniqueRenderer } from './style';
import type { ClassBreaksRenderer, FieldProfile } from './model';

afterEach(cleanup);

describe('cartography V4', () => {
  it('uses inclusive minimum, exclusive maximum and intersects labeling scales', () => {
    const range = { minScaleDenominator: 1000, maxScaleDenominator: 10000 };
    expect(isScaleVisible(range, 1000)).toBe(true);
    expect(isScaleVisible(range, 10000)).toBe(false);
    expect(intersectScale(range, { minScaleDenominator: 2000, maxScaleDenominator: null }))
      .toEqual({ minScaleDenominator: 2000, maxScaleDenominator: 10000 });
    const document = defaultStyleDocument('POINT');
    document.scaleRange = range; document.labeling.enabled = true;
    document.labeling.scaleRange = { minScaleDenominator: 10000, maxScaleDenominator: 20000 };
    expect(documentScaleError(document)).toContain('交集');
    document.scaleRange.minScaleDenominator = Infinity;
    expect(documentScaleError(document)).toContain('有限正数');
  });

  it('fits image limits without non-square pixels and computes OGC request scale', () => {
    const limits = { minimumWidth: 256, maximumWidth: 1600, minimumHeight: 256, maximumHeight: 1200 };
    const request = buildWmsViewport([0, 0, 4000, 2000], 2000, 1000, limits);
    expect(request.width).toBe(1600); expect(request.height).toBe(800);
    expect(request.scaleDenominator).toBeCloseTo(2.5 / 0.00028);
    const fractional = buildWmsViewport([0, 0, 1999, 1001], 1999, 1001, limits);
    expect((fractional.bbox[2] - fractional.bbox[0]) / fractional.width)
      .toBeCloseTo((fractional.bbox[3] - fractional.bbox[1]) / fractional.height);
    expect(() => buildWmsViewport([0, 0, 1000, 10], 1000, 10, limits)).toThrow('狭长');
  });

  it('keeps casing when recoloring or changing magnitude channel/range', () => {
    const symbol = { ...defaultSymbol('LINE'), width: 4, casing: { color: '#FFFFFF', width: 2, opacity: 0.8 } };
    const renderer: ClassBreaksRenderer = { type: 'CLASS_BREAKS', fieldCode: 'value', classificationMethod: 'MANUAL',
      visualChannel: 'COLOR', colorRamp: { id: 'BLUES', reversed: false }, sizeRange: null, breaks: ['10'],
      classBreakRules: [{ id: 'one', label: 'one', symbol }, { id: 'two', label: 'two', symbol }], nullRule: null };
    const width = changeClassBreakVisualChannel(renderer, 'LINE', 'WIDTH');
    const larger = changeClassBreakRange(width, 'LINE', { minimum: 2, maximum: 10 });
    expect(larger.classBreakRules[1].symbol).toMatchObject({ width: 10, casing: symbol.casing });
    const recolored = applyRamp(renderer, { id: 'GREENS', reversed: true });
    if (recolored.type !== 'CLASS_BREAKS') throw new Error('分级类型不应改变');
    expect(recolored.classBreakRules[0].symbol).toMatchObject({ width: 4, casing: symbol.casing });
  });

  it('keeps expanded polar viewports inside Mercator bounds without changing pixel scale', () => {
    const limits = { minimumWidth: 256, maximumWidth: 1600, minimumHeight: 256, maximumHeight: 1200 };
    const edge = 20037508.342789244;
    const request = buildWmsViewport([0, edge - 100, 1000, edge], 800, 600, limits);
    expect(request.bbox[3]).toBeLessThanOrEqual(edge);
    expect((request.bbox[2] - request.bbox[0]) / request.width)
      .toBeCloseTo((request.bbox[3] - request.bbox[1]) / request.height);
    expect(() => buildWmsViewport([-edge, -edge, edge, edge], 800, 600, limits)).toThrow('请放大');
  });

  it('keeps matching unique-value patterns and renders pattern and casing swatches', () => {
    const polygon = { ...defaultSymbol('POLYGON'), pattern: { type: 'DOT' as const, color: '#123456', opacity: 0.8, spacing: 12, dotSize: 2, strokeWidth: 1 } };
    const profile: FieldProfile = { field: { code: 'category', name: '类别', dataType: 'STRING', valueType: 'STRING', uniqueValueSupported: true, classBreaksSupported: false, labelSupported: true },
      totalRowCount: 2, nonNullCount: 2, nullCount: 0, uniqueValues: [{ valueType: 'STRING', value: 'A', count: 2 }], truncated: false,
      minimum: null, maximum: null, breaks: [], actualClassCount: 0, warnings: [] };
    const original = uniqueRenderer(profile, 'POLYGON');
    original.uniqueValueRules[0] = { ...original.uniqueValueRules[0], label: '自定义', symbol: polygon };
    expect(uniqueRenderer(profile, 'POLYGON', original).uniqueValueRules[0]).toEqual(original.uniqueValueRules[0]);
    render(<SpatialSymbolSwatch symbol={polygon} />);
    expect(screen.getByLabelText('面符号示意').querySelector('pattern circle')).not.toBeNull();
    render(<SpatialSymbolSwatch symbol={{ ...defaultSymbol('LINE'), casing: { color: '#FFFFFF', opacity: 1, width: 2 } }} />);
    expect(screen.getByLabelText('线符号示意').querySelectorAll('line')).toHaveLength(2);
  });

  it('disables label scale without labeling and fills a bound from the current request', () => {
    const change = vi.fn();
    render(<SpatialScaleEditor document={defaultStyleDocument('POINT')} disabled={false} currentScale={12345} onChange={change} />);
    expect(screen.getByLabelText('标注可见范围最小分母')).toBeDisabled();
    fireEvent.click(screen.getAllByRole('button', { name: '当前视图' })[0]);
    expect(change).toHaveBeenCalledWith(expect.objectContaining({ scaleRange: { minScaleDenominator: 12345, maxScaleDenominator: null } }));
  });
});
