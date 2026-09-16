import { isRecord } from './scalars';

export const parseLayout = (value: unknown, path: string, errors: string[]) => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是布局对象`);
    return null;
  }
  const coordinates = ['x', 'y', 'width', 'height'] as const;
  if (coordinates.some((key) => typeof value[key] !== 'number' || !Number.isFinite(value[key]))) {
    errors.push(`${path} 必须包含有限数值 x、y、width、height`);
    return null;
  }
  const x = value.x as number;
  const y = value.y as number;
  const width = value.width as number;
  const height = value.height as number;
  if (x < -100_000 || x > 100_000 || y < -100_000 || y > 100_000
    || width < 180 || width > 1000 || height < 96 || height > 1000) {
    errors.push(`${path} 超出允许范围`);
    return null;
  }
  return { x, y, width, height };
};
