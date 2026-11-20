/**
 * Crest-Editor - Mythic Engine WebView Entrypoint
 *
 * Replaces Monaco with Mythic Editor:
 * High-performance piece-tree buffer & virtualized view engine.
 */

import { PieceTreeTextBuffer } from '@mythic/core/buffer/PieceTreeTextBuffer';
import { EditorView } from '@mythic/view/EditorView';
import { Position } from '@mythic/core/model/Position';
import { Selection } from '@mythic/core/model/Selection';
import '@mythic/view/editor.css';
import './styles.css';

(function() {
  'use strict';

  // Polyfills for Android WebView compatibility
  if (typeof window.matchMedia !== 'function') {
    window.matchMedia = function(query) {
      return {
        matches: false,
        media: query || '',
        onchange: null,
        addListener: function() {},
        removeListener: function() {},
        addEventListener: function() {},
        removeEventListener: function() {},
        dispatchEvent: function() { return false; }
      };
    };
  }

  if (typeof window.ResizeObserver !== 'function') {
    window.ResizeObserver = class ResizeObserver {
      constructor(callback) {
        this.callback = callback;
      }
      observe(target) {
        try {
          this.callback([{ target, contentRect: target.getBoundingClientRect() }], this);
        } catch(e) {}
      }
      unobserve() {}
      disconnect() {}
    };
  }

  let editorView = null;
  let currentFilePath = null;
  let isEditorReadySent = false;
  const fileBuffers = new Map();
  const container = document.getElementById('editor-container') || document.body;

  function sendEditorReadyOnce() {
    if (isEditorReadySent) return;
    isEditorReadySent = true;
    if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onEditorReady === 'function') {
      try {
        window.CrestAndroidBridge.onEditorReady(JSON.stringify({
          status: 'READY',
          engine: 'Mythic-Editor'
        }));
      } catch (err) {
        console.error('[MythicBridge] onEditorReady error:', err);
      }
    }
  }

  function initMythicEditor() {
    container.innerHTML = '';
    const initialBuffer = new PieceTreeTextBuffer('', '\n');
    editorView = new EditorView(container, initialBuffer, {
      fontSize: 14,
      fontFamily: 'ui-monospace, SFMono-Regular, "Liberation Mono", Menlo, monospace',
      onDidChangeContent: (buf) => {
        if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onContentChanged === 'function') {
          try {
            window.CrestAndroidBridge.onContentChanged(JSON.stringify({
              lineCount: buf.getLineCount(),
              length: buf.getLength(),
              versionId: Date.now()
            }));
          } catch (err) {
            console.error('[MythicBridge] onContentChanged error:', err);
          }
        }
      },
      onSave: (content) => {
        if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onSaveRequested === 'function') {
          try {
            window.CrestAndroidBridge.onSaveRequested(content);
          } catch (err) {
            console.error('[MythicBridge] onSaveRequested error:', err);
          }
        }
      }
    });

    sendEditorReadyOnce();
  }

  // CrestEditorAPI exposed to Android Kotlin Shell
  window.CrestEditorAPI = {
    isReady: function() {
      return editorView !== null;
    },
    getValue: function() {
      return editorView ? editorView.getBuffer().getText() : '';
    },
    setValue: function(val) {
      const buffer = new PieceTreeTextBuffer(val || '', '\n');
      if (currentFilePath) {
        fileBuffers.set(currentFilePath, buffer);
      }
      if (editorView) {
        editorView.setBuffer(buffer);
        editorView.focus();
      }
      return true;
    },
    openFile: function(filePath, content, languageId, isModified) {
      if (!filePath) return false;
      currentFilePath = filePath;

      let buffer = fileBuffers.get(filePath);
      if (content !== null && content !== undefined) {
        // Content provided from disk or external file read
        buffer = new PieceTreeTextBuffer(content, '\n');
        fileBuffers.set(filePath, buffer);
      } else if (!buffer) {
        // Tab opened without initial content
        buffer = new PieceTreeTextBuffer('', '\n');
        fileBuffers.set(filePath, buffer);
      }
      // If buffer exists and content was null, retain the existing buffer without overwriting!

      if (!editorView) {
        initMythicEditor();
      }

      if (editorView) {
        editorView.setBuffer(buffer);
        editorView.focus();
      }
      return true;
    },
    closeFile: function(filePath) {
      fileBuffers.delete(filePath);
      if (currentFilePath === filePath) {
        currentFilePath = null;
        if (editorView) {
          const emptyBuffer = new PieceTreeTextBuffer('', '\n');
          editorView.setBuffer(emptyBuffer);
        }
      }
      return true;
    },
    renameModel: function(oldPath, newPath, languageId) {
      if (fileBuffers.has(oldPath)) {
        const buf = fileBuffers.get(oldPath);
        fileBuffers.delete(oldPath);
        fileBuffers.set(newPath, buf);
      }
      if (currentFilePath === oldPath) {
        currentFilePath = newPath;
      }
      return true;
    },
    syncExternalContent: function(path, content) {
      const buffer = new PieceTreeTextBuffer(content != null ? content : '', '\n');
      fileBuffers.set(path, buffer);
      if (currentFilePath === path && editorView) {
        editorView.setBuffer(buffer);
      }
      return true;
    },
    clearEditor: function() {
      currentFilePath = null;
      if (editorView) {
        editorView.setBuffer(new PieceTreeTextBuffer('', '\n'));
      }
      return true;
    },
    typeText: function(text) {
      if (!editorView) return false;
      const buffer = editorView.getBuffer();
      const sel = editorView.getSelection();
      const offset = buffer.getOffsetAt(sel.getPosition());
      buffer.insert(offset, text);
      editorView.render();
      return true;
    },
    handleKey: function(key) {
      if (!editorView) return false;
      return true;
    },
    revealLine: function(lineNumber, column = 1) {
      if (!editorView) return false;
      const targetPos = new Position(lineNumber, column);
      editorView.setSelection(new Selection(targetPos.lineNumber, targetPos.column, targetPos.lineNumber, targetPos.column));
      return true;
    },
    getLineCount: function() {
      return editorView ? editorView.getBuffer().getLineCount() : 1;
    },
    triggerAction: function(actionId) {
      if (!editorView) return 'FAILED';
      if (actionId === 'editor.action.selectAll') {
        const totalLines = editorView.getBuffer().getLineCount();
        const lastLineLen = editorView.getBuffer().getLineLength(totalLines);
        editorView.setSelection(new Selection(1, 1, totalLines, lastLineLen + 1));
        return 'OK';
      }
      return 'OK';
    },
    applySettings: function(settings) {
      return true;
    },
    undo: function() {
      return false;
    },
    redo: function() {
      return false;
    },
    setTheme: function(themeName) {
      return true;
    }
  };

  // Mock global monaco object for compatibility with legacy queries
  window.monaco = {
    editor: {
      getEditors: function() {
        if (!editorView) return [];
        return [{
          getModel: function() {
            return {
              getLineCount: function() {
                return editorView.getBuffer().getLineCount();
              },
              getValue: function() {
                return editorView.getBuffer().getText();
              }
            };
          },
          getPosition: function() {
            const pos = editorView.getSelection().getPosition();
            return { lineNumber: pos.lineNumber, column: pos.column };
          },
          setPosition: function(pos) {
            editorView.setSelection(new Selection(pos.lineNumber, pos.column, pos.lineNumber, pos.column));
          },
          revealPositionInCenter: function(pos) {
            if (pos) {
              editorView.setSelection(new Selection(pos.lineNumber, pos.column, pos.lineNumber, pos.column));
            }
          },
          focus: function() {
            editorView.focus();
          }
        }];
      }
    }
  };

  // Boot on DOM ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initMythicEditor);
  } else {
    initMythicEditor();
  }

  // Handle window resizing
  window.addEventListener('resize', () => {
    if (editorView) {
      editorView.layout();
      editorView.render();
    }
  });

})();
