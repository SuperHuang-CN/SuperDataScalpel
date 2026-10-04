import { useState } from 'react';
import { Tooltip } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { number } from '../model/dashboardFormat';
import type { CategoryBarDatum } from './CategoryBarChart';

const taskColors = ['#3788a5', '#5b9b87', '#c3934c', '#7185a6'];
const triggers: ('hover' | 'focus' | 'click')[] = ['hover', 'focus', 'click'];

export function TaskDistributionChart({ data }: { data: CategoryBarDatum[] }) {
  const [active, setActive] = useState<string>();
  const navigate = useNavigate();
  const total = data.reduce((sum, item) => sum + item.count, 0);
  const selected = data.find(item => item.key === active);
  const share = (count: number) => total ? `${(count / total * 100).toFixed(1)}%` : '—';
  const description = (item: CategoryBarDatum) => `${item.label}：${number(item.count)} 个，占比 ${share(item.count)}；${item.detail ?? ''}`;

  return <div className="home-distribution-chart">
    <div className="home-category-caption"><span>任务定义 · 类型占比</span><span>个</span></div>
    <div className="home-task-distribution">
      <div className="home-task-ring">
        <svg viewBox="0 0 160 160" aria-label="任务类型占比">
          <circle cx="80" cy="80" r="58" fill="none" stroke="#e8eff3" strokeWidth="22" />
          {total > 0 && data.map((item, index) => {
            if (!item.count) return null;
            const preceding = data.slice(0, index).reduce((sum, row) => sum + row.count, 0);
            return <Tooltip key={item.key} title={description(item)} trigger={triggers}>
              <circle cx="80" cy="80" r="58" fill="none" stroke={taskColors[index % taskColors.length]}
                strokeWidth="22" pathLength="100" strokeDasharray={`${item.count / total * 100} 100`}
                strokeDashoffset={-preceding / total * 100} transform="rotate(-90 80 80)"
                className="home-task-segment" opacity={active && active !== item.key ? 0.3 : 1}
                tabIndex={0} role="link" aria-label={description(item)}
                onMouseEnter={() => setActive(item.key)} onMouseLeave={() => setActive(undefined)}
                onFocus={() => setActive(item.key)} onBlur={() => setActive(undefined)}
                onClick={() => navigate(item.href)} onKeyDown={event => {
                  if (event.key === 'Enter') navigate(item.href);
                }} />
            </Tooltip>;
          })}
        </svg>
        <div className="home-ring-center" aria-hidden="true">
          <strong>{selected ? share(selected.count) : number(total)}</strong>
          <span>{selected ? selected.label : total ? '任务总数' : '暂无任务'}</span>
        </div>
      </div>
      <div className="home-task-key">
        {data.map((item, index) => <Tooltip key={item.key} title={description(item)} trigger={triggers}>
          <Link to={item.href} aria-label={description(item)} className={active === item.key ? 'is-active' : undefined}
            onMouseEnter={() => setActive(item.key)} onMouseLeave={() => setActive(undefined)}
            onFocus={() => setActive(item.key)} onBlur={() => setActive(undefined)}>
            <i style={{ background: taskColors[index % taskColors.length] }} />
            <span>{item.label}</span><strong>{number(item.count)}</strong>
            <small>{item.detail}</small>
          </Link>
        </Tooltip>)}
      </div>
    </div>
    <div className="home-distribution-note">{total ? '悬停查看占比 · 点击查看任务' : '创建任务后显示类型占比'}</div>
  </div>;
}

export function ServiceDistributionChart({ data }: { data: CategoryBarDatum[] }) {
  const maximum = Math.max(1, ...data.map(item => item.count));
  return <div className="home-distribution-chart">
    <div className="home-category-caption"><span>已启用服务 · 类型对比</span><span>个</span></div>
    <div className="home-service-distribution">
      <div className="home-service-scale" aria-hidden="true"><span>{number(maximum)}</span><span>0</span></div>
      <div className="home-service-columns">
        {data.map(item => <Tooltip key={item.key} trigger={triggers}
          title={`${item.label}：已启用 ${number(item.count)} 个，其中部署异常 ${number(item.errorCount ?? 0)} 个`}>
          <Link to={item.href} className="home-service-column"
            aria-label={`${item.label}：已启用 ${number(item.count)}，部署异常 ${number(item.errorCount ?? 0)}`}>
            <div className="home-service-column-plot">
              <div className="home-service-column-bar" style={{ height: `${item.count / maximum * 100}%` }}>
                <strong>{number(item.count)}</strong>
                <i style={{ height: `${item.count ? Math.min(item.errorCount ?? 0, item.count) / item.count * 100 : 0}%` }} />
              </div>
            </div>
            <span>{item.label}</span>
          </Link>
        </Tooltip>)}
      </div>
    </div>
    <div className="home-distribution-note home-service-key"><span><i />已启用</span><span><i />其中部署异常</span></div>
  </div>;
}
