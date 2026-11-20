/**
 * Mythic Editor - PieceTree Buffer Engine
 *
 * Portions derived from Microsoft Monaco Editor (MIT License)
 * Copyright (c) Microsoft Corporation. All rights reserved.
 *
 * A Red-Black tree piece table implementation for high-performance text buffer manipulation.
 * Avoids full document copies on edit operations by storing text in immutable chunk buffers
 * and managing pieces referencing spans within those buffers.
 */

import { Position } from '../../model/Position';
import { IRange } from '../../model/Range';
import {
  IPiece,
  IRbTree,
  NodeColor,
  SENTINEL,
  TreeNode,
  fixInsert,
  leftest,
  rbDelete,
  righttest,
  updateTreeMetadata
} from './rbTree';
import { createLineStartsFast } from './lineStarts';

const AverageBufferSize = 65535;

export interface IBufferPosition {
  line: number;
  column: number;
}

export class Piece implements IPiece {
  readonly bufferIndex: number;
  readonly start: IBufferPosition;
  readonly end: IBufferPosition;
  readonly lineFeedCnt: number;
  readonly length: number;

  constructor(
    bufferIndex: number,
    start: IBufferPosition,
    end: IBufferPosition,
    lineFeedCnt: number,
    length: number
  ) {
    this.bufferIndex = bufferIndex;
    this.start = start;
    this.end = end;
    this.lineFeedCnt = lineFeedCnt;
    this.length = length;
  }
}

export class StringBuffer {
  buffer: string;
  lineStarts: Uint32Array | Uint16Array | number[];

  constructor(buffer: string, lineStarts: Uint32Array | Uint16Array | number[]) {
    this.buffer = buffer;
    this.lineStarts = lineStarts;
  }
}

export interface INodePosition {
  node: TreeNode;
  remainder: number;
  nodeStartOffset: number;
  nodeStartLineNumber?: number;
}

class PieceTreeSearchCache {
  private readonly _limit: number;
  private _cache: (INodePosition | null)[];

  constructor(limit: number) {
    this._limit = limit;
    this._cache = [];
  }

  get(offset: number): INodePosition | null {
    for (let i = this._cache.length - 1; i >= 0; i--) {
      const nodePos = this._cache[i];
      if (nodePos && nodePos.nodeStartOffset <= offset && nodePos.nodeStartOffset + nodePos.node.piece.length >= offset) {
        return nodePos;
      }
    }
    return null;
  }

  get2(lineNumber: number): INodePosition | null {
    for (let i = this._cache.length - 1; i >= 0; i--) {
      const nodePos = this._cache[i];
      if (
        nodePos &&
        nodePos.nodeStartLineNumber &&
        nodePos.nodeStartLineNumber < lineNumber &&
        nodePos.nodeStartLineNumber + nodePos.node.piece.lineFeedCnt >= lineNumber
      ) {
        return nodePos;
      }
    }
    return null;
  }

  set(nodePosition: INodePosition): void {
    if (this._cache.length >= this._limit) {
      this._cache.shift();
    }
    this._cache.push(nodePosition);
  }

  validate(offset: number): void {
    let hasInvalidVal = false;
    for (let i = 0; i < this._cache.length; i++) {
      const nodePos = this._cache[i];
      if (nodePos && (nodePos.node.parent === SENTINEL || nodePos.nodeStartOffset >= offset)) {
        this._cache[i] = null;
        hasInvalidVal = true;
      }
    }
    if (hasInvalidVal) {
      this._cache = this._cache.filter(entry => entry !== null);
    }
  }
}

export class PieceTreeBase implements IRbTree {
  root: TreeNode = SENTINEL;
  private _buffers: StringBuffer[] = [];
  private _lineCnt: number = 1;
  private _length: number = 0;
  private _EOL: string = '\n';
  private _EOLLength: number = 1;
  private _EOLNormalized: boolean = true;
  private _lastChangeBufferPos: IBufferPosition = { line: 0, column: 0 };
  private _searchCache: PieceTreeSearchCache = new PieceTreeSearchCache(1);
  private _lastVisitedLine = { lineNumber: 0, value: '' };

  constructor(chunks: StringBuffer[], eol: string = '\n', eolNormalized: boolean = true) {
    this.create(chunks, eol, eolNormalized);
  }

  create(chunks: StringBuffer[], eol: string, eolNormalized: boolean): void {
    this._buffers = [new StringBuffer('', [0])];
    this._lastChangeBufferPos = { line: 0, column: 0 };
    this.root = SENTINEL;
    this._lineCnt = 1;
    this._length = 0;
    this._EOL = eol;
    this._EOLLength = eol.length;
    this._EOLNormalized = eolNormalized;

    let lastNode: TreeNode | null = null;
    for (let i = 0, len = chunks.length; i < len; i++) {
      if (chunks[i].buffer.length > 0) {
        if (!chunks[i].lineStarts) {
          chunks[i].lineStarts = createLineStartsFast(chunks[i].buffer);
        }
        const lineStarts = chunks[i].lineStarts;
        const piece = new Piece(
          i + 1,
          { line: 0, column: 0 },
          {
            line: lineStarts.length - 1,
            column: chunks[i].buffer.length - lineStarts[lineStarts.length - 1]
          },
          lineStarts.length - 1,
          chunks[i].buffer.length
        );
        this._buffers.push(chunks[i]);
        lastNode = this.rbInsertRight(lastNode, piece);
      }
    }

    this._searchCache = new PieceTreeSearchCache(1);
    this._lastVisitedLine = { lineNumber: 0, value: '' };
    this.computeBufferMetadata();
  }

  getEOL(): string {
    return this._EOL;
  }

  setEOL(newEOL: string): void {
    this._EOL = newEOL;
    this._EOLLength = this._EOL.length;
    this.normalizeEOL(newEOL);
  }

