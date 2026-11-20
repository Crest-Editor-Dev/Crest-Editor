/**
 * Mythic Editor - High-Level PieceTree Text Buffer
 *
 * Implements ITextBuffer using a PieceTree data structure.
 * Storage details and tree node complexities remain completely hidden behind this API.
 */

import { IPosition, Position } from '../model/Position';
import { IRange, Range } from '../model/Range';
import { ITextBuffer } from './ITextBuffer';
import { PieceTreeBase, StringBuffer } from './pieceTree/pieceTreeBase';
import { createLineStartsFast } from './pieceTree/lineStarts';

export class PieceTreeTextBuffer implements ITextBuffer {
  private _pieceTree: PieceTreeBase;

  constructor(value: string = '', defaultEOL: '\n' | '\r\n' = '\n') {
    const chunks: StringBuffer[] = [];
    if (value.length > 0) {
      chunks.push(new StringBuffer(value, createLineStartsFast(value)));
    }
    this._pieceTree = new PieceTreeBase(chunks, defaultEOL, true);
  }

  getText(): string {
    return this._pieceTree.getValueInRange(
      new Range(1, 1, this.getLineCount(), this.getLineLength(this.getLineCount()) + 1)
    );
  }

  getValueInRange(range: IRange): string {
    return this._pieceTree.getValueInRange(range);
  }

  getLineContent(lineNumber: number): string {
    const clampedLine = Math.max(1, Math.min(lineNumber, this.getLineCount()));
    return this._pieceTree.getLineContent(clampedLine);
  }

  getLineCount(): number {
    return this._pieceTree.getLineCount();
  }

  getLength(): number {
    return this._pieceTree.getLength();
  }

  getLineLength(lineNumber: number): number {
    const clampedLine = Math.max(1, Math.min(lineNumber, this.getLineCount()));
    return this._pieceTree.getLineLength(clampedLine);
  }

  getEOL(): string {
    return this._pieceTree.getEOL();
  }

  setEOL(eol: '\n' | '\r\n'): void {
    this._pieceTree.setEOL(eol);
  }

  insert(offset: number, text: string): void {
    if (text.length === 0) return;
    const clampedOffset = Math.max(0, Math.min(offset, this.getLength()));
    this._pieceTree.insert(clampedOffset, text);
  }

  delete(offset: number, count: number): void {
    if (count <= 0) return;
    const clampedOffset = Math.max(0, Math.min(offset, this.getLength()));
    const clampedCount = Math.min(count, this.getLength() - clampedOffset);
    if (clampedCount <= 0) return;
    this._pieceTree.delete(clampedOffset, clampedCount);
  }

  deleteRange(range: IRange): void {
    if (Range.isEmpty(range)) return;
    const startOffset = this.getOffsetAt({ lineNumber: range.startLineNumber, column: range.startColumn });
    const endOffset = this.getOffsetAt({ lineNumber: range.endLineNumber, column: range.endColumn });
    const count = Math.max(0, endOffset - startOffset);
    this.delete(startOffset, count);
  }

  applyEdit(range: IRange, text: string): void {
    const startOffset = this.getOffsetAt({ lineNumber: range.startLineNumber, column: range.startColumn });
    const endOffset = this.getOffsetAt({ lineNumber: range.endLineNumber, column: range.endColumn });
    const deleteCount = Math.max(0, endOffset - startOffset);

    if (deleteCount > 0) {
      this.delete(startOffset, deleteCount);
    }
    if (text.length > 0) {
      this.insert(startOffset, text);
    }
  }

  getPositionAt(offset: number): Position {
    return this._pieceTree.getPositionAt(offset);
  }

  getOffsetAt(position: IPosition): number {
    return this._pieceTree.getOffsetAt(position.lineNumber, position.column);
  }

  getRangeAt(offset: number, length: number): Range {
    const start = this.getPositionAt(offset);
    const end = this.getPositionAt(offset + length);
    return new Range(start.lineNumber, start.column, end.lineNumber, end.column);
  }
}
