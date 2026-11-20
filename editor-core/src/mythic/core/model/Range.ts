/**
 * Mythic Editor - Core Model
 *
 * Range convention:
 * - A Range is normalized such that (startLineNumber, startColumn) <= (endLineNumber, endColumn).
 * - Empty range: start equals end (represents a cursor point / collapsed selection).
 * - Reversed inputs to the constructor are automatically normalized.
 */

import { IPosition, Position } from './Position';

export interface IRange {
  readonly startLineNumber: number;
  readonly startColumn: number;
  readonly endLineNumber: number;
  readonly endColumn: number;
}

export class Range implements IRange {
  readonly startLineNumber: number;
  readonly startColumn: number;
  readonly endLineNumber: number;
  readonly endColumn: number;

  constructor(
    startLineNumber: number,
    startColumn: number,
    endLineNumber: number,
    endColumn: number
  ) {
    const sln = Math.max(1, Math.floor(startLineNumber));
    const sc = Math.max(1, Math.floor(startColumn));
    const eln = Math.max(1, Math.floor(endLineNumber));
    const ec = Math.max(1, Math.floor(endColumn));

    if (sln > eln || (sln === eln && sc > ec)) {
      // Normalize reversed range
      this.startLineNumber = eln;
      this.startColumn = ec;
      this.endLineNumber = sln;
      this.endColumn = sc;
    } else {
      this.startLineNumber = sln;
      this.startColumn = sc;
      this.endLineNumber = eln;
      this.endColumn = ec;
    }
  }

  /**
   * Test if this range is empty (start equals end).
   */
  isEmpty(): boolean {
    return Range.isEmpty(this);
  }

  static isEmpty(range: IRange): boolean {
    return (
      range.startLineNumber === range.endLineNumber &&
      range.startColumn === range.endColumn
    );
  }

  /**
   * Test if a position is in this range (inclusive).
   */
  containsPosition(position: IPosition): boolean {
    return Range.containsPosition(this, position);
  }

  static containsPosition(range: IRange, position: IPosition): boolean {
    if (position.lineNumber < range.startLineNumber || position.lineNumber > range.endLineNumber) {
      return false;
    }
    if (position.lineNumber === range.startLineNumber && position.column < range.startColumn) {
      return false;
    }
    if (position.lineNumber === range.endLineNumber && position.column > range.endColumn) {
      return false;
    }
    return true;
  }

  /**
   * Test if another range is entirely within this range.
   */
  containsRange(otherRange: IRange): boolean {
    return Range.containsRange(this, otherRange);
  }

  static containsRange(range: IRange, otherRange: IRange): boolean {
    if (otherRange.startLineNumber < range.startLineNumber || otherRange.endLineNumber < range.startLineNumber) {
      return false;
    }
    if (otherRange.startLineNumber > range.endLineNumber || otherRange.endLineNumber > range.endLineNumber) {
      return false;
    }
    if (otherRange.startLineNumber === range.startLineNumber && otherRange.startColumn < range.startColumn) {
      return false;
    }
    if (otherRange.endLineNumber === range.endLineNumber && otherRange.endColumn > range.endColumn) {
      return false;
    }
    return true;
  }

  /**
   * Test if this range starts and ends on the same line.
   */
  isSingleLine(): boolean {
    return this.startLineNumber === this.endLineNumber;
  }

  /**
   * Get the start position.
   */
  getStartPosition(): Position {
    return new Position(this.startLineNumber, this.startColumn);
  }

  /**
   * Get the end position.
   */
  getEndPosition(): Position {
    return new Position(this.endLineNumber, this.endColumn);
  }

  /**
   * Test equality with another range.
   */
  equalsRange(other: IRange | null | undefined): boolean {
    return Range.equalsRange(this, other);
  }

  static equalsRange(a: IRange | null | undefined, b: IRange | null | undefined): boolean {
    if (!a && !b) return true;
    if (!a || !b) return false;
    return (
      a.startLineNumber === b.startLineNumber &&
      a.startColumn === b.startColumn &&
      a.endLineNumber === b.endLineNumber &&
      a.endColumn === b.endColumn
    );
  }