  normalizeEOL(eol: string): void {
    const averageBufferSize = AverageBufferSize;
    const min = averageBufferSize - Math.floor(averageBufferSize / 3);
    const max = min * 2;

    let tempChunk = '';
    let tempChunkLen = 0;
    const chunks: StringBuffer[] = [];

    this.iterate(this.root, node => {
      const str = this.getNodeContent(node);
      const len = str.length;
      if (tempChunkLen <= min || tempChunkLen + len < max) {
        tempChunk += str;
        tempChunkLen += len;
        return true;
      }
      const text = tempChunk.replace(/\r\n|\r|\n/g, eol);
      chunks.push(new StringBuffer(text, createLineStartsFast(text)));
      tempChunk = str;
      tempChunkLen = len;
      return true;
    });

    if (tempChunkLen > 0) {
      const text = tempChunk.replace(/\r\n|\r|\n/g, eol);
      chunks.push(new StringBuffer(text, createLineStartsFast(text)));
    }

    this.create(chunks, eol, true);
  }

  getLength(): number {
    return this._length;
  }

  getLineCount(): number {
    return this._lineCnt;
  }

  getOffsetAt(lineNumber: number, column: number): number {
    let leftLen = 0;
    let x = this.root;

    while (x !== SENTINEL) {
      if (x.left !== SENTINEL && x.lf_left + 1 >= lineNumber) {
        x = x.left;
      } else if (x.lf_left + x.piece.lineFeedCnt + 1 >= lineNumber) {
        leftLen += x.size_left;
        const accumulatedValInCurrentIndex = this.getAccumulatedValue(x, lineNumber - x.lf_left - 2);
        return (leftLen += accumulatedValInCurrentIndex + column - 1);
      } else {
        lineNumber -= x.lf_left + x.piece.lineFeedCnt;
        leftLen += x.size_left + x.piece.length;
        x = x.right;
      }
    }
    return leftLen;
  }

  getPositionAt(offset: number): Position {
    offset = Math.floor(offset);
    offset = Math.max(0, offset);

    let x = this.root;
    let lfCnt = 0;
    const originalOffset = offset;

    while (x !== SENTINEL) {
      if (x.size_left !== 0 && x.size_left >= offset) {
        x = x.left;
      } else if (x.size_left + x.piece.length >= offset) {
        const out = this.getIndexOf(x, offset - x.size_left);
        lfCnt += x.lf_left + out.index;
        if (out.index === 0) {
          const lineStartOffset = this.getOffsetAt(lfCnt + 1, 1);
          const column = originalOffset - lineStartOffset;
          return new Position(lfCnt + 1, column + 1);
        }
        return new Position(lfCnt + 1, out.remainder + 1);
      } else {
        offset -= x.size_left + x.piece.length;
        lfCnt += x.lf_left + x.piece.lineFeedCnt;
        if (x.right === SENTINEL) {
          const lineStartOffset = this.getOffsetAt(lfCnt + 1, 1);
          const column = originalOffset - offset - lineStartOffset;
          return new Position(lfCnt + 1, column + 1);
        } else {
          x = x.right;
        }
      }
    }
    return new Position(1, 1);
  }

  getValueInRange(range: IRange, eol?: string): string {
    if (range.startLineNumber === range.endLineNumber && range.startColumn === range.endColumn) {
      return '';
    }

    const startPosition = this.nodeAt2(range.startLineNumber, range.startColumn);
    const endPosition = this.nodeAt2(range.endLineNumber, range.endColumn);

    if (!startPosition || !endPosition) {
      return '';
    }

    const value = this.getValueInRange2(startPosition, endPosition);
    if (eol) {
      if (eol !== this._EOL || !this._EOLNormalized) {
        return value.replace(/\r\n|\r|\n/g, eol);
      }
      return value;
    }
    return value;
  }

  private getValueInRange2(startPosition: INodePosition, endPosition: INodePosition): string {
    if (startPosition.node === endPosition.node) {
      const node = startPosition.node;
      const buffer = this._buffers[node.piece.bufferIndex].buffer;
      const startOffset = this.offsetInBuffer(node.piece.bufferIndex, node.piece.start);
      return buffer.substring(startOffset + startPosition.remainder, startOffset + endPosition.remainder);
    }

    let x = startPosition.node;
    const buffer = this._buffers[x.piece.bufferIndex].buffer;
    const startOffset = this.offsetInBuffer(x.piece.bufferIndex, x.piece.start);
    let ret = buffer.substring(startOffset + startPosition.remainder, startOffset + x.piece.length);

    x = x.next();
    while (x !== SENTINEL) {
      const buf = this._buffers[x.piece.bufferIndex].buffer;
      const sOffset = this.offsetInBuffer(x.piece.bufferIndex, x.piece.start);
      if (x === endPosition.node) {
        ret += buf.substring(sOffset, sOffset + endPosition.remainder);
        break;
      } else {
        ret += buf.substring(sOffset, sOffset + x.piece.length);
      }
      x = x.next();
    }
    return ret;
  }

