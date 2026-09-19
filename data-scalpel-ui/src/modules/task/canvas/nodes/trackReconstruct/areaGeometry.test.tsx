import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, expect, it } from 'vitest';
import { CanvasNodeType, type CanvasColumnSchema, type SpatialDistanceMethod } from '../../canvasTypes';
import { createTrackReconstructConfiguration } from '../nodeDefaults';
import { areaGeometryProblems, createAreaGeometryOptions } from './areaGeometry';
import { trackReconstructCanvasView } from './canvasView';
import { summarizeTrackReconstruct } from '../nodeSummaries';
import { AreaGeometryEditor } from './AreaGeometryEditor';

afterEach(cleanup);
it('edits independent geodesic sampling and retains it when the method becomes inactive', () => {
  function Harness() {
    const [value, setValue] = useState(createAreaGeometryOptions(true));
    const [method, setMethod] = useState<SpatialDistanceMethod>('GEODESIC');
    return <><button onClick={() => setMethod(method === 'GEODESIC' ? 'PLANAR' : 'GEODESIC')}>切换方法</button>
      <AreaGeometryEditor value={value} onChange={setValue} geometry={undefined} columns={[]}
        validationAvailable={false} distanceMethod={method} />
      <output>{JSON.stringify(value)}</output></>;
  }
  render(<Harness />);
  const input = screen.getByRole('spinbutton', { name: '面边界采样最大段长' });
  expect(input).toHaveValue('');
  fireEvent.change(input, { target: { value: '200' } });
  expect(screen.getByRole('status')).toHaveTextContent('"maximumSegmentLength":200');
  fireEvent.click(screen.getByText('切换方法'));
  expect(screen.queryByRole('spinbutton', { name: '面边界采样最大段长' })).not.toBeInTheDocument();
  expect(screen.getByRole('status')).toHaveTextContent('"maximumSegmentLength":200');
  fireEvent.click(screen.getByText('切换方法'));
  expect(screen.getByRole('spinbutton', { name: '面边界采样最大段长' })).toHaveValue('200');
});

it('validates sampling only in the geodesic branch and does not guess a radius or sampling length', () => {
  const value = { ...createAreaGeometryOptions(true), geodesicBoundary: { maximumSegmentLength: -1, maximumSegmentLengthUnit: null } };
  expect(areaGeometryProblems(value, undefined, [], false, 'PLANAR')).toEqual([]);
  expect(areaGeometryProblems(value, undefined, [], false, 'GEODESIC')).toHaveLength(2);
});
it('keeps unresolved fields distinct from absent upstream fields', () => {
  const value = { ...createAreaGeometryOptions(false), bufferField: 'missing' };
  expect(areaGeometryProblems(value, undefined, [], false)).toEqual([]);
  expect(areaGeometryProblems(value, undefined, [], true)).toContain('缓冲距离字段已失效');
  const geometry = { name:'shape', fieldType:'GEOMETRY', geometry:{ kind:'POINT',dimension:'XY',crs:{authority:'EPSG',code:3857} } } as CanvasColumnSchema;
  expect(areaGeometryProblems({ ...value,bufferMode:'NONE' },geometry,[],true)).toContain('点生成面轨迹时必须配置缓冲距离');
});

it('renders area cards and summaries without exposing buffer expressions or distances', () => {
  const configuration = { ...createTrackReconstructConfiguration(),sourceTableName:'events',outputTableName:'tracks' };
  configuration.reconstruction!.areaGeometry = { ...createAreaGeometryOptions(false),bufferMode:'EXPRESSION',bufferExpression:'secret_radius * 987654' };
  const data = { type:CanvasNodeType.TrackReconstruct,name:'重建',configuration };
  const Body = trackReconstructCanvasView.Body;
  const { container } = render(<Body data={data} />);
  expect(screen.getByText('观测 → MultiPolygon')).toBeInTheDocument();
  expect(screen.getByText('表达式缓冲')).toBeInTheDocument();
  expect(container).not.toHaveTextContent('secret_radius'); expect(container).not.toHaveTextContent('987654');
  expect(summarizeTrackReconstruct(data)).toContain('面轨迹');
  expect(summarizeTrackReconstruct(data)).not.toContain('987654');
  expect(trackReconstructCanvasView.resolveSize(configuration).height).toBeGreaterThan(104);
});
