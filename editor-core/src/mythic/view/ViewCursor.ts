/**
 * Mythic Editor - View Cursor
 *
 * Portions derived from Microsoft Monaco Editor (MIT License)
 * Reference: vs/editor/browser/viewParts/viewCursors/viewCursor.js
 *
 * Renders a blinking caret overlay positioned by line and column coordinates.
 */

import { Position } from '../core/model/Position';
import { Viewport } from './Viewport';

export class ViewCursor {
  readonly element: HTMLElement;
  private readonly viewport: Viewport;
  private position: Position = new Position(1, 1);
  private isVisible: boolean = true;

  constructor(viewport: Viewport) {
    this.viewport = viewport;
    this.element = document.createElement('div');
    this.element.className = 'mythic-cursor';
    this.element.style.height = `${viewport.lineHeight}px`;
    this.updatePosition(this.position);
  }

  getPosition(): Position {
    return this.position;
  }

  updatePosition(position: Position): void {
    this.position = position;
    const top = this.viewport.getLineTop(position.lineNumber);
    const left = this.viewport.getColumnLeft(position.column);

    this.element.style.transform = `translate3d(${left}px, ${top}px, 0)`;

    // Restart blinking on cursor motion
    this.element.classList.remove('blink');
    void this.element.offsetWidth; // trigger reflow
    this.element.classList.add('blink');
  }

  show(): void {
    if (!this.isVisible) {
      this.element.style.display = 'block';
      this.isVisible = true;
    }
  }

  hide(): void {
    if (this.isVisible) {
      this.element.style.display = 'none';
      this.isVisible = false;
    }
  }
}
