/**
 * Mythic Editor - Core Text Buffer Interface
 *
 * Provides an abstract storage contract for text documents.
 * All positions and lines follow 1-based indexing:
 * - Line numbers start at 1.
 * - Columns start at 1.
 * - Offsets are 0-based in UTF-16 code units.
 */

import { IPosition, Position } from '../model/Position';
import { IRange, Range } from '../model/Range';

export interface ITextBuffer {
  /**
   * Get the full text content of the buffer.
   */
  getText(): string;

  /**
   * Get the text content within a specified Range.
   */
  getValueInRange(range: IRange): string;

  /**
   * Get the text of a single line (1-based, without line ending).
   */
  getLineContent(lineNumber: number): string;

  /**
   * Total number of lines in the buffer (minimum 1).
   */
  getLineCount(): number;

  /**
   * Total length of the document in UTF-16 code units.
   */
  getLength(): number;

  /**
   * Get the character length of a specific line (excluding line terminator).
   */
  getLineLength(lineNumber: number): number;

  /**
   * Get the current default End Of Line terminator ('\n' or '\r\n').
   */
  getEOL(): string;

  /**
   * Set and normalize the End Of Line terminator.
   */
  setEOL(eol: '\n' | '\r\n'): void;

  /**
   * Insert text at a 0-based character offset.
   */
  insert(offset: number, text: string): void;

  /**
   * Delete `count` characters starting at 0-based offset.
   */
  delete(offset: number, count: number): void;

  /**
   * Delete text within a specified Range.
   */
  deleteRange(range: IRange): void;

  /**
   * Replace text in a range with new text.
   */
  applyEdit(range: IRange, text: string): void;

  /**
   * Convert a 0-based offset to a 1-based Position.
   */
  getPositionAt(offset: number): Position;

  /**
   * Convert a 1-based Position to a 0-based offset.
   */
  getOffsetAt(position: IPosition): number;

  /**
   * Create a Range corresponding to an offset and length.
   */
  getRangeAt(offset: number, length: number): Range;
}
