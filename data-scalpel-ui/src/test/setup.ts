import '@testing-library/jest-dom/vitest';

// jsdom does not implement pseudo-element styles. Ant Design probes them while
// measuring overlays, which otherwise emits an error for every Select, Tooltip
// and Modal interaction and makes the Canvas interaction suite time out.
const browserGetComputedStyle = window.getComputedStyle.bind(window);
Object.defineProperty(window, 'getComputedStyle', {
  configurable: true,
  value: (element: Element) => browserGetComputedStyle(element),
});

Object.defineProperty(window, 'matchMedia', {
  configurable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    addListener: () => undefined,
    removeListener: () => undefined,
    dispatchEvent: () => false,
  }),
});

class TestResizeObserver implements ResizeObserver {
  disconnect = () => undefined;
  observe = () => undefined;
  unobserve = () => undefined;
}

Object.defineProperty(globalThis, 'ResizeObserver', {
  configurable: true,
  value: TestResizeObserver,
});