  getLinesContent(): string[] {
    const lines: string[] = [];
    let linesLength = 0;
    let currentLine = '';
    let danglingCR = false;

    this.iterate(this.root, node => {
      if (node === SENTINEL) {
        return true;
      }
      const piece = node.piece;
      let pieceLength = piece.length;
      if (pieceLength === 0) {
        return true;
      }

      const buffer = this._buffers[piece.bufferIndex].buffer;
      const lineStarts = this._buffers[piece.bufferIndex].lineStarts;
      const pieceStartLine = piece.start.line;
      const pieceEndLine = piece.end.line;
      let pieceStartOffset = lineStarts[pieceStartLine] + piece.start.column;

      if (danglingCR) {
        if (buffer.charCodeAt(pieceStartOffset) === 10) {
          pieceStartOffset++;
          pieceLength--;
        }
        lines[linesLength++] = currentLine;
        currentLine = '';
        danglingCR = false;
        if (pieceLength === 0) {
          return true;
        }
      }

      if (pieceStartLine === pieceEndLine) {
        if (!this._EOLNormalized && buffer.charCodeAt(pieceStartOffset + pieceLength - 1) === 13) {
          danglingCR = true;
          currentLine += buffer.substring(pieceStartOffset, pieceStartOffset + pieceLength - 1);
        } else {
          currentLine += buffer.substring(pieceStartOffset, pieceStartOffset + pieceLength);
        }
        return true;
      }

      currentLine += (this._EOLNormalized
        ? buffer.substring(pieceStartOffset, Math.max(pieceStartOffset, lineStarts[pieceStartLine + 1] - this._EOLLength))
        : buffer.substring(pieceStartOffset, lineStarts[pieceStartLine + 1]).replace(/(\r\n|\r|\n)$/, ''));
      lines[linesLength++] = currentLine;

      for (let line = pieceStartLine + 1; line < pieceEndLine; line++) {
        currentLine = (this._EOLNormalized
          ? buffer.substring(lineStarts[line], lineStarts[line + 1] - this._EOLLength)
          : buffer.substring(lineStarts[line], lineStarts[line + 1]).replace(/(\r\n|\r|\n)$/, ''));
        lines[linesLength++] = currentLine;
      }

      if (!this._EOLNormalized && buffer.charCodeAt(lineStarts[pieceEndLine] + piece.end.column - 1) === 13) {
        danglingCR = true;
        if (piece.end.column === 0) {
          linesLength--;
        } else {
          currentLine = buffer.substring(lineStarts[pieceEndLine], lineStarts[pieceEndLine] + piece.end.column - 1);
        }
      } else {
        currentLine = buffer.substring(lineStarts[pieceEndLine], lineStarts[pieceEndLine] + piece.end.column);
      }
      return true;
    });

    if (danglingCR) {
      lines[linesLength++] = currentLine;
      currentLine = '';
    }
    lines[linesLength++] = currentLine;
    return lines;
  }

  getLineContent(lineNumber: number): string {
    if (this._lastVisitedLine.lineNumber === lineNumber) {
      return this._lastVisitedLine.value;
    }
    this._lastVisitedLine.lineNumber = lineNumber;

    if (lineNumber === this._lineCnt) {
      this._lastVisitedLine.value = this.getLineRawContent(lineNumber);
    } else if (this._EOLNormalized) {
      this._lastVisitedLine.value = this.getLineRawContent(lineNumber, this._EOLLength);
    } else {
      this._lastVisitedLine.value = this.getLineRawContent(lineNumber).replace(/(\r\n|\r|\n)$/, '');
    }
    return this._lastVisitedLine.value;
  }

  getLineLength(lineNumber: number): number {
    if (lineNumber === this.getLineCount()) {
      const startOffset = this.getOffsetAt(lineNumber, 1);
      return this.getLength() - startOffset;
    }
    return this.getOffsetAt(lineNumber + 1, 1) - this.getOffsetAt(lineNumber, 1) - this._EOLLength;
  }

  insert(offset: number, value: string, eolNormalized: boolean = false): void {
    if (value.length === 0) {
      return;
    }
    this._EOLNormalized = this._EOLNormalized && eolNormalized;
    this._lastVisitedLine.lineNumber = 0;
    this._lastVisitedLine.value = '';

    if (this.root !== SENTINEL) {
      const nodePos = this.nodeAt(offset);
      if (!nodePos) {
        return;
      }
      const { node, remainder, nodeStartOffset } = nodePos;
      const piece = node.piece;
      const bufferIndex = piece.bufferIndex;
      const insertPosInBuffer = this.positionInBuffer(node, remainder);

      if (
        node.piece.bufferIndex === 0 &&
        piece.end.line === this._lastChangeBufferPos.line &&
        piece.end.column === this._lastChangeBufferPos.column &&
        nodeStartOffset + piece.length === offset &&
        value.length < AverageBufferSize
      ) {
        this.appendToNode(node, value);
        this.computeBufferMetadata();
        return;
      }

      if (nodeStartOffset === offset) {
        this.insertContentToNodeLeft(value, node);
        this._searchCache.validate(offset);
      } else if (nodeStartOffset + node.piece.length > offset) {
        // Inserting into the middle of a node
        const nodesToDel: TreeNode[] = [];
        let newRightPiece = new Piece(
          piece.bufferIndex,
          insertPosInBuffer,
          piece.end,
          this.getLineFeedCnt(piece.bufferIndex, insertPosInBuffer, piece.end),
          this.offsetInBuffer(bufferIndex, piece.end) - this.offsetInBuffer(bufferIndex, insertPosInBuffer)
        );

        if (this.shouldCheckCRLF() && this.endWithCR(value)) {
          const headOfRight = this.nodeCharCodeAt(node, remainder);
          if (headOfRight === 10) {
            const newStart = { line: newRightPiece.start.line + 1, column: 0 };
            newRightPiece = new Piece(
              newRightPiece.bufferIndex,
              newStart,
              newRightPiece.end,
              this.getLineFeedCnt(newRightPiece.bufferIndex, newStart, newRightPiece.end),
              newRightPiece.length - 1
            );
            value += '\n';
          }
        }

        if (this.shouldCheckCRLF() && this.startWithLF(value)) {
          const tailOfLeft = this.nodeCharCodeAt(node, remainder - 1);
          if (tailOfLeft === 13) {
            const previousPos = this.positionInBuffer(node, remainder - 1);
            this.deleteNodeTail(node, previousPos);
            value = '\r' + value;
            if (node.piece.length === 0) {
              nodesToDel.push(node);
            }
          } else {
            this.deleteNodeTail(node, insertPosInBuffer);
          }
        } else {
          this.deleteNodeTail(node, insertPosInBuffer);
        }

        const newPieces = this.createNewPieces(value);
        if (newRightPiece.length > 0) {
          this.rbInsertRight(node, newRightPiece);
        }

        let tmpNode: TreeNode = node;
        for (let k = 0; k < newPieces.length; k++) {
          tmpNode = this.rbInsertRight(tmpNode, newPieces[k]);
        }
        this.deleteNodes(nodesToDel);
      } else {
        this.insertContentToNodeRight(value, node);
      }
    } else {
      const pieces = this.createNewPieces(value);
      let node = this.rbInsertLeft(null, pieces[0]);
      for (let k = 1; k < pieces.length; k++) {
        node = this.rbInsertRight(node, pieces[k]);
      }
    }

    this.computeBufferMetadata();
  }