  /**
   * Compare ranges by start position, then by end position.
   */
  static compareRangesUsingStarts(a: IRange | null | undefined, b: IRange | null | undefined): number {
    if (a && b) {
      const aStartLine = a.startLineNumber | 0;
      const bStartLine = b.startLineNumber | 0;
      if (aStartLine === bStartLine) {
        const aStartCol = a.startColumn | 0;
        const bStartCol = b.startColumn | 0;
        if (aStartCol === bStartCol) {
          const aEndLine = a.endLineNumber | 0;
          const bEndLine = b.endLineNumber | 0;
          if (aEndLine === bEndLine) {
            return (a.endColumn | 0) - (b.endColumn | 0);
          }
          return aEndLine - bEndLine;
        }
        return aStartCol - bStartCol;
      }
      return aStartLine - bStartLine;
    }
    return (a ? 1 : 0) - (b ? 1 : 0);
  }

  /**
   * Check if two ranges intersect.
   */
  static areIntersecting(a: IRange, b: IRange): boolean {
    if (a.endLineNumber < b.startLineNumber || (a.endLineNumber === b.startLineNumber && a.endColumn <= b.startColumn)) {
      return false;
    }
    if (b.endLineNumber < a.startLineNumber || (b.endLineNumber === a.startLineNumber && b.endColumn <= a.startColumn)) {
      return false;
    }
    return true;
  }

  /**
   * Intersect two ranges. Returns null if they do not intersect.
   */
  static intersectRanges(a: IRange, b: IRange): Range | null {
    let resultStartLineNumber = a.startLineNumber;
    let resultStartColumn = a.startColumn;
    let resultEndLineNumber = a.endLineNumber;
    let resultEndColumn = a.endColumn;

    const otherStartLineNumber = b.startLineNumber;
    const otherStartColumn = b.startColumn;
    const otherEndLineNumber = b.endLineNumber;
    const otherEndColumn = b.endColumn;

    if (resultStartLineNumber < otherStartLineNumber) {
      resultStartLineNumber = otherStartLineNumber;
      resultStartColumn = otherStartColumn;
    } else if (resultStartLineNumber === otherStartLineNumber) {
      resultStartColumn = Math.max(resultStartColumn, otherStartColumn);
    }

    if (resultEndLineNumber > otherEndLineNumber) {
      resultEndLineNumber = otherEndLineNumber;
      resultEndColumn = otherEndColumn;
    } else if (resultEndLineNumber === otherEndLineNumber) {
      resultEndColumn = Math.min(resultEndColumn, otherEndColumn);
    }

    // Check if result is empty or invalid
    if (resultStartLineNumber > resultEndLineNumber) {
      return null;
    }
    if (resultStartLineNumber === resultEndLineNumber && resultStartColumn > resultEndColumn) {
      return null;
    }

    return new Range(resultStartLineNumber, resultStartColumn, resultEndLineNumber, resultEndColumn);
  }

  clone(): Range {
    return new Range(this.startLineNumber, this.startColumn, this.endLineNumber, this.endColumn);
  }

  toString(): string {
    return `[${this.startLineNumber},${this.startColumn} -> ${this.endLineNumber},${this.endColumn}]`;
  }

  toJSON(): IRange {
    return {
      startLineNumber: this.startLineNumber,
      startColumn: this.startColumn,
      endLineNumber: this.endLineNumber,
      endColumn: this.endColumn
    };
  }

  static fromPositions(start: IPosition, end: IPosition = start): Range {
    return new Range(start.lineNumber, start.column, end.lineNumber, end.column);
  }

  static lift(range: IRange | null | undefined): Range | null {
    if (!range) return null;
    return new Range(range.startLineNumber, range.startColumn, range.endLineNumber, range.endColumn);
  }

  static isIRange(obj: unknown): obj is IRange {
    return (
      typeof obj === 'object' &&
      obj !== null &&
      typeof (obj as IRange).startLineNumber === 'number' &&
      typeof (obj as IRange).startColumn === 'number' &&
      typeof (obj as IRange).endLineNumber === 'number' &&
      typeof (obj as IRange).endColumn === 'number'
    );
  }
}
