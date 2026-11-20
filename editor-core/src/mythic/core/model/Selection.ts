/**
 * Mythic Editor - Core Model
 *
 * Selection convention:
 * - A Selection is a directed range defined by:
 *   - Anchor (selectionStartLineNumber, selectionStartColumn): where selection started.
 *   - Active cursor (positionLineNumber, positionColumn): where the cursor head currently is.
 * - Direction:
 *   - LTR (0): Anchor <= Active Cursor (forward selection).
 *   - RTL (1): Active Cursor < Anchor (backward/reverse selection).
 */

import { IPosition, Position } from './Position';
import { IRange, Range } from './Range';

export const enum SelectionDirection {
  LTR = 0,
  RTL = 1
}

export interface ISelection extends IRange {
  readonly selectionStartLineNumber: number;
  readonly selectionStartColumn: number;
  readonly positionLineNumber: number;
  readonly positionColumn: number;
}

export class Selection extends Range implements ISelection {
  readonly selectionStartLineNumber: number;
  readonly selectionStartColumn: number;
  readonly positionLineNumber: number;
  readonly positionColumn: number;

  constructor(
    selectionStartLineNumber: number,
    selectionStartColumn: number,
    positionLineNumber: number,
    positionColumn: number
  ) {
    super(selectionStartLineNumber, selectionStartColumn, positionLineNumber, positionColumn);
    this.selectionStartLineNumber = Math.max(1, Math.floor(selectionStartLineNumber));
    this.selectionStartColumn = Math.max(1, Math.floor(selectionStartColumn));
    this.positionLineNumber = Math.max(1, Math.floor(positionLineNumber));
    this.positionColumn = Math.max(1, Math.floor(positionColumn));
  }

  /**
   * Selection direction (LTR: forward, RTL: reverse).
   */
  getDirection(): SelectionDirection {
    if (
      this.selectionStartLineNumber < this.positionLineNumber ||
      (this.selectionStartLineNumber === this.positionLineNumber && this.selectionStartColumn <= this.positionColumn)
    ) {
      return SelectionDirection.LTR;
    }
    return SelectionDirection.RTL;
  }

  /**
   * Get the active cursor position (head of selection).
   */
  getPosition(): Position {
    return new Position(this.positionLineNumber, this.positionColumn);
  }

  /**
   * Get the selection anchor position (origin of selection).
   */
  getSelectionStart(): Position {
    return new Position(this.selectionStartLineNumber, this.selectionStartColumn);
  }

  /**
   * Collapse the selection into a cursor at the current position.
   */
  collapseToPosition(): Selection {
    return new Selection(
      this.positionLineNumber,
      this.positionColumn,
      this.positionLineNumber,
      this.positionColumn
    );
  }

  /**
   * Test if this selection equals another selection (including orientation).
   */
  equalsSelection(other: ISelection | null | undefined): boolean {
    return Selection.selectionsEqual(this, other);
  }

  static selectionsEqual(a: ISelection | null | undefined, b: ISelection | null | undefined): boolean {
    if (!a && !b) return true;
    if (!a || !b) return false;
    return (
      a.selectionStartLineNumber === b.selectionStartLineNumber &&
      a.selectionStartColumn === b.selectionStartColumn &&
      a.positionLineNumber === b.positionLineNumber &&
      a.positionColumn === b.positionColumn
    );
  }

  static fromPositions(start: IPosition, end: IPosition = start): Selection {
    return new Selection(start.lineNumber, start.column, end.lineNumber, end.column);
  }

  static fromRange(range: IRange, direction: SelectionDirection = SelectionDirection.LTR): Selection {
    if (direction === SelectionDirection.LTR) {
      return new Selection(range.startLineNumber, range.startColumn, range.endLineNumber, range.endColumn);
    }
    return new Selection(range.endLineNumber, range.endColumn, range.startLineNumber, range.startColumn);
  }

  override toString(): string {
    return `[${this.selectionStartLineNumber},${this.selectionStartColumn} -> ${this.positionLineNumber},${this.positionColumn}]`;
  }

  override toJSON(): ISelection {
    return {
      startLineNumber: this.startLineNumber,
      startColumn: this.startColumn,
      endLineNumber: this.endLineNumber,
      endColumn: this.endColumn,
      selectionStartLineNumber: this.selectionStartLineNumber,
      selectionStartColumn: this.selectionStartColumn,
      positionLineNumber: this.positionLineNumber,
      positionColumn: this.positionColumn
    };
  }

  static isISelection(obj: unknown): obj is ISelection {
    return (
      Range.isIRange(obj) &&
      typeof (obj as ISelection).selectionStartLineNumber === 'number' &&
      typeof (obj as ISelection).selectionStartColumn === 'number' &&
      typeof (obj as ISelection).positionLineNumber === 'number' &&
      typeof (obj as ISelection).positionColumn === 'number'
    );
  }
}