  delete(offset: number, cnt: number): void {
    this._lastVisitedLine.lineNumber = 0;
    this._lastVisitedLine.value = '';
    if (cnt <= 0 || this.root === SENTINEL) {
      return;
    }

    const startPosition = this.nodeAt(offset);
    const endPosition = this.nodeAt(offset + cnt);

    if (!startPosition || !endPosition) {
      return;
    }

    const startNode = startPosition.node;
    const endNode = endPosition.node;

    if (startNode === endNode) {
      const startSplitPosInBuffer = this.positionInBuffer(startNode, startPosition.remainder);
      const endSplitPosInBuffer = this.positionInBuffer(startNode, endPosition.remainder);

      if (startPosition.nodeStartOffset === offset) {
        if (cnt === startNode.piece.length) {
          const next = startNode.next();
          rbDelete(this, startNode);
          this.validateCRLFWithPrevNode(next);
          this.computeBufferMetadata();
          return;
        }
        this.deleteNodeHead(startNode, endSplitPosInBuffer);
        this._searchCache.validate(offset);
        this.validateCRLFWithPrevNode(startNode);
        this.computeBufferMetadata();
        return;
      }

      if (startPosition.nodeStartOffset + startNode.piece.length === offset + cnt) {
        this.deleteNodeTail(startNode, startSplitPosInBuffer);
        this.validateCRLFWithNextNode(startNode);
        this.computeBufferMetadata();
        return;
      }

      this.shrinkNode(startNode, startSplitPosInBuffer, endSplitPosInBuffer);
      this.computeBufferMetadata();
      return;
    }

    const nodesToDel: TreeNode[] = [];
    const startSplitPosInBuffer = this.positionInBuffer(startNode, startPosition.remainder);
    this.deleteNodeTail(startNode, startSplitPosInBuffer);
    this._searchCache.validate(offset);
    if (startNode.piece.length === 0) {
      nodesToDel.push(startNode);
    }

    const endSplitPosInBuffer = this.positionInBuffer(endNode, endPosition.remainder);
    this.deleteNodeHead(endNode, endSplitPosInBuffer);
    if (endNode.piece.length === 0) {
      nodesToDel.push(endNode);
    }

    const secondNode = startNode.next();
    for (let node = secondNode; node !== SENTINEL && node !== endNode; node = node.next()) {
      nodesToDel.push(node);
    }

    const prev = startNode.piece.length === 0 ? startNode.prev() : startNode;
    this.deleteNodes(nodesToDel);
    this.validateCRLFWithNextNode(prev);
    this.computeBufferMetadata();
  }

  private insertContentToNodeLeft(value: string, node: TreeNode): void {
    const nodesToDel: TreeNode[] = [];
    if (this.shouldCheckCRLF() && this.endWithCR(value) && this.startWithLF(node)) {
      const piece = node.piece;
      const newStart = { line: piece.start.line + 1, column: 0 };
      node.piece = new Piece(
        piece.bufferIndex,
        newStart,
        piece.end,
        this.getLineFeedCnt(piece.bufferIndex, newStart, piece.end),
        piece.length - 1
      );
      value += '\n';
      updateTreeMetadata(this, node, -1, -1);
      if (node.piece.length === 0) {
        nodesToDel.push(node);
      }
    }

    const newPieces = this.createNewPieces(value);
    let newNode = this.rbInsertLeft(node, newPieces[newPieces.length - 1]);
    for (let k = newPieces.length - 2; k >= 0; k--) {
      newNode = this.rbInsertLeft(newNode, newPieces[k]);
    }
    this.validateCRLFWithPrevNode(newNode);
    this.deleteNodes(nodesToDel);
  }

  private insertContentToNodeRight(value: string, node: TreeNode): void {
    if (this.adjustCarriageReturnFromNext(value, node)) {
      value += '\n';
    }
    const newPieces = this.createNewPieces(value);
    const newNode = this.rbInsertRight(node, newPieces[0]);
    let tmpNode = newNode;
    for (let k = 1; k < newPieces.length; k++) {
      tmpNode = this.rbInsertRight(tmpNode, newPieces[k]);
    }
    this.validateCRLFWithPrevNode(newNode);
  }

  positionInBuffer(node: TreeNode, remainder: number): IBufferPosition {
    const piece = node.piece;
    const bufferIndex = node.piece.bufferIndex;
    const lineStarts = this._buffers[bufferIndex].lineStarts;
    const startOffset = lineStarts[piece.start.line] + piece.start.column;
    const offset = startOffset + remainder;

    let low = piece.start.line;
    let high = piece.end.line;
    let mid = 0;
    let midStart = 0;

    while (low <= high) {
      mid = low + (((high - low) / 2) | 0);
      midStart = lineStarts[mid];
      if (mid === high) {
        break;
      }
      const midStop = lineStarts[mid + 1];
      if (offset < midStart) {
        high = mid - 1;
      } else if (offset >= midStop) {
        low = mid + 1;
      } else {
        break;
      }
    }

    return {
      line: mid,
      column: offset - midStart
    };
  }

  getLineFeedCnt(bufferIndex: number, start: IBufferPosition, end: IBufferPosition): number {
    if (end.column === 0) {
      return end.line - start.line;
    }
    const lineStarts = this._buffers[bufferIndex].lineStarts;
    if (end.line === lineStarts.length - 1) {
      return end.line - start.line;
    }
    const nextLineStartOffset = lineStarts[end.line + 1];
    const endOffset = lineStarts[end.line] + end.column;
    if (nextLineStartOffset > endOffset + 1) {
      return end.line - start.line;
    }
    const previousCharOffset = endOffset - 1;
    const buffer = this._buffers[bufferIndex].buffer;
    if (buffer.charCodeAt(previousCharOffset) === 13) {
      return end.line - start.line + 1;
    }
    return end.line - start.line;
  }

