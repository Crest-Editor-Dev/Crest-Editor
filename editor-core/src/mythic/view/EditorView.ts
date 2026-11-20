/**
 * Mythic Editor - High-Performance View Engine
 *
 * Coordinates the virtualized viewport, line rendering, cursor, selection overlays,
 * and mobile/desktop input controllers.
 */

import { ITextBuffer } from '../core/buffer/ITextBuffer';
import { Position } from '../core/model/Position';
import { Selection } from '../core/model/Selection';
import { OffsetTransformer } from '../core/model/OffsetTransformer';
import { FontMetrics, IFontMetrics } from './FontMetrics';
import { Viewport } from './Viewport';
import { RenderedLinesCollection } from './RenderedLinesCollection';
import { ViewGutter } from './ViewGutter';
import { ViewCursor } from './ViewCursor';
import { ViewSelection } from './ViewSelection';
import { InputController } from './InputController';
import './editor.css';

export interface IEditorOptions {
  fontSize?: number;
  fontFamily?: string;
  lineHeight?: number;
}

export class EditorView {
  readonly rootElement: HTMLElement;
  private readonly scrollContainer: HTMLElement;
  private readonly canvas: HTMLElement;
  private readonly linesContent: HTMLElement;

  private buffer: ITextBuffer;
  private viewport: Viewport;
  private metrics: IFontMetrics;

  private linesCollection: RenderedLinesCollection;
  private gutter: ViewGutter;
  private cursor: ViewCursor;
  private selectionOverlay: ViewSelection;
  private inputController: InputController;

  private selection: Selection = new Selection(1, 1, 1, 1);
  private isPointerDown: boolean = false;
  private selectionAnchor: Position = new Position(1, 1);

  constructor(targetElement: HTMLElement, buffer: ITextBuffer, options: IEditorOptions = {}) {
    this.buffer = buffer;

    // 1. Measure Font
    this.metrics = FontMetrics.measure(
      options.fontFamily ?? 'ui-monospace, SFMono-Regular, Menlo, monospace',
      options.fontSize ?? 14,
      options.lineHeight
    );

    // 2. Initialize Viewport
    this.viewport = new Viewport(this.metrics.lineHeight, this.metrics.charWidth);

    // 3. Construct DOM structure
    this.rootElement = document.createElement('div');
    this.rootElement.className = 'mythic-editor';
    this.rootElement.style.fontSize = `${this.metrics.fontSize}px`;

    this.scrollContainer = document.createElement('div');
    this.scrollContainer.className = 'mythic-scroll-container';

    this.canvas = document.createElement('div');
    this.canvas.className = 'mythic-canvas';

    this.linesContent = document.createElement('div');
    this.linesContent.className = 'mythic-lines-content';
    this.linesContent.style.left = `${this.viewport.gutterWidth}px`;

    // 4. Subsystem Components
    this.gutter = new ViewGutter(this.viewport);
    this.linesCollection = new RenderedLinesCollection(this.linesContent, this.viewport);
    this.cursor = new ViewCursor(this.viewport);
    this.selectionOverlay = new ViewSelection(this.viewport);

    // Input Controller
    this.inputController = new InputController(this.viewport, {
      onType: (text) => this.handleType(text),
      onDeleteBackward: () => this.handleDeleteBackward(),
      onDeleteForward: () => this.handleDeleteForward(),
      onEnter: () => this.handleEnter(),
      onTab: () => this.handleType('    '),
      onArrow: (dir, shift) => this.handleArrow(dir, shift)
    });

    // Assemble DOM
    this.linesContent.appendChild(this.selectionOverlay.container);
    this.linesContent.appendChild(this.cursor.element);
    this.linesContent.appendChild(this.inputController.textArea);

    this.canvas.appendChild(this.gutter.container);
    this.canvas.appendChild(this.linesContent);
    this.scrollContainer.appendChild(this.canvas);
    this.rootElement.appendChild(this.scrollContainer);
    targetElement.appendChild(this.rootElement);

    // 5. Setup Listeners
    this.bindEvents();

    // 6. Initial Layout & Render
    this.layout();
    this.render();
  }

