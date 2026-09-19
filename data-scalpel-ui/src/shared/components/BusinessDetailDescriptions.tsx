import { Children, Fragment, isValidElement, type ReactNode } from 'react';
import type { DescriptionsProps } from 'antd';

type DescriptionItem = NonNullable<DescriptionsProps['items']>[number];

interface DescriptionItemElementProps {
  label?: ReactNode;
  children?: ReactNode;
  span?: DescriptionItem['span'];
  className?: string;
}

const numericMaximum = (value: unknown, fallback: number): number => {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (!value || typeof value !== 'object') return fallback;
  const values = Object.values(value).filter((entry): entry is number => typeof entry === 'number' && Number.isFinite(entry));
  return values.length > 0 ? Math.max(...values) : fallback;
};

const resolveColumnCount = (column: DescriptionsProps['column']): number => (
  Math.min(4, Math.max(1, numericMaximum(column, 3)))
);

const resolveSpan = (span: DescriptionItem['span'], columnCount: number): number => {
  if (span === 'filled') return columnCount;
  return Math.min(columnCount, Math.max(1, numericMaximum(span, 1)));
};

const collectChildItems = (children: ReactNode, result: DescriptionItem[] = []): DescriptionItem[] => {
  Children.forEach(children, (child) => {
    if (!isValidElement(child)) return;
    if (child.type === Fragment) {
      collectChildItems((child.props as { children?: ReactNode }).children, result);
      return;
    }
    const props = child.props as DescriptionItemElementProps;
    if (props.label === undefined) return;
    result.push({
      key: child.key ?? `item-${result.length}`,
      label: props.label,
      children: props.children,
      span: props.span,
      className: props.className,
    });
  });
  return result;
};

export const BusinessDetailDescriptions = ({
  items,
  children,
  column,
  className,
}: DescriptionsProps) => {
  const columnCount = resolveColumnCount(column);
  const resolvedItems = items ?? collectChildItems(children);
  const rootClassName = Array.from(new Set([
    'business-detail-descriptions',
    `business-detail-descriptions-columns-${columnCount}`,
    className,
  ].filter(Boolean))).join(' ');

  return (
    <dl className={rootClassName}>
      {resolvedItems.map((item, index) => {
        const span = resolveSpan(item.span, columnCount);
        return (
          <div
            key={item.key ?? `item-${index}`}
            className={[
              'business-detail-description-item',
              `business-detail-description-item-span-${span}`,
              item.className,
            ].filter(Boolean).join(' ')}
          >
            <dt className="business-detail-description-label">{item.label}</dt>
            <dd className="business-detail-description-value">{item.children ?? '—'}</dd>
          </div>
        );
      })}
    </dl>
  );
};