  offsetInBuffer(bufferIndex: number, cursor: IBufferPosition): number {
    const lineStarts = this._buffers[bufferIndex].lineStarts;
    return lineStarts[cursor.line] + cursor.column;
  }

  deleteNodes(nodes: TreeNode[]): void {
    for (let i = 0; i < nodes.length; i++) {
      rbDelete(this, nodes[i]);
    }
  }

  createNewPieces(text: string): Piece[] {
    if (text.length > AverageBufferSize) {
      const newPieces: Piece[] = [];
      while (text.length > AverageBufferSize) {
        const lastChar = text.charCodeAt(AverageBufferSize - 1);
        let splitText: string;
        if (lastChar === 13 || (lastChar >= 0xd800 && lastChar <= 0xdbff)) {
          // Surrogate pair or \r preservation
          splitText = text.substring(0, AverageBufferSize - 1);
          text = text.substring(AverageBufferSize - 1);
        } else {
          splitText = text.substring(0, AverageBufferSize);
          text = text.substring(AverageBufferSize);
        }
        const lineStarts = createLineStartsFast(splitText);
        newPieces.push(
          new Piece(
            this._buffers.length,
            { line: 0, column: 0 },
            { line: lineStarts.length - 1, column: splitText.length - lineStarts[lineStarts.length - 1] },
            lineStarts.length - 1,
            splitText.length
          )
        );
        this._buffers.push(new StringBuffer(splitText, lineStarts));
      }
      const lineStarts = createLineStartsFast(text);
      newPieces.push(
        new Piece(
          this._buffers.length,
          { line: 0, column: 0 },
          { line: lineStarts.length - 1, column: text.length - lineStarts[lineStarts.length - 1] },
          lineStarts.length - 1,
          text.length
        )
      );
      this._buffers.push(new StringBuffer(text, lineStarts));
      return newPieces;
    }

    let startOffset = this._buffers[0].buffer.length;
    const lineStarts = createLineStartsFast(text, false) as number[];
    let start = this._lastChangeBufferPos;

    if (
      this._buffers[0].lineStarts[this._buffers[0].lineStarts.length - 1] === startOffset &&
      startOffset !== 0 &&
      this.startWithLF(text) &&
      this.endWithCR(this._buffers[0].buffer)
    ) {
      this._lastChangeBufferPos = {
        line: this._lastChangeBufferPos.line,
        column: this._lastChangeBufferPos.column + 1
      };
      start = this._lastChangeBufferPos;
      for (let i = 0; i < lineStarts.length; i++) {
        lineStarts[i] += startOffset + 1;
      }
      this._buffers[0].lineStarts = (this._buffers[0].lineStarts as number[]).concat(lineStarts.slice(1));
      this._buffers[0].buffer += '_' + text;
      startOffset += 1;
    } else {
      if (startOffset !== 0) {
        for (let i = 0; i < lineStarts.length; i++) {
          lineStarts[i] += startOffset;
        }
      }
      this._buffers[0].lineStarts = (this._buffers[0].lineStarts as number[]).concat(lineStarts.slice(1));
      this._buffers[0].buffer += text;
    }

    const endOffset = this._buffers[0].buffer.length;
    const endIndex = this._buffers[0].lineStarts.length - 1;
    const endColumn = endOffset - this._buffers[0].lineStarts[endIndex];
    const endPos: IBufferPosition = { line: endIndex, column: endColumn };
    const newPiece = new Piece(0, start, endPos, this.getLineFeedCnt(0, start, endPos), endOffset - startOffset);
    this._lastChangeBufferPos = endPos;
    return [newPiece];
  }

  getLineRawContent(lineNumber: number, endOffset: number = 0): string {
    let x = this.root;
    let ret = '';
    const cache = this._searchCache.get2(lineNumber);

    if (cache) {
      x = cache.node;
      const prevAccumulatedValue = this.getAccumulatedValue(x, lineNumber - (cache.nodeStartLineNumber || 0) - 1);
      const buffer = this._buffers[x.piece.bufferIndex].buffer;
      const startOffset = this.offsetInBuffer(x.piece.bufferIndex, x.piece.start);
      if ((cache.nodeStartLineNumber || 0) + x.piece.lineFeedCnt === lineNumber) {
        ret = buffer.substring(startOffset + prevAccumulatedValue, startOffset + x.piece.length);
      } else {
        const accumulatedValue = this.getAccumulatedValue(x, lineNumber - (cache.nodeStartLineNumber || 0));
        return buffer.substring(startOffset + prevAccumulatedValue, startOffset + accumulatedValue - endOffset);
      }
    } else {
      let nodeStartOffset = 0;
      const originalLineNumber = lineNumber;
      while (x !== SENTINEL) {
        if (x.left !== SENTINEL && x.lf_left >= lineNumber - 1) {
          x = x.left;
        } else if (x.lf_left + x.piece.lineFeedCnt > lineNumber - 1) {
          const prevAccumulatedValue = this.getAccumulatedValue(x, lineNumber - x.lf_left - 2);
          const accumulatedValue = this.getAccumulatedValue(x, lineNumber - x.lf_left - 1);
          const buffer = this._buffers[x.piece.bufferIndex].buffer;
          const startOffset = this.offsetInBuffer(x.piece.bufferIndex, x.piece.start);
          nodeStartOffset += x.size_left;
          this._searchCache.set({
            node: x,
            nodeStartOffset,
            nodeStartLineNumber: originalLineNumber - (lineNumber - 1 - x.lf_left),
            remainder: 0
          });
          return buffer.substring(startOffset + prevAccumulatedValue, startOffset + accumulatedValue - endOffset);
        } else if (x.lf_left + x.piece.lineFeedCnt === lineNumber - 1) {
          const prevAccumulatedValue = this.getAccumulatedValue(x, lineNumber - x.lf_left - 2);
          const buffer = this._buffers[x.piece.bufferIndex].buffer;
          const startOffset = this.offsetInBuffer(x.piece.bufferIndex, x.piece.start);
          ret = buffer.substring(startOffset + prevAccumulatedValue, startOffset + x.piece.length);
          break;
        } else {
          lineNumber -= x.lf_left + x.piece.lineFeedCnt;
          nodeStartOffset += x.size_left + x.piece.length;
          x = x.right;
        }
      }
    }

    x = x.next();
    while (x !== SENTINEL) {
      const buffer = this._buffers[x.piece.bufferIndex].buffer;
      if (x.piece.lineFeedCnt > 0) {
        const accumulatedValue = this.getAccumulatedValue(x, 0);
        const startOffset = this.offsetInBuffer(x.piece.bufferIndex, x.piece.start);
        ret += buffer.substring(startOffset, startOffset + accumulatedValue - endOffset);
        return ret;
      } else {
        const startOffset = this.offsetInBuffer(x.piece.bufferIndex, x.piece.start);
        ret += buffer.substring(startOffset, startOffset + x.piece.length);
      }
      x = x.next();
    }
    return ret;
  }

