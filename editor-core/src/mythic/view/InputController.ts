/**
 * Mythic Editor - Input Controller (Mobile / Gboard / IME Bridge)
 *
 * Portions derived from Microsoft Monaco Editor (MIT License)
 * Reference: vs/editor/browser/controller/editContext/textArea/textAreaEditContextInput.js
 *
 * Manages an invisible textarea aligned with the cursor that captures keyboard,
 * Android soft keyboard (Gboard), and IME composition events.
 */

import { Position } from '../core/model/Position';
import { Viewport } from './Viewport';

export interface IInputEvents {
  onType: (text: string) => void;
  onDeleteBackward: () => void;
  onDeleteForward: () => void;
  onEnter: () => void;
  onTab: () => void;
  onArrow: (direction: 'left' | 'right' | 'up' | 'down', expandSelection: boolean) => void;
}

export class InputController {
  readonly textArea: HTMLTextAreaElement;
  private readonly viewport: Viewport;
  private isComposing: boolean = false;
  private events: IInputEvents;

  constructor(viewport: Viewport, events: IInputEvents) {
    this.viewport = viewport;
    this.events = events;

    this.textArea = document.createElement('textarea');
    this.textArea.className = 'mythic-inputarea';
    this.textArea.setAttribute('autocomplete', 'off');
    this.textArea.setAttribute('autocorrect', 'off');
    this.textArea.setAttribute('autocapitalize', 'off');
    this.textArea.setAttribute('spellcheck', 'false');
    this.textArea.setAttribute('tabindex', '0');

    this.bindEvents();
  }

  focus(): void {
    this.textArea.focus();
  }

  updatePosition(cursorPosition: Position): void {
    const top = this.viewport.getLineTop(cursorPosition.lineNumber);
    const left = this.viewport.getColumnLeft(cursorPosition.column);
    this.textArea.style.transform = `translate3d(${left}px, ${top}px, 0)`;
  }

  private bindEvents(): void {
    // Keyboard events
    this.textArea.addEventListener('keydown', (e: KeyboardEvent) => {
      if (this.isComposing) return;

      switch (e.key) {
        case 'Enter':
          e.preventDefault();
          this.events.onEnter();
          break;
        case 'Backspace':
          e.preventDefault();
          this.events.onDeleteBackward();
          break;
        case 'Delete':
          e.preventDefault();
          this.events.onDeleteForward();
          break;
        case 'Tab':
          e.preventDefault();
          this.events.onTab();
          break;
        case 'ArrowLeft':
          e.preventDefault();
          this.events.onArrow('left', e.shiftKey);
          break;
        case 'ArrowRight':
          e.preventDefault();
          this.events.onArrow('right', e.shiftKey);
          break;
        case 'ArrowUp':
          e.preventDefault();
          this.events.onArrow('up', e.shiftKey);
          break;
        case 'ArrowDown':
          e.preventDefault();
          this.events.onArrow('down', e.shiftKey);
          break;
      }
    });

    // Composition events (Gboard / Android IME)
    this.textArea.addEventListener('compositionstart', () => {
      this.isComposing = true;
    });

    this.textArea.addEventListener('compositionend', (e: CompositionEvent) => {
      this.isComposing = false;
      if (e.data) {
        this.events.onType(e.data);
      }
      this.textArea.value = '';
    });

    // Modern input & beforeinput events
    this.textArea.addEventListener('beforeinput', (e: InputEvent) => {
      if (this.isComposing) return;

      if (e.inputType === 'insertText' && e.data) {
        e.preventDefault();
        this.events.onType(e.data);
      } else if (e.inputType === 'insertLineBreak' || e.inputType === 'insertParagraph') {
        e.preventDefault();
        this.events.onEnter();
      } else if (e.inputType === 'deleteContentBackward') {
        e.preventDefault();
        this.events.onDeleteBackward();
      } else if (e.inputType === 'deleteContentForward') {
        e.preventDefault();
        this.events.onDeleteForward();
      }
    });

    // Fallback standard input event
    this.textArea.addEventListener('input', () => {
      if (this.isComposing) return;
      if (this.textArea.value.length > 0) {
        const val = this.textArea.value;
        this.textArea.value = '';
        this.events.onType(val);
      }
    });

    // Paste event
    this.textArea.addEventListener('paste', (e: ClipboardEvent) => {
      e.preventDefault();
      const text = e.clipboardData?.getData('text/plain');
      if (text) {
        this.events.onType(text);
      }
    });
  }
}
