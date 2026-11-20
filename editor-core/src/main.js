/**
 * Crest-Editor - Mythic Engine WebView Entrypoint
 *
 * Replaces Monaco completely with Mythic Editor:
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
  const container = document.getElementById('editor-container') || document.body;

  function initMythicEditor(initialText = '') {
    container.innerHTML = '';
    const buffer = new PieceTreeTextBuffer(initialText, '\n');
    editorView = new EditorView(container, buffer, {
      fontSize: 14,
      fontFamily: 'ui-monospace, SFMono-Regular, "Liberation Mono", Menlo, monospace'
    });

    // Notify Android Bridge that editor is ready
    if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onEditorReady === 'function') {
      try {
        window.CrestAndroidBridge.onEditorReady(JSON.stringify({
          ready: true,
          engine: 'Mythic-Editor'
        }));
      } catch (err) {
        console.error('[MythicBridge] onEditorReady error:', err);
      }
    }
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
      initMythicEditor(val || '');
      return true;
    },
    openFile: function(filePath, content, languageId, isModified) {
      currentFilePath = filePath;
      initMythicEditor(content || '');
      return true;
    },
    closeFile: function(filePath) {
      if (currentFilePath === filePath) {
        currentFilePath = null;
        initMythicEditor('');
      }
      return true;
    },
    renameModel: function(oldPath, newPath, languageId) {
      if (currentFilePath === oldPath) {
        currentFilePath = newPath;
      }
      return true;
    },
    syncExternalContent: function(path, content) {
      if (currentFilePath === path && editorView) {
        initMythicEditor(content || '');
      }
      return true;
    },
    clearEditor: function() {
      currentFilePath = null;
      initMythicEditor('');
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
      // Key handler bridge
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
      console.log('[MythicEditor] triggerAction:', actionId);
      return 'OK';
    },
    applySettings: function(settings) {
      console.log('[MythicEditor] applySettings:', settings);
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
          revealPositionInCenter: function() {},
          focus: function() {}
        }];
      }
    }
  };

  // Boot on DOM ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      initMythicEditor('// Welcome to Crest-Editor powered by Mythic Engine 😈🔥\n\n');
    });
  } else {
    initMythicEditor('// Welcome to Crest-Editor powered by Mythic Engine 😈🔥\n\n');
  }

  // Handle window resizing
  window.addEventListener('resize', () => {
    if (editorView) {
      editorView.layout();
      editorView.render();
    }
  });

})();