  computeBufferMetadata(): void {
    let x = this.root;
    let lfCnt = 1;
    let len = 0;
    while (x !== SENTINEL) {
      lfCnt += x.lf_left + x.piece.lineFeedCnt;
      len += x.size_left + x.piece.length;
      x = x.right;
    }
    this._lineCnt = lfCnt;
    this._length = len;
    this._searchCache.validate(this._length);
  }

  getIndexOf(node: TreeNode, accumulatedValue: number): { index: number; remainder: number } {
    const piece = node.piece;
    const pos = this.positionInBuffer(node, accumulatedValue);
    const lineCnt = pos.line - piece.start.line;
    if (this.offsetInBuffer(piece.bufferIndex, piece.end) - this.offsetInBuffer(piece.bufferIndex, piece.start) === accumulatedValue) {
      const realLineCnt = this.getLineFeedCnt(node.piece.bufferIndex, piece.start, pos);
      if (realLineCnt !== lineCnt) {
        return { index: realLineCnt, remainder: 0 };
      }
    }
    return { index: lineCnt, remainder: pos.column };
  }

  getAccumulatedValue(node: TreeNode, index: number): number {
    if (index < 0) {
      return 0;
    }
    const piece = node.piece;
    const lineStarts = this._buffers[piece.bufferIndex].lineStarts;
    const expectedLineStartIndex = piece.start.line + index + 1;
    if (expectedLineStartIndex > piece.end.line) {
      return lineStarts[piece.end.line] + piece.end.column - lineStarts[piece.start.line] - piece.start.column;
    }
    return lineStarts[expectedLineStartIndex] - lineStarts[piece.start.line] - piece.start.column;
  }

  deleteNodeTail(node: TreeNode, pos: IBufferPosition): void {
    const piece = node.piece;
    const originalLFCnt = piece.lineFeedCnt;
    const originalEndOffset = this.offsetInBuffer(piece.bufferIndex, piece.end);
    const newEnd = pos;
    const newEndOffset = this.offsetInBuffer(piece.bufferIndex, newEnd);
    const newLineFeedCnt = this.getLineFeedCnt(piece.bufferIndex, piece.start, newEnd);
    const lf_delta = newLineFeedCnt - originalLFCnt;
    const size_delta = newEndOffset - originalEndOffset;
    const newLength = piece.length + size_delta;
    node.piece = new Piece(piece.bufferIndex, piece.start, newEnd, newLineFeedCnt, newLength);
    updateTreeMetadata(this, node, size_delta, lf_delta);
  }

  deleteNodeHead(node: TreeNode, pos: IBufferPosition): void {
    const piece = node.piece;
    const originalLFCnt = piece.lineFeedCnt;
    const originalStartOffset = this.offsetInBuffer(piece.bufferIndex, piece.start);
    const newStart = pos;
    const newLineFeedCnt = this.getLineFeedCnt(piece.bufferIndex, newStart, piece.end);
    const newStartOffset = this.offsetInBuffer(piece.bufferIndex, newStart);
    const lf_delta = newLineFeedCnt - originalLFCnt;
    const size_delta = originalStartOffset - newStartOffset;
    const newLength = piece.length + size_delta;
    node.piece = new Piece(piece.bufferIndex, newStart, piece.end, newLineFeedCnt, newLength);
    updateTreeMetadata(this, node, size_delta, lf_delta);
  }

  shrinkNode(node: TreeNode, start: IBufferPosition, end: IBufferPosition): void {
    const piece = node.piece;
    const originalStartPos = piece.start;
    const originalEndPos = piece.end;

    const oldLength = piece.length;
    const oldLFCnt = piece.lineFeedCnt;
    const newEnd = start;
    const newLineFeedCnt = this.getLineFeedCnt(piece.bufferIndex, piece.start, newEnd);
    const newLength = this.offsetInBuffer(piece.bufferIndex, start) - this.offsetInBuffer(piece.bufferIndex, originalStartPos);
    node.piece = new Piece(piece.bufferIndex, piece.start, newEnd, newLineFeedCnt, newLength);
    updateTreeMetadata(this, node, newLength - oldLength, newLineFeedCnt - oldLFCnt);

    const newPiece = new Piece(
      piece.bufferIndex,
      end,
      originalEndPos,
      this.getLineFeedCnt(piece.bufferIndex, end, originalEndPos),
      this.offsetInBuffer(piece.bufferIndex, originalEndPos) - this.offsetInBuffer(piece.bufferIndex, end)
    );
    const newNode = this.rbInsertRight(node, newPiece);
    this.validateCRLFWithPrevNode(newNode);
  }

