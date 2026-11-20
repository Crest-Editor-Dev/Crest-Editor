/**
 * Mythic Editor - Viewport & Virtualization Coordinate Model
 *
 * Manages scroll offsets and calculates visible line intervals based on
 * fixed line height geometry, mirroring Monaco's ViewLayout calculations.
 */

export interface IVisibleLinesRange {
  readonly startLineNumber: number;
  readonly endLineNumber: number;
}

export class Viewport {
  scrollTop: number = 0;
  scrollLeft: number = 0;
  viewportWidth: number = 800;
  viewportHeight: number = 600;
  lineHeight: number = 21;
  charWidth: number = 8.4;
  gutterWidth: number = 55;
  overscanLines: number = 3;

  constructor(lineHeight: number = 21, charWidth: number = 8.4) {
    this.lineHeight = lineHeight;
    this.charWidth = charWidth;
  }

  /**
   * Set viewport pixel dimensions.
   */
  setDimensions(width: number, height: number): void {
    this.viewportWidth = Math.max(0, width);
    this.viewportHeight = Math.max(0, height);
  }

  /**
   * Calculate currently visible line numbers including overscan buffer.
   */
  getVisibleLines(totalLineCount: number): IVisibleLinesRange {
    if (totalLineCount <= 0) {
      return { startLineNumber: 1, endLineNumber: 1 };
    }

    const firstVisible = Math.floor(this.scrollTop / this.lineHeight) + 1;
    const lastVisible = Math.ceil((this.scrollTop + this.viewportHeight) / this.lineHeight);

    const rawStart = Math.max(1, firstVisible - this.overscanLines);
    const rawEnd = Math.min(totalLineCount, lastVisible + this.overscanLines);

    const startLineNumber = Math.min(totalLineCount, rawStart);
    const endLineNumber = Math.max(startLineNumber, Math.min(totalLineCount, rawEnd));

    return {
      startLineNumber,
      endLineNumber
    };
  }

  /**
   * Pixel Y offset for the top of a line (0-based relative to total document).
   */
  getLineTop(lineNumber: number): number {
    return (Math.max(1, lineNumber) - 1) * this.lineHeight;
  }

  /**
   * Total virtual height of document.
   */
  getTotalHeight(totalLineCount: number): number {
    return Math.max(1, totalLineCount) * this.lineHeight;
  }

  /**
   * Pixel X offset for a column (1-based column).
   */
  getColumnLeft(column: number): number {
    return (Math.max(1, column) - 1) * this.charWidth;
  }

  /**
   * Convert document Y coordinate to 1-based lineNumber.
   */
  getLineNumberAtY(y: number, totalLineCount: number): number {
    const rawLine = Math.floor(y / this.lineHeight) + 1;
    return Math.max(1, Math.min(rawLine, totalLineCount));
  }

  /**
   * Convert line X coordinate (excluding gutter) to 1-based column.
   */
  getColumnAtX(x: number, lineLength: number): number {
    const rawCol = Math.round(x / this.charWidth) + 1;
    return Math.max(1, Math.min(rawCol, lineLength + 1));
  }
}
