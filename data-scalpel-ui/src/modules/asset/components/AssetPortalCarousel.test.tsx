import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AssetPortalCarousel } from './AssetPortalCarousel';

let setIntersection: (visible: boolean) => void;
beforeEach(() => {
  vi.useFakeTimers();
  vi.spyOn(document, 'hidden', 'get').mockReturnValue(false);
  vi.stubGlobal('IntersectionObserver', class {
    constructor(callback: (entries: Array<{ isIntersecting: boolean }>) => void) {
      setIntersection = visible => callback([{ isIntersecting: visible }]);
    }
    observe() { setIntersection(true); }
    disconnect() {}
  });
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
const pages = [<a key="a" href="#a">资源甲</a>, <a key="b" href="#b">资源乙</a>];

describe('资产门户分组播放', () => {
  it('在约定间隔后切换，隐藏组不可聚焦，手动暂停后仍可翻组', () => {
    const { container, unmount } = render(<AssetPortalCarousel pages={pages} label="精选资源" interval={6000} total={6} />);
    act(() => vi.advanceTimersByTime(5999));
    expect(container.querySelector('.portal-carousel-slide:not([inert])')).toHaveTextContent('资源甲');
    act(() => vi.advanceTimersByTime(1));
    expect(container.querySelector('.portal-carousel-slide:not([inert])')).toHaveTextContent('资源乙');
    fireEvent.click(screen.getByRole('button', { name: '暂停精选资源自动滚动' }));
    act(() => vi.advanceTimersByTime(18000));
    expect(container.querySelector('.portal-carousel-slide:not([inert])')).toHaveTextContent('资源乙');
    fireEvent.click(screen.getByRole('button', { name: '精选资源下一组' }));
    expect(container.querySelector('.portal-carousel-slide:not([inert])')).toHaveTextContent('资源甲');
    unmount();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('离屏与键盘阅读时暂停，回到可视范围后重新等待完整间隔', () => {
    const { container } = render(<AssetPortalCarousel pages={pages} label="最近发布" interval={4000} total={8} vertical />);
    act(() => setIntersection(false));
    act(() => vi.advanceTimersByTime(12000));
    expect(container.querySelector('.portal-carousel-slide:not([inert])')).toHaveTextContent('资源甲');
    act(() => setIntersection(true));
    fireEvent.focus(screen.getByRole('button', { name: '最近发布下一组' }));
    act(() => vi.advanceTimersByTime(12000));
    expect(container.querySelector('.portal-carousel-slide:not([inert])')).toHaveTextContent('资源甲');
    fireEvent.blur(screen.getByRole('button', { name: '最近发布下一组' }), { relatedTarget: document.body });
    act(() => vi.advanceTimersByTime(4000));
    expect(container.querySelector('.portal-carousel-slide:not([inert])')).toHaveTextContent('资源乙');
  });

  it('减少动态效果时不自动播放，单组不显示轮播控制', () => {
    const original = window.matchMedia;
    vi.spyOn(window, 'matchMedia').mockImplementation(query => ({ ...original(query), matches: true }));
    const { container, rerender } = render(<AssetPortalCarousel pages={pages} label="精选资源" interval={6000} total={6} />);
    act(() => vi.advanceTimersByTime(30000));
    expect(container.querySelector('.portal-carousel-slide:not([inert])')).toHaveTextContent('资源甲');
    expect(screen.queryByRole('button', { name: '暂停精选资源自动滚动' })).not.toBeInTheDocument();
    rerender(<AssetPortalCarousel pages={[pages[0]]} label="精选资源" interval={6000} total={1} />);
    expect(screen.queryByRole('button', { name: '精选资源下一组' })).not.toBeInTheDocument();
  });
});
