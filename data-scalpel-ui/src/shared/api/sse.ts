/** Streaming SSE decoder. Handles UTF-8 across chunks, CRLF and multiline data. */
export class SseDecoder {
  private buffer = '';
  push(text: string): { event: string; data: string }[] {
    this.buffer += text;
    if (this.buffer.length > 1024 * 1024) throw new Error('事件数据超过缓冲上限，需要重新同步');
    const frames: { event: string; data: string }[] = [];
    let match: RegExpExecArray | null;
    while ((match = /\r?\n\r?\n/.exec(this.buffer))) {
      const raw = this.buffer.slice(0, match.index);
      this.buffer = this.buffer.slice(match.index + match[0].length);
      let event = 'message'; const data: string[] = [];
      for (const line of raw.split(/\r?\n/)) {
        if (line.startsWith(':')) continue;
        const colon = line.indexOf(':');
        const field = colon < 0 ? line : line.slice(0, colon);
        const value = colon < 0 ? '' : line.slice(colon + 1).replace(/^ /, '');
        if (field === 'event') event = value;
        if (field === 'data') data.push(value);
      }
      if (data.length) frames.push({ event, data: data.join('\n') });
    }
    return frames;
  }
}
