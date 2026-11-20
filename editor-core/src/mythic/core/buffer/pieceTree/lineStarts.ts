/**
 * Mythic Editor - Line Starts & EOL Indexing
 *
 * Portions derived from Microsoft Monaco Editor (MIT License)
 * Copyright (c) Microsoft Corporation. All rights reserved.
 *
 * Fast indexing of newline characters (\r, \n, \r\n) within text chunks.
 */

export function createUintArray(arr: number[]): Uint32Array | Uint16Array {
  if (arr[arr.length - 1] < 65536) {
    const r = new Uint16Array(arr.length);
    r.set(arr, 0);
    return r;
  } else {
    const r = new Uint32Array(arr.length);
    r.set(arr, 0);
    return r;
  }
}

export function createLineStartsFast(str: string, readonly: boolean = true): Uint32Array | Uint16Array | number[] {
  const r: number[] = [0];
  let rLength = 1;
  const len = str.length;

  for (let i = 0; i < len; i++) {
    const chr = str.charCodeAt(i);
    if (chr === 13 /* CarriageReturn \r */) {
      if (i + 1 < len && str.charCodeAt(i + 1) === 10 /* LineFeed \n */) {
        // \r\n case
        r[rLength++] = i + 2;
        i++; // skip \n
      } else {
        // \r case
        r[rLength++] = i + 1;
      }
    } else if (chr === 10 /* LineFeed \n */) {
      r[rLength++] = i + 1;
    }
  }

  if (readonly) {
    return createUintArray(r);
  }
  return r;
}