  appendToNode(node: TreeNode, value: string): void {
    if (this.adjustCarriageReturnFromNext(value, node)) {
      value += '\n';
    }
    const hitCRLF = this.shouldCheckCRLF() && this.startWithLF(value) && this.endWithCR(node);
    const startOffset = this._buffers[0].buffer.length;
    this._buffers[0].buffer += value;
    const lineStarts = createLineStartsFast(value, false) as number[];
    for (let i = 0; i < lineStarts.length; i++) {
      lineStarts[i] += startOffset;
    }
    if (hitCRLF) {
      const prevStartOffset = this._buffers[0].lineStarts[this._buffers[0].lineStarts.length - 2];
      (this._buffers[0].lineStarts as number[]).pop();
      this._lastChangeBufferPos = {
        line: this._lastChangeBufferPos.line - 1,
        column: startOffset - prevStartOffset
      };
    }
    this._buffers[0].lineStarts = (this._buffers[0].lineStarts as number[]).concat(lineStarts.slice(1));
    const endIndex = this._buffers[0].lineStarts.length - 1;
    const endColumn = this._buffers[0].buffer.length - this._buffers[0].lineStarts[endIndex];
    const newEnd: IBufferPosition = { line: endIndex, column: endColumn };
    const newLength = node.piece.length + value.length;
    const oldLineFeedCnt = node.piece.lineFeedCnt;
    const newLineFeedCnt = this.getLineFeedCnt(0, node.piece.start, newEnd);
    const lf_delta = newLineFeedCnt - oldLineFeedCnt;
    node.piece = new Piece(node.piece.bufferIndex, node.piece.start, newEnd, newLineFeedCnt, newLength);
    this._lastChangeBufferPos = newEnd;
    updateTreeMetadata(this, node, value.length, lf_delta);
  }

  nodeAt(offset: number): INodePosition | null {
    let x = this.root;
    const cache = this._searchCache.get(offset);
    if (cache) {
      return {
        node: cache.node,
        nodeStartOffset: cache.nodeStartOffset,
        remainder: offset - cache.nodeStartOffset
      };
    }
    let nodeStartOffset = 0;
    while (x !== SENTINEL) {
      if (x.size_left > offset) {
        x = x.left;
      } else if (x.size_left + x.piece.length >= offset) {
        nodeStartOffset += x.size_left;
        const ret: INodePosition = {
          node: x,
          remainder: offset - x.size_left,
          nodeStartOffset
        };
        this._searchCache.set(ret);
        return ret;
      } else {
        offset -= x.size_left + x.piece.length;
        nodeStartOffset += x.size_left + x.piece.length;
        x = x.right;
      }
    }
    return null;
  }

  nodeAt2(lineNumber: number, column: number): INodePosition | null {
    let x = this.root;
    let nodeStartOffset = 0;
    while (x !== SENTINEL) {
      if (x.left !== SENTINEL && x.lf_left >= lineNumber - 1) {
        x = x.left;
      } else if (x.lf_left + x.piece.lineFeedCnt > lineNumber - 1) {
        const prevAccumulatedValue = this.getAccumulatedValue(x, lineNumber - x.lf_left - 2);
        const accumulatedValue = this.getAccumulatedValue(x, lineNumber - x.lf_left - 1);
        nodeStartOffset += x.size_left;
        return {
          node: x,
          remainder: Math.min(prevAccumulatedValue + column - 1, accumulatedValue),
          nodeStartOffset
        };
      } else if (x.lf_left + x.piece.lineFeedCnt === lineNumber - 1) {
        const prevAccumulatedValue = this.getAccumulatedValue(x, lineNumber - x.lf_left - 2);
        if (prevAccumulatedValue + column - 1 <= x.piece.length) {
          return {
            node: x,
            remainder: prevAccumulatedValue + column - 1,
            nodeStartOffset
          };
        } else {
          column -= x.piece.length - prevAccumulatedValue;
          break;
        }
      } else {
        lineNumber -= x.lf_left + x.piece.lineFeedCnt;
        nodeStartOffset += x.size_left + x.piece.length;
        x = x.right;
      }
    }

    x = x.next();
    while (x !== SENTINEL) {
      if (x.piece.lineFeedCnt > 0) {
        const accumulatedValue = this.getAccumulatedValue(x, 0);
        const offset = this.offsetOfNode(x);
        return {
          node: x,
          remainder: Math.min(column - 1, accumulatedValue),
          nodeStartOffset: offset
        };
      } else {
        if (x.piece.length >= column - 1) {
          const offset = this.offsetOfNode(x);
          return {
            node: x,
            remainder: column - 1,
            nodeStartOffset: offset
          };
        } else {
          column -= x.piece.length;
        }
      }
      x = x.next();
    }
    return null;
  }

  nodeCharCodeAt(node: TreeNode, offset: number): number {
    if (node.piece.lineFeedCnt < 1) {
      return -1;
    }
    const buffer = this._buffers[node.piece.bufferIndex];
    const newOffset = this.offsetInBuffer(node.piece.bufferIndex, node.piece.start) + offset;
    return buffer.buffer.charCodeAt(newOffset);
  }

  offsetOfNode(node: TreeNode | null): number {
    if (!node) {
      return 0;
    }
    let pos = node.size_left;
    let curr = node;
    while (curr !== this.root && curr !== SENTINEL) {
      if (curr.parent.right === curr) {
        pos += curr.parent.size_left + curr.parent.piece.length;
      }
      curr = curr.parent;
    }
    return pos;
  }

  shouldCheckCRLF(): boolean {
    return !(this._EOLNormalized && this._EOL === '\n');
  }

  startWithLF(val: string | TreeNode): boolean {
    if (typeof val === 'string') {
      return val.charCodeAt(0) === 10;
    }
    if (val === SENTINEL || val.piece.lineFeedCnt === 0) {
      return false;
    }
    const piece = val.piece;
    const lineStarts = this._buffers[piece.bufferIndex].lineStarts;
    const line = piece.start.line;
    const startOffset = lineStarts[line] + piece.start.column;
    if (line === lineStarts.length - 1) {
      return false;
    }
    const nextLineOffset = lineStarts[line + 1];
    if (nextLineOffset > startOffset + 1) {
      return false;
    }
    return this._buffers[piece.bufferIndex].buffer.charCodeAt(startOffset) === 10;
  }