  getBuffer(): ITextBuffer {
    return this.buffer;
  }

  getSelection(): Selection {
    return this.selection;
  }

  setSelection(newSelection: Selection): void {
    this.selection = newSelection;
    this.cursor.updatePosition(newSelection.getPosition());
    this.inputController.updatePosition(newSelection.getPosition());
    this.selectionOverlay.update(newSelection, this.buffer);
    this.gutter.update(
      this.viewport.getVisibleLines(this.buffer.getLineCount()).startLineNumber,
      this.viewport.getVisibleLines(this.buffer.getLineCount()).endLineNumber,
      newSelection.positionLineNumber
    );
  }

  layout(): void {
    const rect = this.rootElement.getBoundingClientRect();
    this.viewport.setDimensions(rect.width, rect.height);
    this.canvas.style.height = `${this.viewport.getTotalHeight(this.buffer.getLineCount())}px`;
  }

  render(): void {
    const totalLines = this.buffer.getLineCount();
    this.canvas.style.height = `${this.viewport.getTotalHeight(totalLines)}px`;

    const visible = this.viewport.getVisibleLines(totalLines);
    this.linesCollection.update(visible.startLineNumber, visible.endLineNumber, this.buffer);
    this.gutter.update(visible.startLineNumber, visible.endLineNumber, this.selection.positionLineNumber);
    this.selectionOverlay.update(this.selection, this.buffer);
    this.cursor.updatePosition(this.selection.getPosition());
  }

  private bindEvents(): void {
    // Scroll handling (virtualization trigger)
    this.scrollContainer.addEventListener('scroll', () => {
      this.viewport.scrollTop = this.scrollContainer.scrollTop;
      this.viewport.scrollLeft = this.scrollContainer.scrollLeft;
      this.render();
    });

    // Pointer events for click & selection drag
    const getPosFromEvent = (e: MouseEvent | Touch): Position => {
      const contentRect = this.linesContent.getBoundingClientRect();
      const x = Math.max(0, e.clientX - contentRect.left + this.scrollContainer.scrollLeft);
      const y = Math.max(0, e.clientY - contentRect.top + this.scrollContainer.scrollTop);

      const line = this.viewport.getLineNumberAtY(y, this.buffer.getLineCount());
      const lineLen = this.buffer.getLineLength(line);
      const col = this.viewport.getColumnAtX(x, lineLen);
      return new Position(line, col);
    };

    this.linesContent.addEventListener('mousedown', (e: MouseEvent) => {
      e.preventDefault();
      this.isPointerDown = true;
      this.inputController.focus();

      const pos = getPosFromEvent(e);
      this.selectionAnchor = pos;
      this.setSelection(new Selection(pos.lineNumber, pos.column, pos.lineNumber, pos.column));
    });

    window.addEventListener('mousemove', (e: MouseEvent) => {
      if (!this.isPointerDown) return;
      const currentPos = getPosFromEvent(e);
      this.setSelection(
        new Selection(
          this.selectionAnchor.lineNumber,
          this.selectionAnchor.column,
          currentPos.lineNumber,
          currentPos.column
        )
      );
    });

    window.addEventListener('mouseup', () => {
      this.isPointerDown = false;
    });

    // Touch support (mobile tap & drag)
    this.linesContent.addEventListener('touchstart', (e: TouchEvent) => {
      if (e.touches.length === 1) {
        this.inputController.focus();
        const pos = getPosFromEvent(e.touches[0]);
        this.selectionAnchor = pos;
        this.setSelection(new Selection(pos.lineNumber, pos.column, pos.lineNumber, pos.column));
      }
    }, { passive: true });
  }

