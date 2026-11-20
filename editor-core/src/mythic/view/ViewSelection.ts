/**
 * Mythic Editor - View Selection Overlay
 *
 * Portions derived from Microsoft Monaco Editor (MIT License)
 * Reference: vs/editor/browser/viewParts/selections/selections.js
 *
 * Renders selection highlight rectangles spanning across lines for the active selection.
 */

import { ITextBuffer } from '../core/buffer/ITextBuffer';
import { Selection } from '../core/model/Selection';
import { Viewport } from './Viewport';

export class ViewSelection {
  readonly container: HTMLElement;
  private readonly viewport: Viewport;
  private selectionElements: HTMLElement[] = [];

  constructor(viewport: Viewport) {
    this.viewport = viewport;
    this.container = document.createElement('div');
    this.container.className = 'mythic-selection-layer';
  }

  update(selection: Selection, buffer: ITextBuffer): void {
    this.clear();

    if (selection.isEmpty()) {
      return;
    }

    const startLine = selection.startLineNumber;
    const endLine = selection.endLineNumber;

    for (let line = startLine; line <= endLine; line++) {
      const lineLen = buffer.getLineLength(line);
      let left = 0;
      let width = 0;

      if (startLine === endLine) {
        // Single line selection
        left = this.viewport.getColumnLeft(selection.startColumn);
        width = Math.max(0, (selection.endColumn - selection.startColumn) * this.viewport.charWidth);
      } else if (line === startLine) {
        // First line of multi-line selection
        left = this.viewport.getColumnLeft(selection.startColumn);
        width = Math.max(0, (lineLen - selection.startColumn + 1) * this.viewport.charWidth + this.viewport.charWidth * 0.5);
      } else if (line === endLine) {
        // Last line of multi-line selection
        left = 0;
        width = Math.max(0, (selection.endColumn - 1) * this.viewport.charWidth);
      } else {
        // Middle line of multi-line selection
        left = 0;
        width = Math.max(0, lineLen * this.viewport.charWidth + this.viewport.charWidth * 0.5);
      }

      if (width > 0) {
        const rectEl = document.createElement('div');
        rectEl.className = 'mythic-selection-rect';
        rectEl.style.height = `${this.viewport.lineHeight}px`;
        rectEl.style.transform = `translate3d(${left}px, ${this.viewport.getLineTop(line)}px, 0)`;
        rectEl.style.width = `${width}px`;

        this.container.appendChild(rectEl);
        this.selectionElements.push(rectEl);
      }
    }
  }

  clear(): void {
    for (const el of this.selectionElements) {
      if (el.parentNode === this.container) {
        this.container.removeChild(el);
      }
    }
    this.selectionElements = [];
  }
}
