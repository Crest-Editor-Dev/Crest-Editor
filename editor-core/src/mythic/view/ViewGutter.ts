/**
 * Mythic Editor - View Gutter (Line Numbers)
 *
 * Portions derived from Microsoft Monaco Editor (MIT License)
 * Reference: vs/editor/browser/viewParts/lineNumbers/lineNumbers.js
 *
 * Virtualized line numbers gutter synchronized with the main line view.
 */

import { Viewport } from './Viewport';

export class ViewGutter {
  readonly container: HTMLElement;
  private readonly viewport: Viewport;
  private renderedNumbers: Map<number, HTMLElement> = new Map();

  constructor(viewport: Viewport) {
    this.viewport = viewport;
    this.container = document.createElement('div');
    this.container.className = 'mythic-gutter';
    this.container.style.width = `${viewport.gutterWidth}px`;
  }

  update(startLineNumber: number, endLineNumber: number, activeLineNumber?: number): void {
    const activeLines = new Set<number>();

    for (let line = startLineNumber; line <= endLineNumber; line++) {
      activeLines.add(line);
      let numEl = this.renderedNumbers.get(line);

      if (!numEl) {
        numEl = document.createElement('div');
        numEl.className = 'mythic-line-number';
        numEl.style.height = `${this.viewport.lineHeight}px`;
        numEl.style.lineHeight = `${this.viewport.lineHeight}px`;
        numEl.style.top = `${this.viewport.getLineTop(line)}px`;
        numEl.textContent = line.toString();

        this.container.appendChild(numEl);
        this.renderedNumbers.set(line, numEl);
      }

      if (line === activeLineNumber) {
        numEl.classList.add('active');
      } else {
        numEl.classList.remove('active');
      }
    }

    for (const [line, el] of this.renderedNumbers.entries()) {
      if (!activeLines.has(line)) {
        if (el.parentNode === this.container) {
          this.container.removeChild(el);
        }
        this.renderedNumbers.delete(line);
      }
    }
  }

  flush(): void {
    for (const el of this.renderedNumbers.values()) {
      if (el.parentNode === this.container) {
        this.container.removeChild(el);
      }
    }
    this.renderedNumbers.clear();
  }
}