  // --- Input Handlers ---
  private handleType(text: string): void {
    if (!this.selection.isEmpty()) {
      // Replace active selection
      const startOffset = this.buffer.getOffsetAt(this.selection.getStartPosition());
      this.buffer.applyEdit(this.selection, text);
      const newPos = this.buffer.getPositionAt(startOffset + text.length);
      this.setSelection(new Selection(newPos.lineNumber, newPos.column, newPos.lineNumber, newPos.column));
    } else {
      // Normal insert
      const offset = this.buffer.getOffsetAt(this.selection.getPosition());
      this.buffer.insert(offset, text);
      const newPos = this.buffer.getPositionAt(offset + text.length);
      this.setSelection(new Selection(newPos.lineNumber, newPos.column, newPos.lineNumber, newPos.column));
    }
    this.linesCollection.refresh(this.buffer);
    this.render();
  }

  private handleDeleteBackward(): void {
    if (!this.selection.isEmpty()) {
      const startOffset = this.buffer.getOffsetAt(this.selection.getStartPosition());
      this.buffer.deleteRange(this.selection);
      const newPos = this.buffer.getPositionAt(startOffset);
      this.setSelection(new Selection(newPos.lineNumber, newPos.column, newPos.lineNumber, newPos.column));
    } else {
      const offset = this.buffer.getOffsetAt(this.selection.getPosition());
      if (offset > 0) {
        const fullText = this.buffer.getText();
        const adjustedOffset = OffsetTransformer.adjustSurrogateOffset(fullText, offset - 1);
        const deleteCount = offset - adjustedOffset;

        this.buffer.delete(adjustedOffset, deleteCount);
        const newPos = this.buffer.getPositionAt(adjustedOffset);
        this.setSelection(new Selection(newPos.lineNumber, newPos.column, newPos.lineNumber, newPos.column));
      }
    }
    this.linesCollection.refresh(this.buffer);
    this.render();
  }

  private handleDeleteForward(): void {
    if (!this.selection.isEmpty()) {
      this.handleDeleteBackward();
    } else {
      const offset = this.buffer.getOffsetAt(this.selection.getPosition());
      if (offset < this.buffer.getLength()) {
        this.buffer.delete(offset, 1);
      }
    }
    this.linesCollection.refresh(this.buffer);
    this.render();
  }

  private handleEnter(): void {
    this.handleType('\n');
  }

  private handleArrow(direction: 'left' | 'right' | 'up' | 'down', expandSelection: boolean): void {
    const cur = this.selection.getPosition();
    let nextLine = cur.lineNumber;
    let nextCol = cur.column;

    switch (direction) {
      case 'left':
        if (nextCol > 1) {
          nextCol--;
        } else if (nextLine > 1) {
          nextLine--;
          nextCol = this.buffer.getLineLength(nextLine) + 1;
        }
        break;
      case 'right':
        if (nextCol <= this.buffer.getLineLength(nextLine)) {
          nextCol++;
        } else if (nextLine < this.buffer.getLineCount()) {
          nextLine++;
          nextCol = 1;
        }
        break;
      case 'up':
        if (nextLine > 1) {
          nextLine--;
          nextCol = Math.min(nextCol, this.buffer.getLineLength(nextLine) + 1);
        }
        break;
      case 'down':
        if (nextLine < this.buffer.getLineCount()) {
          nextLine++;
          nextCol = Math.min(nextCol, this.buffer.getLineLength(nextLine) + 1);
        }
        break;
    }

    const targetPos = new Position(nextLine, nextCol);
    if (expandSelection) {
      this.setSelection(
        new Selection(
          this.selection.selectionStartLineNumber,
          this.selection.selectionStartColumn,
          targetPos.lineNumber,
          targetPos.column
        )
      );
    } else {
      this.setSelection(new Selection(targetPos.lineNumber, targetPos.column, targetPos.lineNumber, targetPos.column));
    }
  }
}
