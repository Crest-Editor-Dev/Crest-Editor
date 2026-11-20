/**
 * Mythic Editor - Core Model
 *
 * Offset conversion utilities.
 * Defines the contract and helper converters between 0-based UTF-16 offsets
 * and 1-based (lineNumber, column) positions.
 */

import { IPosition, Position } from './Position';
import { IRange, Range } from './Range';

export interface IOffsetRange {
  readonly startOffset: number;
  readonly endOffset: number;
}

export interface IPositionOffsetTransformer {
  getOffsetAt(position: IPosition): number;
  getPositionAt(offset: number): Position;
  getRangeAt(offset: number, length: number): Range;
  getOffsetRange(range: IRange): IOffsetRange;
}

/**
 * Standard utility functions for offset calculations.
 */
export class OffsetTransformer {
  /**
   * Clamp an offset within [0, maxOffset].
   */
  static clampOffset(offset: number, maxOffset: number): number {
    return Math.max(0, Math.min(Math.floor(offset), maxOffset));
  }

  /**
   * Check if a character at index `offset` in `text` is the high surrogate of a surrogate pair.
   * High surrogate range: 0xD800 to 0xDBFF.
   */
  static isHighSurrogate(text: string, offset: number): boolean {
    if (offset < 0 || offset >= text.length) return false;
    const code = text.charCodeAt(offset);
    return code >= 0xD800 && code <= 0xDBFF;
  }

  /**
   * Check if a character at index `offset` in `text` is the low surrogate of a surrogate pair.
   * Low surrogate range: 0xDC00 to 0xDFFF.
   */
  static isLowSurrogate(text: string, offset: number): boolean {
    if (offset < 0 || offset >= text.length) return false;
    const code = text.charCodeAt(offset);
    return code >= 0xDC00 && code <= 0xDFFF;
  }

  /**
   * Adjust an offset so it does not land in the middle of a surrogate pair.
   * If landing on a low surrogate, shifts back to the high surrogate start.
   */
  static adjustSurrogateOffset(text: string, offset: number): number {
    if (offset > 0 && offset < text.length && this.isLowSurrogate(text, offset) && this.isHighSurrogate(text, offset - 1)) {
      return offset - 1;
    }
    return offset;
  }
}
