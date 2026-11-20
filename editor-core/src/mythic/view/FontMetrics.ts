/**
 * Mythic Editor - Font Metrics Reader
 *
 * Portions derived from Microsoft Monaco Editor (MIT License)
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Reference: vs/editor/browser/config/charWidthReader.js
 *
 * Measures sub-pixel character dimensions for monospace fonts by rendering
 * a repeated string of 256 characters in an offscreen element and dividing width by 256.
 */

export interface IFontMetrics {
  readonly charWidth: number;
  readonly lineHeight: number;
  readonly fontSize: number;
  readonly fontFamily: string;
}

export class FontMetrics {
  /**
   * Measure monospace character width and line height accurately.
   */
  static measure(fontFamily: string = 'monospace', fontSize: number = 14, userLineHeight?: number): IFontMetrics {
    if (typeof document === 'undefined') {
      // Non-browser fallback
      const lineHeight = userLineHeight ?? Math.round(fontSize * 1.5);
      return { charWidth: fontSize * 0.6, lineHeight, fontSize, fontFamily };
    }

    const container = document.createElement('div');
    container.style.position = 'absolute';
    container.style.top = '-50000px';
    container.style.left = '-50000px';
    container.style.width = '50000px';
    container.style.visibility = 'hidden';
    container.style.fontFamily = fontFamily;
    container.style.fontSize = `${fontSize}px`;
    container.style.lineHeight = '1';

    const testElement = document.createElement('span');
    testElement.style.whiteSpace = 'pre';

    // Repeat character 256 (2^8) times as in Monaco charWidthReader
    let testString = 'X';
    for (let i = 0; i < 8; i++) {
      testString += testString;
    }
    testElement.textContent = testString;

    container.appendChild(testElement);
    document.body.appendChild(container);

    const measuredWidth = testElement.offsetWidth / 256;
    const measuredHeight = testElement.offsetHeight;

    document.body.removeChild(container);

    const charWidth = measuredWidth > 0 ? measuredWidth : fontSize * 0.6;
    const lineHeight = userLineHeight ?? Math.max(measuredHeight, Math.round(fontSize * 1.5));

    return {
      charWidth,
      lineHeight,
      fontSize,
      fontFamily
    };
  }
}
