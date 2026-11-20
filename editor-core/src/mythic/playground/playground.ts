/**
 * Mythic Editor - Playground & Interactive View Host
 */

import { PieceTreeTextBuffer } from '../core/buffer/PieceTreeTextBuffer';
import { Position } from '../core/model/Position';
import { EditorView } from '../view/EditorView';

const SAMPLES: Record<string, string> = {
  basic: `// Welcome to Mythic Editor View Engine!
function calculateFibonacci(n: number): number {
  if (n <= 1) return n;
  return calculateFibonacci(n - 1) + calculateFibonacci(n - 2);
}

console.log(calculateFibonacci(10));`,

  unicode: `// Unicode & Astral Planes Test
const greeting = "नमस्ते दुनिया!"; // Devanagari
const chinese = "你好，世界！"; // CJK
const arabic = "مرحبا بالعالم"; // RTL Arabic
const emojis = "🚀 😈 🔥 💀 ⚡ 😀"; // Astral Emojis
const math = "∀x ∈ ℝ: x² ≥ 0"; // Math symbols`,

  multiline: Array.from({ length: 100 }, (_, i) => `Line ${i + 1}: The quick brown fox jumps over the lazy dog (${i * 10})`).join('\n')
};

class PlaygroundApp {
  private editorView!: EditorView;
  private logBox: HTMLElement;
  private mountEl: HTMLElement;

  constructor() {
    this.mountEl = document.getElementById('editor-mount')!;
    this.logBox = document.getElementById('log-box')!;

    this.initEditor(SAMPLES.basic);
    this.bindEvents();
    this.log('Interactive Editor View mounted successfully.');
  }

  private initEditor(text: string): void {
    this.mountEl.innerHTML = '';
    const buffer = new PieceTreeTextBuffer(text, '\n');
    this.editorView = new EditorView(this.mountEl, buffer);

    // Keep stats updated
    const updateStats = () => {
      const lenEl = document.getElementById('stat-length');
      const linesEl = document.getElementById('stat-lines');
      const cursorEl = document.getElementById('stat-cursor');

      if (lenEl) lenEl.textContent = buffer.getLength().toString();
      if (linesEl) linesEl.textContent = buffer.getLineCount().toString();
      if (cursorEl) {
        const sel = this.editorView.getSelection();
        cursorEl.textContent = sel.isEmpty()
          ? `(${sel.positionLineNumber}, ${sel.positionColumn})`
          : `[${sel.selectionStartLineNumber},${sel.selectionStartColumn} -> ${sel.positionLineNumber},${sel.positionColumn}]`;
      }
    };

    updateStats();
    window.addEventListener('mouseup', updateStats);
    window.addEventListener('keyup', updateStats);
  }

  private bindEvents(): void {
    // Sample select
    const sampleSelect = document.getElementById('sample-select') as HTMLSelectElement;
    sampleSelect?.addEventListener('change', () => {
      const key = sampleSelect.value;
      if (SAMPLES[key]) {
        this.initEditor(SAMPLES[key]);
        this.log(`Loaded sample: ${key}`);
      }
    });

    // Programmatic Insert operation
    document.getElementById('btn-insert')?.addEventListener('click', () => {
      const offsetInput = document.getElementById('insert-offset') as HTMLInputElement;
      const textInput = document.getElementById('insert-text') as HTMLInputElement;
      const offset = parseInt(offsetInput.value, 10);
      const text = textInput.value;

      if (isNaN(offset) || !text) return;

      const t0 = performance.now();
      const buffer = this.editorView.getBuffer();
      buffer.insert(offset, text);
      this.editorView.render();
      const dt = (performance.now() - t0).toFixed(3);

      this.updateMetrics();
      this.log(`Inserted ${text.length} chars at offset ${offset} in ${dt}ms`);
    });

    // Programmatic Delete operation
    document.getElementById('btn-delete')?.addEventListener('click', () => {
      const offsetInput = document.getElementById('delete-offset') as HTMLInputElement;
      const countInput = document.getElementById('delete-count') as HTMLInputElement;
      const offset = parseInt(offsetInput.value, 10);
      const count = parseInt(countInput.value, 10);

      if (isNaN(offset) || isNaN(count)) return;

      const t0 = performance.now();
      const buffer = this.editorView.getBuffer();
      buffer.delete(offset, count);
      this.editorView.render();
      const dt = (performance.now() - t0).toFixed(3);

      this.updateMetrics();
      this.log(`Deleted ${count} chars at offset ${offset} in ${dt}ms`);
    });

    // Offset-to-position calculator
    const calcOffset = document.getElementById('calc-offset') as HTMLInputElement;
    calcOffset?.addEventListener('input', () => {
      const offset = parseInt(calcOffset.value, 10);
      const res = document.getElementById('calc-pos-result');
      if (res && !isNaN(offset)) {
        const pos = this.editorView.getBuffer().getPositionAt(offset);
        res.textContent = `Line ${pos.lineNumber}, Col ${pos.column}`;
      }
    });

    // Position-to-offset calculator
    const calcLine = document.getElementById('calc-line') as HTMLInputElement;
    const calcCol = document.getElementById('calc-col') as HTMLInputElement;
    const updatePosToOffset = () => {
      const line = parseInt(calcLine.value, 10);
      const col = parseInt(calcCol.value, 10);
      const res = document.getElementById('calc-offset-result');
      if (res && !isNaN(line) && !isNaN(col)) {
        const offset = this.editorView.getBuffer().getOffsetAt(new Position(line, col));
        res.textContent = `Offset: ${offset}`;
      }
    };
    calcLine?.addEventListener('input', updatePosToOffset);
    calcCol?.addEventListener('input', updatePosToOffset);

    // Resize handling
    window.addEventListener('resize', () => {
      this.editorView.layout();
      this.editorView.render();
    });
  }

  private updateMetrics(): void {
    const buffer = this.editorView.getBuffer();
    const lenEl = document.getElementById('stat-length');
    const linesEl = document.getElementById('stat-lines');
    if (lenEl) lenEl.textContent = buffer.getLength().toString();
    if (linesEl) linesEl.textContent = buffer.getLineCount().toString();
  }

  private log(message: string): void {
    const entry = document.createElement('div');
    entry.className = 'log-entry';
    const time = new Date().toLocaleTimeString();
    entry.textContent = `[${time}] ${message}`;
    this.logBox.appendChild(entry);
    this.logBox.scrollTop = this.logBox.scrollHeight;
  }
}

window.addEventListener('DOMContentLoaded', () => {
  new PlaygroundApp();
});
