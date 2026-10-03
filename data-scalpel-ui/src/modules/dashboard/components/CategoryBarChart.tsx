import { useState } from 'react';
import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import { Button, Tooltip } from 'antd';
import { Link } from 'react-router-dom';
import { number } from '../model/dashboardFormat';

export interface CategoryBarDatum {
  key: string;
  label: string;
  count: number;
  href: string;
  detail?: string;
  errorCount?: number;
}

/** Paginate categories without aggregating away user-defined layers or changing the scale. */
export const CategoryBarChart = ({ data, label, pageSize = 6 }: {
  data: CategoryBarDatum[]; label: string; pageSize?: number;
}) => {
  const [requestedPage, setPage] = useState(0);
  const pages = Math.max(1, Math.ceil(data.length / pageSize));
  const page = Math.min(requestedPage, pages - 1);
  const visible = data.slice(page * pageSize, (page + 1) * pageSize);
  const maximum = Math.max(1, ...data.map(item => item.count));
  return <div className="home-category-chart" role="group" aria-label={label}>
    <div className="home-category-caption"><span>{label}</span><span>数量</span></div>
    <div className="home-category-plot" style={{ gridTemplateRows: `repeat(${Math.max(1, visible.length)}, minmax(0, 1fr))` }}>
      {visible.map(item => <Tooltip key={item.key} trigger={['hover', 'focus']} title={<>{item.label}：{number(item.count)}{item.detail && <><br />{item.detail}</>}</>}>
        <Link className="home-category-row" to={item.href} aria-label={`${item.label}：${number(item.count)}${item.detail ? `，${item.detail}` : ''}`}>
          <span className="home-category-name">{item.label}</span>
          <span className="home-category-track" aria-hidden>
            <span className="home-category-fill" style={{ width: `${item.count / maximum * 100}%` }}>
              {Boolean(item.errorCount) && item.count > 0 && <i style={{ width: `${Math.min(item.errorCount!, item.count) / item.count * 100}%` }} />}
            </span>
          </span>
          <strong>{number(item.count)}</strong>
          {item.detail && <small className={item.errorCount ? 'home-danger' : ''}>{item.detail}</small>}
        </Link>
      </Tooltip>)}
      {!visible.length && <span className="home-category-empty">暂无分类数据</span>}
    </div>
    <div className="home-category-pagination">
      <span>{pages > 1 ? `${page * pageSize + 1}–${Math.min((page + 1) * pageSize, data.length)} / 共 ${data.length} 类` : `共 ${data.length} 类`}</span>
      {data.some(item => item.errorCount !== undefined) && <span className="home-category-error-key"><i />部署异常</span>}
      {pages > 1 && <div>
        <Button type="text" size="small" icon={<LeftOutlined />} disabled={page === 0} aria-label={`${label}上一页`} onClick={() => setPage(page - 1)} />
        <span aria-live="polite">{page + 1} / {pages}</span>
        <Button type="text" size="small" icon={<RightOutlined />} disabled={page === pages - 1} aria-label={`${label}下一页`} onClick={() => setPage(page + 1)} />
      </div>}
    </div>
  </div>;
};
