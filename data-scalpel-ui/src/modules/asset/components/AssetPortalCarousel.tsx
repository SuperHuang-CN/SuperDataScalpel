import { ArrowDownOutlined, ArrowLeftOutlined, ArrowRightOutlined, ArrowUpOutlined } from '@ant-design/icons';
import { useEffect, useRef, useState, type ReactNode } from 'react';

/** Discrete groups keep resources readable; offscreen slides never receive keyboard focus. */
export const AssetPortalCarousel = ({ pages, label, interval, vertical = false, total }: { pages: ReactNode[]; label: string; interval: number; vertical?: boolean; total: number }) => {
  const root = useRef<HTMLDivElement>(null);
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const [reduced, setReduced] = useState(() => window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [visible, setVisible] = useState(false);
  const [hidden, setHidden] = useState(() => document.hidden);
  const [announcement, setAnnouncement] = useState('');
  const count = pages.length;
  const active = count ? index % count : 0;
  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onMedia = () => setReduced(media.matches);
    const onVisibility = () => setHidden(document.hidden);
    const observer = new IntersectionObserver(entries => setVisible(entries[0].isIntersecting), { threshold: .25 });
    if (root.current) observer.observe(root.current);
    media.addEventListener('change', onMedia);
    document.addEventListener('visibilitychange', onVisibility);
    return () => { observer.disconnect(); media.removeEventListener('change', onMedia); document.removeEventListener('visibilitychange', onVisibility); };
  }, []);
  useEffect(() => {
    if (count <= 1 || paused || reduced || hovered || focused || !visible || hidden) return;
    const timer = window.setTimeout(() => setIndex(current => (current + 1) % count), interval);
    return () => window.clearTimeout(timer);
  }, [count, index, paused, reduced, hovered, focused, visible, hidden, interval]);
  const show = (next: number) => { const page = (next + count) % count; setIndex(page); setAnnouncement(`已显示第 ${page + 1} 组，共 ${count} 组`); };
  if (!count) return null;
  return <div ref={root} className={`portal-carousel ${vertical ? 'portal-carousel-vertical' : ''}`} role="region" aria-roledescription="轮播" aria-label={label}
    onPointerEnter={e => { if (e.pointerType === 'mouse') setHovered(true); }} onPointerLeave={() => setHovered(false)} onFocusCapture={() => setFocused(true)} onBlurCapture={e => { if (!e.currentTarget.contains(e.relatedTarget)) setFocused(false); }}>
    <div className="portal-carousel-window"><div className="portal-carousel-track" style={{ transform: vertical ? `translateY(calc(-${active} * var(--portal-recent-height)))` : `translateX(-${active * 100}%)` }}>
      {pages.map((page, i) => <div className="portal-carousel-slide" key={i} inert={i !== active} aria-hidden={i !== active} role="group" aria-label={`第 ${i + 1} 组，共 ${count} 组`}>{page}</div>)}
    </div></div>
    {count > 1 && <div className="portal-carousel-controls" onKeyDown={e => { if (e.key === (vertical ? 'ArrowDown' : 'ArrowRight')) { e.preventDefault(); show(active + 1); } else if (e.key === (vertical ? 'ArrowUp' : 'ArrowLeft')) { e.preventDefault(); show(active - 1); } }}>
      <span>{total} {vertical ? '条发布' : '项精选'} · {active + 1} / {count}</span>
      <button type="button" onClick={() => show(active - 1)} aria-label={`${label}上一组`}>{vertical ? <ArrowUpOutlined /> : <ArrowLeftOutlined />}</button>
      {!vertical && <div className="portal-carousel-dots">{pages.map((_, i) => <button key={i} type="button" aria-label={`${label}第 ${i + 1} 组`} aria-current={i === active} onClick={() => show(i)} />)}</div>}
      <button type="button" onClick={() => show(active + 1)} aria-label={`${label}下一组`}>{vertical ? <ArrowDownOutlined /> : <ArrowRightOutlined />}</button>
      {!reduced && <button type="button" className="portal-carousel-pause" aria-label={`${paused ? '播放' : '暂停'}${label}自动滚动`} onClick={() => setPaused(value => !value)}>{paused ? '播放' : '暂停'}</button>}
    </div>}
    <span className="portal-sr-only" role="status">{announcement}</span>
  </div>;
};
