/**
 * Mythic Editor - Virtualized Rendered Lines Collection
 *
 * Portions derived from Microsoft Monaco Editor (MIT License)
 * Reference: vs/editor/browser/view/viewLayer.js (RenderedLinesCollection)
 *
 * Efficiently manages line DOM nodes inside the viewport, recycling existing
 * nodes across scroll frames and only instantiating lines entering the viewport.
 */

import { ITextBuffer } from '../core/buffer/ITextBuffer';
import { Viewport } from './Viewport';

export class RenderedLinesCollection {
  private readonly container: HTMLElement;
  private readonly viewport: Viewport;
  private renderedMap: Map<number, HTMLElement> = new Map();

  constructor(container: HTMLElement, viewport: Viewport) {
    this.container = container;
    this.viewport = viewport;
  }

  /**
   * Synchronize rendered DOM lines with the current visible range.
   */
  update(startLineNumber: number, endLineNumber: number, buffer: ITextBuffer): void {
    const activeLines = new Set<number>();

    for (let line = startLineNumber; line <= endLineNumber; line++) {
      activeLines.add(line);
      let lineEl = this.renderedMap.get(line);

      if (!lineEl) {
        lineEl = document.createElement('div');
        lineEl.className = 'mythic-view-line';
        lineEl.style.height = `${this.viewport.lineHeight}px`;
        lineEl.style.lineHeight = `${this.viewport.lineHeight}px`;
        lineEl.style.top = `${this.viewport.getLineTop(line)}px`;
        lineEl.textContent = buffer.getLineContent(line) || '\u00a0'; // non-breaking space for empty line height

        this.container.appendChild(lineEl);
        this.renderedMap.set(line, lineEl);
      }
    }

    // Remove nodes that scrolled outside the visible window
    for (const [line, el] of this.renderedMap.entries()) {
      if (!activeLines.has(line)) {
        if (el.parentNode === this.container) {
          this.container.removeChild(el);
        }
        this.renderedMap.delete(line);
      }
    }
  }

  /**
   * Force refresh all currently rendered line contents.
   */
  refresh(buffer: ITextBuffer): void {
    for (const [line, el] of this.renderedMap.entries()) {
      el.style.top = `${this.viewport.getLineTop(line)}px`;
      el.textContent = buffer.getLineContent(line) || '\u00a0';
    }
  }

  /**
   * Flush all DOM line nodes.
   */
  flush(): void {
    for (const el of this.renderedMap.values()) {
      if (el.parentNode === this.container) {
        this.container.removeChild(el);
      }
    }
    this.renderedMap.clear();
  }
}
