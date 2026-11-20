/**
 * Mythic Editor - Core Model
 *
 * Position convention:
 * - Line numbers are 1-based (first line is line 1).
 * - Columns are 1-based (first character of a line is column 1).
 * - Offsets are in UTF-16 code units, matching JavaScript String.prototype length/indexing.
 */

export interface IPosition {
  readonly lineNumber: number;
  readonly column: number;
}

export class Position implements IPosition {
  readonly lineNumber: number;
  readonly column: number;

  constructor(lineNumber: number, column: number) {
    this.lineNumber = Math.max(1, Math.floor(lineNumber));
    this.column = Math.max(1, Math.floor(column));
  }

  /**
   * Create a new Position with updated lineNumber and/or column.
   */
  with(newLineNumber: number = this.lineNumber, newColumn: number = this.column): Position {
    if (newLineNumber === this.lineNumber && newColumn === this.column) {
      return this;
    }
    return new Position(newLineNumber, newColumn);
  }

  /**
   * Derive a new position by adding line and column deltas.
   */
  delta(deltaLineNumber: number = 0, deltaColumn: number = 0): Position {
    return this.with(
      Math.max(1, this.lineNumber + deltaLineNumber),
      Math.max(1, this.column + deltaColumn)
    );
  }

  /**
   * Test equality with another position.
   */
  equals(other: IPosition | null | undefined): boolean {
    return Position.equals(this, other);
  }

  static equals(a: IPosition | null | undefined, b: IPosition | null | undefined): boolean {
    if (!a && !b) return true;
    if (!a || !b) return false;
    return a.lineNumber === b.lineNumber && a.column === b.column;
  }

  /**
   * Test if this position comes strictly before another position.
   */
  isBefore(other: IPosition): boolean {
    return Position.isBefore(this, other);
  }

  static isBefore(a: IPosition, b: IPosition): boolean {
    if (a.lineNumber < b.lineNumber) return true;
    if (b.lineNumber < a.lineNumber) return false;
    return a.column < b.column;
  }

  /**
   * Test if this position comes before or is equal to another position.
   */
  isBeforeOrEqual(other: IPosition): boolean {
    return Position.isBeforeOrEqual(this, other);
  }

  static isBeforeOrEqual(a: IPosition, b: IPosition): boolean {
    if (a.lineNumber < b.lineNumber) return true;
    if (b.lineNumber < a.lineNumber) return false;
    return a.column <= b.column;
  }

  /**
   * Comparator for sorting positions (line first, then column).
   */
  static compare(a: IPosition, b: IPosition): number {
    if (a.lineNumber === b.lineNumber) {
      return a.column - b.column;
    }
    return a.lineNumber - b.lineNumber;
  }

  clone(): Position {
    return new Position(this.lineNumber, this.column);
  }

  toString(): string {
    return `(${this.lineNumber},${this.column})`;
  }

  toJSON(): IPosition {
    return {
      lineNumber: this.lineNumber,
      column: this.column
    };
  }

  static lift(pos: IPosition): Position {
    return new Position(pos.lineNumber, pos.column);
  }

  static isIPosition(obj: unknown): obj is IPosition {
    return (
      typeof obj === 'object' &&
      obj !== null &&
      typeof (obj as IPosition).lineNumber === 'number' &&
      typeof (obj as IPosition).column === 'number'
    );
  }
}