  endWithCR(val: string | TreeNode): boolean {
    if (typeof val === 'string') {
      return val.charCodeAt(val.length - 1) === 13;
    }
    if (val === SENTINEL || val.piece.lineFeedCnt === 0) {
      return false;
    }
    return this.nodeCharCodeAt(val, val.piece.length - 1) === 13;
  }

  validateCRLFWithPrevNode(nextNode: TreeNode): void {
    if (this.shouldCheckCRLF() && this.startWithLF(nextNode)) {
      const node = nextNode.prev();
      if (this.endWithCR(node)) {
        this.fixCRLF(node, nextNode);
      }
    }
  }

  validateCRLFWithNextNode(node: TreeNode): void {
    if (this.shouldCheckCRLF() && this.endWithCR(node)) {
      const nextNode = node.next();
      if (this.startWithLF(nextNode)) {
        this.fixCRLF(node, nextNode);
      }
    }
  }

  fixCRLF(prev: TreeNode, next: TreeNode): void {
    const nodesToDel: TreeNode[] = [];
    const lineStarts = this._buffers[prev.piece.bufferIndex].lineStarts;
    let newEnd: IBufferPosition;

    if (prev.piece.end.column === 0) {
      newEnd = {
        line: prev.piece.end.line - 1,
        column: lineStarts[prev.piece.end.line] - lineStarts[prev.piece.end.line - 1] - 1
      };
    } else {
      newEnd = { line: prev.piece.end.line, column: prev.piece.end.column - 1 };
    }

    const prevNewLength = prev.piece.length - 1;
    const prevNewLFCnt = prev.piece.lineFeedCnt - 1;
    prev.piece = new Piece(prev.piece.bufferIndex, prev.piece.start, newEnd, prevNewLFCnt, prevNewLength);
    updateTreeMetadata(this, prev, -1, -1);
    if (prev.piece.length === 0) {
      nodesToDel.push(prev);
    }

    const newStart = { line: next.piece.start.line + 1, column: 0 };
    const newLength = next.piece.length - 1;
    const newLineFeedCnt = this.getLineFeedCnt(next.piece.bufferIndex, newStart, next.piece.end);
    next.piece = new Piece(next.piece.bufferIndex, newStart, next.piece.end, newLineFeedCnt, newLength);
    updateTreeMetadata(this, next, -1, -1);
    if (next.piece.length === 0) {
      nodesToDel.push(next);
    }

    const pieces = this.createNewPieces('\r\n');
    this.rbInsertRight(prev, pieces[0]);

    for (let i = 0; i < nodesToDel.length; i++) {
      rbDelete(this, nodesToDel[i]);
    }
  }

  adjustCarriageReturnFromNext(value: string, node: TreeNode): boolean {
    if (this.shouldCheckCRLF() && this.endWithCR(value)) {
      const nextNode = node.next();
      if (this.startWithLF(nextNode)) {
        value += '\n';
        if (nextNode.piece.length === 1) {
          rbDelete(this, nextNode);
        } else {
          const piece = nextNode.piece;
          const newStart = { line: piece.start.line + 1, column: 0 };
          const newLength = piece.length - 1;
          const newLineFeedCnt = this.getLineFeedCnt(piece.bufferIndex, newStart, piece.end);
          nextNode.piece = new Piece(piece.bufferIndex, newStart, piece.end, newLineFeedCnt, newLength);
          updateTreeMetadata(this, nextNode, -1, -1);
        }
        return true;
      }
    }
    return false;
  }

  iterate(node: TreeNode, callback: (node: TreeNode) => boolean): boolean {
    if (node === SENTINEL) {
      return callback(SENTINEL);
    }
    const leftRet = this.iterate(node.left, callback);
    if (!leftRet) {
      return leftRet;
    }
    return callback(node) && this.iterate(node.right, callback);
  }

  getNodeContent(node: TreeNode): string {
    if (node === SENTINEL) {
      return '';
    }
    const buffer = this._buffers[node.piece.bufferIndex];
    const piece = node.piece;
    const startOffset = this.offsetInBuffer(piece.bufferIndex, piece.start);
    const endOffset = this.offsetInBuffer(piece.bufferIndex, piece.end);
    return buffer.buffer.substring(startOffset, endOffset);
  }

  getPieceContent(piece: IPiece): string {
    const buffer = this._buffers[piece.bufferIndex];
    const startOffset = this.offsetInBuffer(piece.bufferIndex, piece.start);
    const endOffset = this.offsetInBuffer(piece.bufferIndex, piece.end);
    return buffer.buffer.substring(startOffset, endOffset);
  }

  rbInsertRight(node: TreeNode | null, p: IPiece): TreeNode {
    const z = new TreeNode(p, NodeColor.Red);
    z.left = SENTINEL;
    z.right = SENTINEL;
    z.parent = SENTINEL;
    z.size_left = 0;
    z.lf_left = 0;

    const x = this.root;
    if (x === SENTINEL) {
      this.root = z;
      z.color = NodeColor.Black;
    } else if (node && node.right === SENTINEL) {
      node.right = z;
      z.parent = node;
    } else if (node) {
      const nextNode = leftest(node.right);
      nextNode.left = z;
      z.parent = nextNode;
    } else {
      const prevNode = righttest(this.root);
      prevNode.right = z;
      z.parent = prevNode;
    }
    fixInsert(this, z);
    return z;
  }

  rbInsertLeft(node: TreeNode | null, p: IPiece): TreeNode {
    const z = new TreeNode(p, NodeColor.Red);
    z.left = SENTINEL;
    z.right = SENTINEL;
    z.parent = SENTINEL;
    z.size_left = 0;
    z.lf_left = 0;

    if (this.root === SENTINEL) {
      this.root = z;
      z.color = NodeColor.Black;
    } else if (node && node.left === SENTINEL) {
      node.left = z;
      z.parent = node;
    } else if (node) {
      const prevNode = righttest(node.left);
      prevNode.right = z;
      z.parent = prevNode;
    } else {
      const prevNode = leftest(this.root);
      prevNode.left = z;
      z.parent = prevNode;
    }
    fixInsert(this, z);
    return z;
  }
}
