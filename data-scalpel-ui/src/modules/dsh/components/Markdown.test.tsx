import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Markdown } from './Markdown';
describe('assistant Markdown boundary', () => {
  it('renders a table while excluding images, raw HTML and script URLs', () => {
    const { container } = render(<Markdown text={'| 项目 | 状态 |\n| --- | --- |\n| 会话 | 完成 |\n\n![remote](https://example.com/tracker.png)\n\n<script>window.BAD=true</script>\n\n[unsafe](javascript:alert(1))'} />);
    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('a[href^="javascript:"]')).toBeNull();
  });
});
