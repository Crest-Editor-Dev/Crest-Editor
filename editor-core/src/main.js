import * as monaco from './core/vs/editor/editor.main.js';
import './languages/definitions/register.all.ts';
import './languages/features/register.all.ts';
import './styles.css';
import { createHighlighter } from 'shiki';
import { shikiToMonaco } from '@shikijs/monaco';

import EditorWorker from './editor.worker?worker&inline';
import HtmlWorker from './languages/features/html/html.worker?worker&inline';
import CssWorker from './languages/features/css/css.worker?worker&inline';
import JsonWorker from './languages/features/json/json.worker?worker&inline';
import TsWorker from './languages/features/typescript/ts.worker?worker&inline';

(function() {
  'use strict';

  // Configure Monaco Environment with inlined Blob Workers for Android WebView
  window.MonacoEnvironment = {
    getWorker: function (moduleId, label) {
      if (label === 'json') {
        return new JsonWorker();
      }
      if (label === 'css' || label === 'scss' || label === 'less') {
        return new CssWorker();
      }
      if (label === 'html' || label === 'handlebars' || label === 'razor') {
        return new HtmlWorker();
      }
      if (label === 'typescript' || label === 'javascript') {
        return new TsWorker();
      }
      return new EditorWorker();
    }
  };

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

  let editor = null;
  let isReady = false;
  let autoSaveTimer = null;
  let isContentDirty = false;
  let shikiHighlighter = null;
  const loadedShikiLanguages = new Set();

  function resolveTheme(themeName) {
    if (!themeName) return 'dark-plus';
    if (themeName === 'vs-dark') return 'dark-plus';
    if (themeName === 'vs' || themeName === 'vs-light') return 'light-plus';
    return themeName;
  }

  function applyShikiTheme(themeName) {
    const resolved = resolveTheme(themeName);
    try {
      monaco.editor.setTheme(resolved);
    } catch (e) {
      console.warn('[Crest Shiki] Could not set theme', resolved, e);
      try { monaco.editor.setTheme('vs-dark'); } catch (_) {}
    }
  }

  async function ensureLanguageLoaded(langId) {
    if (!langId || !shikiHighlighter) return;
    if (loadedShikiLanguages.has(langId)) return;

    try {
      console.log('[Crest Shiki] Dynamically loading language grammar:', langId);
      await shikiHighlighter.loadLanguage(langId);
      loadedShikiLanguages.add(langId);

      const existingLangs = new Set(monaco.languages.getLanguages().map(l => l.id));
      if (!existingLangs.has(langId)) {
        monaco.languages.register({ id: langId });
      }

      const activeTheme = resolveTheme(currentSettings['editor.theme']);
      shikiToMonaco(shikiHighlighter, monaco);

      // Fix HTML bracket colorization: remove < and > as bracket pairs so <!DOCTYPE> and tags never show unexpected bracket errors (matching official VS Code behavior)
      try {
        monaco.languages.setLanguageConfiguration('html', {
          brackets: [
            ['<!--', '-->'],
            ['{', '}'],
            ['(', ')']
          ],
          colorizedBracketPairs: []
        });
      } catch (_) {}
      monaco.editor.setTheme(activeTheme);
      console.log('[Crest Shiki] Successfully registered grammar for:', langId);
    } catch (e) {
      console.warn('[Crest Shiki] Language grammar failed to load or not bundled:', langId, e);
    }
  }

  async function initShiki() {
    try {
      console.log('[Crest Shiki] Initializing TextMate syntax engine...');
      shikiHighlighter = await createHighlighter({
        themes: ['dark-plus', 'light-plus', 'github-dark', 'one-dark-pro', 'dracula'],
        langs: [
          'javascript', 'typescript', 'python', 'kotlin', 'java',
          'c', 'cpp', 'rust', 'go', 'html', 'css', 'json',
          'yaml', 'shellscript', 'markdown', 'sql', 'xml'
        ]
      });

      const existingLangs = new Set(monaco.languages.getLanguages().map(l => l.id));
      for (const lang of shikiHighlighter.getLoadedLanguages()) {
        loadedShikiLanguages.add(lang);
        if (!existingLangs.has(lang)) {
          monaco.languages.register({ id: lang });
          existingLangs.add(lang);
        }
      }

      shikiToMonaco(shikiHighlighter, monaco);

      // Fix HTML bracket colorization: remove < and > as bracket pairs so <!DOCTYPE> and tags never show unexpected bracket errors (matching official VS Code behavior)
      try {
        monaco.languages.setLanguageConfiguration('html', {
          brackets: [
            ['<!--', '-->'],
            ['{', '}'],
            ['(', ')']
          ],
          colorizedBracketPairs: []
        });
      } catch (_) {}

      const desiredTheme = currentSettings['editor.theme'] || 'vs-dark';
      applyShikiTheme(desiredTheme);

      if (footerStatusText) {
        footerStatusText.textContent = 'Crest Mobile • TextMate Active';
      }

      console.log('[Crest Shiki] TextMate engine successfully loaded with', shikiHighlighter.getLoadedLanguages().length, 'grammars!');
    } catch (err) {
      console.error('[Crest Shiki] Failed to initialize TextMate engine, fallback to Monaco Monarch:', err);
    }
  }

  // Settings & VS Code-style AutoSave Engine
  const currentSettings = {
    'files.autoSave': 'afterDelay',
    'files.autoSaveDelay': 1000,
    'editor.fontSize': 14,
    'editor.tabSize': 4,
    'editor.wordWrap': 'on',
    'editor.minimap.enabled': false,
    'editor.cursorBlinking': 'smooth',
    'editor.theme': 'vs-dark',
    'editor.lineNumbers': 'on'
  };

  function triggerAutoSave(reason) {
    if (!isContentDirty || !editor) return;
    const model = editor.getModel();
    if (!model || model.uri.path === '/empty') return;

    if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onSaveRequested === 'function') {
      console.log('[AutoSave] Running auto-save with reason:', reason);
      window.CrestAndroidBridge.onSaveRequested(editor.getValue());
      isContentDirty = false;
    }
  }

  function scheduleAutoSave() {
    const mode = currentSettings['files.autoSave'] || 'afterDelay';
    if (mode !== 'afterDelay') return;

    if (autoSaveTimer) clearTimeout(autoSaveTimer);
    const delay = parseInt(currentSettings['files.autoSaveDelay'], 10) || 1000;
    autoSaveTimer = setTimeout(function() {
      triggerAutoSave('AUTO');
    }, delay);
  }

  // App/Window focus changes (Android onPause / window blur)
  window.addEventListener('blur', function() {
    const mode = currentSettings['files.autoSave'];
    if (mode === 'onWindowChange' || mode === 'onFocusChange') {
      triggerAutoSave('WINDOW_CHANGE');
    }
  });

  document.addEventListener('visibilitychange', function() {
    if (document.visibilityState === 'hidden') {
      const mode = currentSettings['files.autoSave'];
      if (mode === 'onWindowChange' || mode === 'onFocusChange') {
        triggerAutoSave('WINDOW_CHANGE');
      }
    }
  });

  const statusPill = document.getElementById('status-pill');
  const statLines = document.getElementById('stat-lines');
  const statPos = document.getElementById('stat-pos');
  const footerStatusText = document.getElementById('footer-status-text');
  const btnTheme = document.getElementById('btn-theme');

  const sampleCode = '';

  try {
    const container = document.getElementById('editor-container');

    editor = monaco.editor.create(container, {
      value: sampleCode,
      language: 'javascript',
      theme: 'vs-dark',
      automaticLayout: true,
      fontSize: 14,
      lineNumbers: 'on',
      wordWrap: 'on',
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      smoothScrolling: true,
      contextmenu: false,
      quickSuggestions: {
        other: true,
        comments: false,
        strings: true
      },
      suggestOnTriggerCharacters: true,
      acceptSuggestionOnEnter: 'on',
      tabCompletion: 'on',
      wordBasedSuggestions: 'currentDocument',
      fixedOverflowWidgets: true,
      cursorBlinking: 'smooth',
      editContext: false
    });

    isReady = true;

    setupMobileTouchSelection(editor, container);

    // Add Ctrl+S / Cmd+S save command shortcut
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, function() {
      isContentDirty = false;
      if (autoSaveTimer) clearTimeout(autoSaveTimer);
      if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onSaveRequested === 'function') {
        window.CrestAndroidBridge.onSaveRequested(editor.getValue());
      }
    });

    if (statusPill) {
      statusPill.textContent = 'Monaco Active';
      statusPill.style.background = 'rgba(34, 197, 94, 0.2)';
      statusPill.style.color = '#22c55e';
    }

    if (footerStatusText) {
      footerStatusText.textContent = 'Crest Mobile • In-Tree Engine Active';
    }

    updateStats();

    editor.onDidChangeModelContent(function() {
      isContentDirty = true;
      scheduleAutoSave();
      updateStats();
      if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onContentChanged === 'function') {
        try {
          window.CrestAndroidBridge.onContentChanged(JSON.stringify({
            lineCount: editor.getModel().getLineCount(),
            length: editor.getValue().length,
            versionId: editor.getModel().getVersionId()
          }));
        } catch(err) {
          console.error('Bridge onContentChanged error:', err);
        }
      }
    });

    editor.onDidChangeCursorPosition(function(e) {
      if (statPos) {
        statPos.textContent = 'Ln ' + e.position.lineNumber + ', Col ' + e.position.column;
      }
    });

    if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onEditorReady === 'function') {
      try {
        window.CrestAndroidBridge.onEditorReady(JSON.stringify({
          status: 'READY',
          engine: 'Crest In-Tree Monaco',
          lineCount: editor.getModel().getLineCount()
        }));
      } catch(err) {
        console.error('Bridge onEditorReady error:', err);
      }
    }

    console.log('[Crest Engine] Monaco successfully initialized from in-tree source!');

    // Initialize Shiki TextMate syntax engine in background
    initShiki();

  } catch (error) {
    console.error('[Crest Engine] Initialization error:', error);
    if (statusPill) {
      statusPill.textContent = 'Init Error';
      statusPill.style.background = 'rgba(239, 68, 68, 0.2)';
      statusPill.style.color = '#ef4444';
    }
  }

  function updateStats() {
    if (!editor) return;
    const model = editor.getModel();
    if (model && statLines) {
      const count = model.getLineCount();
      statLines.textContent = count + (count === 1 ? ' line' : ' lines');
    }
  }

  let isDarkMode = true;
  if (btnTheme) {
    btnTheme.addEventListener('click', function() {
      if (!editor) return;
      isDarkMode = !isDarkMode;
      applyShikiTheme(isDarkMode ? 'dark-plus' : 'light-plus');
      btnTheme.textContent = isDarkMode ? '🌙' : '☀️';
    });
  }

  // Global Bridge API
  window.CrestEditorAPI = {
    isReady: function() {
      return isReady && editor !== null;
    },
    getValue: function() {
      return editor ? editor.getValue() : '';
    },
    setValue: function(val) {
      if (editor) {
        editor.setValue(val);
        updateStats();
        return true;
      }
      return false;
    },
    typeText: function(text) {
      if (editor) {
        const position = editor.getPosition();
        editor.executeEdits('test-typing', [{
          range: new monaco.Range(position.lineNumber, position.column, position.lineNumber, position.column),
          text: text,
          forceMoveMarkers: true
        }]);
        updateStats();
        return true;
      }
      return false;
    },
    clearEditor: function() {
      if (!editor) return false;
      let emptyModel = monaco.editor.getModel(monaco.Uri.file('/empty'));
      if (!emptyModel) {
        emptyModel = monaco.editor.createModel('', 'plaintext', monaco.Uri.file('/empty'));
      }
      editor.setModel(emptyModel);
      updateStats();
      return true;
    },
    openFile: function(filePath, content, languageId) {
      if (!editor) return false;
      isContentDirty = false;
      if (autoSaveTimer) clearTimeout(autoSaveTimer);

      if (languageId && shikiHighlighter && !loadedShikiLanguages.has(languageId)) {
        ensureLanguageLoaded(languageId).catch(console.error);
      }

      try {
        const uri = monaco.Uri.file(filePath);
        let model = monaco.editor.getModel(uri);
        if (!model) {
          model = monaco.editor.createModel(content, languageId, uri);
        } else {
          if (model.getValue() !== content) {
            model.setValue(content);
          }
          monaco.editor.setModelLanguage(model, languageId);
        }
        editor.setModel(model);
        isContentDirty = false;
        if (autoSaveTimer) clearTimeout(autoSaveTimer);
        updateStats();
        return true;
      } catch (err) {
        console.error('[Crest Monaco] openFile error:', err);
        return false;
      }
    },
    applySettings: function(settings) {
      if (!settings) return;
      Object.assign(currentSettings, settings);

      if (editor) {
        const options = {};
        if ('editor.fontSize' in settings) options.fontSize = parseInt(settings['editor.fontSize'], 10);
        if ('editor.tabSize' in settings) options.tabSize = parseInt(settings['editor.tabSize'], 10);
        if ('editor.wordWrap' in settings) options.wordWrap = settings['editor.wordWrap'];
        if ('editor.minimap.enabled' in settings) options.minimap = { enabled: Boolean(settings['editor.minimap.enabled']) };
        if ('editor.cursorBlinking' in settings) options.cursorBlinking = settings['editor.cursorBlinking'];
        if ('editor.lineNumbers' in settings) options.lineNumbers = settings['editor.lineNumbers'];
        editor.updateOptions(options);

        if ('editor.theme' in settings) {
          applyShikiTheme(settings['editor.theme']);
        }
      }
      console.log('[Crest Monaco] Applied settings successfully:', settings);
    },
    loadLanguage: function(langId) {
      return ensureLanguageLoaded(langId);
    },
    getLoadedLanguages: function() {
      return shikiHighlighter ? shikiHighlighter.getLoadedLanguages() : [];
    },
    getLoadedThemes: function() {
      return shikiHighlighter ? shikiHighlighter.getLoadedThemes() : [];
    },
    getEditorStats: function() {
      if (!editor) return null;
      const model = editor.getModel();
      const pos = editor.getPosition();
      return {
        lineCount: model.getLineCount(),
        characterCount: model.getValueLength(),
        cursorPosition: { lineNumber: pos.lineNumber, column: pos.column },
        language: model.getLanguageId()
      };
    },
    undo: function() {
      if (editor) {
        try { editor.focus(); } catch (e) {}
        const model = editor.getModel();
        if (model && typeof model.undo === 'function') {
          model.undo();
        } else {
          editor.trigger('keyboard', 'undo', null);
        }
      }
    },
    redo: function() {
      if (editor) {
        try { editor.focus(); } catch (e) {}
        const model = editor.getModel();
        if (model && typeof model.redo === 'function') {
          model.redo();
        } else {
          editor.trigger('keyboard', 'redo', null);
        }
      }
    },
    paste: function() {
      if (typeof window.pasteClipboard === 'function') {
        window.pasteClipboard();
      }
    },
    triggerAction: function(actionId) {
      console.log('[Crest Monaco] triggerAction called with:', actionId);
      if (!editor) return 'NO_EDITOR';
      try { editor.focus(); } catch (e) {}

      // Try normalized action IDs (e.g. editor.action.foldAll <-> editor.foldAll)
      let act = editor.getAction ? editor.getAction(actionId) : null;
      if (!act && typeof actionId.startsWith === 'function') {
        if (actionId.startsWith('editor.action.')) {
          act = editor.getAction(actionId.replace('editor.action.', 'editor.'));
        } else if (actionId.startsWith('editor.')) {
          act = editor.getAction(actionId.replace('editor.', 'editor.action.'));
        }
      }

      if (act) {
        if (typeof act._run === 'function') {
          try {
            console.log('[Crest Monaco] executing act._run() for:', act.id);
            act._run();
            return 'RUN_OK';
          } catch (e) {
            console.error('[Crest Monaco] act._run error:', e);
            return 'RUN_ERR:' + e;
          }
        } else if (typeof act.run === 'function') {
          try {
            console.log('[Crest Monaco] executing act.run() for:', act.id);
            act.run();
            return 'RUN_PROMISE';
          } catch (e) {
            console.error('[Crest Monaco] act.run error:', e);
            return 'RUN_ERR:' + e;
          }
        }
      }

      if (editor._commandService && typeof editor._commandService.executeCommand === 'function') {
        try {
          console.log('[Crest Monaco] executing commandService for:', actionId);
          editor._commandService.executeCommand(actionId);
          return 'CMD_OK';
        } catch (e) {}
      }

      try {
        console.log('[Crest Monaco] triggering commandPalette for:', actionId);
        editor.trigger('commandPalette', actionId, null);
        return 'TRIGGER_OK';
      } catch (e) {
        return 'TRIGGER_ERR:' + e;
      }
    },
    handleKey: function(key) {
      if (!editor) return;
      editor.focus();
      switch (key) {
        case 'ESC':
          editor.trigger('keyboard', 'closeFindWidget', null);
          const sel = editor.getSelection();
          if (sel && !sel.isEmpty()) {
            editor.setSelection(new monaco.Selection(sel.positionLineNumber, sel.positionColumn, sel.positionLineNumber, sel.positionColumn));
          }
          break;
        case 'TAB':
          editor.trigger('keyboard', 'tab', null);
          break;
        case 'HOME':
          editor.trigger('keyboard', 'cursorHome', null);
          break;
        case 'END':
          editor.trigger('keyboard', 'cursorEnd', null);
          break;
        case 'PGUP':
          editor.trigger('keyboard', 'cursorPageUp', null);
          break;
        case 'PGDN':
          editor.trigger('keyboard', 'cursorPageDown', null);
          break;
        case 'UP':
          editor.trigger('keyboard', 'cursorUp', null);
          break;
        case 'DOWN':
          editor.trigger('keyboard', 'cursorDown', null);
          break;
        case 'LEFT':
          editor.trigger('keyboard', 'cursorLeft', null);
          break;
        case 'RIGHT':
          editor.trigger('keyboard', 'cursorRight', null);
          break;
      }
    }
  };

  // Mobile Touch Selection, Handles & Dynamic Floating Toolbar
  function setupMobileTouchSelection(editor, container) {
    const startHandle = document.getElementById('start-handle');
    const endHandle = document.getElementById('end-handle');
    const floatingToolbar = document.getElementById('floating-toolbar');
    const floatingCopy = document.getElementById('floating-copy');
    const floatingPaste = document.getElementById('floating-paste');

    if (!startHandle || !endHandle || !floatingToolbar) return;

    const editorNode = editor.getDomNode();
    let pressTimer = null;
    let startX = 0, startY = 0;
    let activeDrag = null;
    let isScrolling = false;
    let scrollResetTimer = null;
    let suppressMouseUntil = 0;
    let isLongPressConfirmed = false;
    let isHandleMoving = false;
    let handleStillTimer = null;
    let pendingUiRaf = null;

    window.pasteClipboard = async function() {
      const model = editor.getModel();
      if (!model) return;
      let text = '';
      if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.getClipboardText === 'function') {
        try {
          text = window.CrestAndroidBridge.getClipboardText();
        } catch (_) {}
      }
      if (!text) {
        try {
          if (navigator.clipboard && navigator.clipboard.readText) {
            text = await navigator.clipboard.readText();
          }
        } catch (_) {}
      }
      if (!text) return;

      const selection = editor.getSelection();
      const range = (selection && !selection.isEmpty())
        ? selection
        : (selection ? new monaco.Range(selection.startLineNumber, selection.startColumn, selection.startLineNumber, selection.startColumn) : model.getFullModelRange());

      editor.executeEdits('ui-paste', [{ range, text, forceMoveMarkers: true }]);
      editor.pushUndoStop();
      editor.focus();
      floatingToolbar.classList.remove('visible');
    };

    function updateCopyToolbar(cachedWrapRect, cachedEditorRect) {
      if (isHandleMoving) {
        floatingToolbar.classList.remove('visible');
        return;
      }
      const selection = editor.getSelection();
      const hasSelection = selection && !selection.isEmpty();
      const wrapRect = cachedWrapRect || container.getBoundingClientRect();
      const editorRect = cachedEditorRect || editorNode.getBoundingClientRect();
      const editorLeft = editorRect.left - wrapRect.left;
      const editorTop = editorRect.top - wrapRect.top;

      if (hasSelection) {
        floatingCopy.style.display = '';
        floatingPaste.style.display = '';
        const start = editor.getScrolledVisiblePosition(selection.getStartPosition());
        const end = editor.getScrolledVisiblePosition(selection.getEndPosition());
        if (!start || !end) {
          floatingToolbar.classList.remove('visible');
          return;
        }
        const toolbarWidth = floatingToolbar.offsetWidth || 140;
        const toolbarHeight = floatingToolbar.offsetHeight || 32;
        const isSingleLine = selection.startLineNumber === selection.endLineNumber;
        const startXPos = editorLeft + start.left;
        const endXPos = editorLeft + end.left;
        const selectionMidX = isSingleLine ? ((startXPos + endXPos) / 2) : (startXPos + 35);
        const left = Math.max(4, Math.min(Math.round(selectionMidX - (toolbarWidth / 2)), wrapRect.width - toolbarWidth - 4));

        const lineHeight = start.height || 19;
        const topOfSelection = editorTop + start.top;
        const gapAboveHandle = 10;
        let top = Math.round(topOfSelection - toolbarHeight - gapAboveHandle);
        if (top < 4) {
          const handleOffset = isSingleLine ? 28 : 6;
          top = Math.round(topOfSelection + lineHeight + handleOffset);
        }
        floatingToolbar.style.transform = `translate3d(${left}px, ${top}px, 0)`;
        floatingToolbar.classList.add('visible');
      } else if (Date.now() < suppressMouseUntil) {
        floatingCopy.style.display = 'none';
        floatingPaste.style.display = '';
        const pos = editor.getPosition();
        if (!pos) return;
        const visible = editor.getScrolledVisiblePosition(pos);
        if (!visible) return;
        const toolbarWidth = floatingToolbar.offsetWidth || 75;
        const toolbarHeight = floatingToolbar.offsetHeight || 32;
        const left = Math.max(4, Math.min(Math.round(editorLeft + visible.left - (toolbarWidth / 2)), wrapRect.width - toolbarWidth - 4));
        const gap = 8;
        let top = Math.round(editorTop + visible.top - toolbarHeight - gap);
        if (top < 2) top = Math.round(editorTop + visible.top + (visible.height || 19) + gap);

        floatingToolbar.style.transform = `translate3d(${left}px, ${top}px, 0)`;
        floatingToolbar.classList.add('visible');
      } else {
        floatingToolbar.classList.remove('visible');
      }
    }

    function positionForHandle(handle, position, isStart, wrapRect, editorRect) {
      const visible = editor.getScrolledVisiblePosition(position);
      if (!visible) {
        handle.classList.remove('visible');
        return;
      }
      const x = Math.round(editorRect.left - wrapRect.left + visible.left - 12);
      const y = Math.round(editorRect.top - wrapRect.top + visible.top + (isStart ? -3 : (visible.height || 18)));
      handle.style.transform = `translate3d(${x}px, ${y}px, 0)`;
      handle.classList.add('visible');
    }

    function updateHandles(wrapRect, editorRect) {
      if (activeDrag) return;
      const selection = editor.getSelection();
      if (!selection || selection.isEmpty()) {
        startHandle.classList.remove('visible');
        endHandle.classList.remove('visible');
        return;
      }
      positionForHandle(startHandle, selection.getStartPosition(), true, wrapRect, editorRect);
      positionForHandle(endHandle, selection.getEndPosition(), false, wrapRect, editorRect);
    }

    function scheduleUiUpdate() {
      if (pendingUiRaf) return;
      pendingUiRaf = requestAnimationFrame(() => {
        pendingUiRaf = null;
        const wrapRect = container.getBoundingClientRect();
        const editorRect = editorNode.getBoundingClientRect();
        updateHandles(wrapRect, editorRect);
        updateCopyToolbar(wrapRect, editorRect);
      });
    }

    floatingPaste.addEventListener('click', () => window.pasteClipboard());
    floatingCopy.addEventListener('click', async () => {
      const sel = editor.getSelection();
      const model = editor.getModel();
      if (!sel || sel.isEmpty() || !model) return;
      const text = model.getValueInRange(sel);
      if (!text) return;
      try {
        await navigator.clipboard.writeText(text);
      } catch (_) {
        const temp = document.createElement('textarea');
        temp.value = text;
        temp.style.position = 'fixed';
        temp.style.left = '-9999px';
        document.body.appendChild(temp);
        temp.select();
        document.execCommand('copy');
        temp.remove();
      }
    });

    ['touchstart', 'mousedown', 'pointerdown'].forEach(evt => {
      floatingToolbar.addEventListener(evt, e => e.stopPropagation());
    });

    let lastScrollTime = 0;
    let isUserTouchDragging = false;
    let lastUserTouchDragTime = 0;
    let touchHasMoved = false;

    function getInputElements() {
      const els = editorNode.querySelectorAll('textarea, input, [contenteditable], .native-edit-context, .inputarea, .ime-text-area');
      els.forEach(el => {
        if (el._imeGuarded) return;
        el._imeGuarded = true;
        const origFocus = el.focus.bind(el);
        el._origFocus = origFocus;
        el.focus = function(opts) {
          if (isScrolling) {
            return;
          }
          return origFocus(opts);
        };
      });
      return els;
    }

    function disableImeForScroll(isInit = false) {
      if (!isInit) {
        lastScrollTime = Date.now();
      }
      getInputElements().forEach(el => {
        if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
          el.readOnly = true;
          el.setAttribute('inputmode', 'none');
        }
        if (document.activeElement === el) {
          el.blur();
        }
      });
      try {
        if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.hideKeyboard === 'function') {
          window.CrestAndroidBridge.hideKeyboard();
        }
      } catch (_) {}
    }

    function enableImeForTyping() {
      if (isScrolling) return;
      getInputElements().forEach(el => {
        if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {
          el.readOnly = false;
          el.removeAttribute('readonly');
          el.setAttribute('inputmode', 'text');
        }
      });
      const mainInput = editorNode.querySelector('textarea.inputarea, .native-edit-context, textarea');
      if (mainInput) {
        if (typeof mainInput._origFocus === 'function') {
          mainInput._origFocus();
        } else {
          mainInput.focus();
        }
      }
      try {
        if (editor && typeof editor.focus === 'function') {
          editor.focus();
        }
      } catch (_) {}
      try {
        if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.showKeyboard === 'function') {
          window.CrestAndroidBridge.showKeyboard();
        }
      } catch (_) {}
    }

    function setupInputAreaFocusGuard() {
      getInputElements().forEach(el => {
        if (el._imeGuarded) return;
        el._imeGuarded = true;
        const origFocus = el.focus.bind(el);
        el.focus = function(opts) {
          if (isScrolling) {
            return;
          }
          return origFocus(opts);
        };
      });
    }

    function isScrollbarTarget(target) {
      if (!target) return false;
      if (target === window || target === document || target === document.body) return true;
      if (typeof target.closest === 'function') {
        return !!(
          target.closest('.scrollbar') ||
          target.closest('.slider') ||
          target.closest('.editor-scrollable')
        );
      }
      return false;
    }

    function isMarginTarget(target) {
      if (!target || typeof target.closest !== 'function') return false;
      return !!(
        target.closest('.margin') ||
        target.closest('.glyph-margin') ||
        target.closest('.line-numbers') ||
        target.closest('.margin-view-overlays')
      );
    }

    editorNode.addEventListener('-monaco-gesturetap', function(event) {
      isScrolling = false;
      clearTimeout(scrollResetTimer);
      lastScrollTime = 0;
      enableImeForTyping();
    });

    document.addEventListener('selectionchange', event => {
      if (Date.now() < suppressMouseUntil || isLongPressConfirmed) event.stopImmediatePropagation();
    }, true);

    container.addEventListener('contextmenu', e => { e.preventDefault(); e.stopPropagation(); }, true);

    function selectWordAt(x, y) {
      const target = editor.getTargetAtClientPoint(x, y);
      if (!target || !target.position) return;
      const model = editor.getModel();
      const word = model.getWordAtPosition(target.position);
      if (!word) {
        editor.setPosition(target.position);
        scheduleUiUpdate();
        return;
      }
      editor.setSelection({
        startLineNumber: target.position.lineNumber,
        startColumn: word.startColumn,
        endLineNumber: target.position.lineNumber,
        endColumn: word.endColumn
      });
      scheduleUiUpdate();
    }

    editorNode.addEventListener('touchstart', e => {
      if (isScrollbarTarget(e.target)) {
        clearTimeout(pressTimer);
        return;
      }
      if (e.touches.length !== 1) { clearTimeout(pressTimer); return; }
      const touch = e.touches[0];
      startX = touch.clientX;
      startY = touch.clientY;
      clearTimeout(pressTimer);
      isLongPressConfirmed = false;
      isUserTouchDragging = false;
      touchHasMoved = false;

      // On touchstart, we record initial coords. We do NOT blur or make readOnly here
      // so Chromium allows soft keyboard on tap. If the touch becomes a scroll (dx/dy > 8),
      // touchmove will trigger disableImeForScroll().

      pressTimer = setTimeout(() => {
        if (!isScrolling) {
          isLongPressConfirmed = true;
          suppressMouseUntil = Date.now() + 5000;
          selectWordAt(startX, startY);
          if (navigator.vibrate) { try { navigator.vibrate(35); } catch(_) {} }
        }
      }, 380);
    }, true);

    editorNode.addEventListener('touchmove', e => {
      if (isScrollbarTarget(e.target)) {
        clearTimeout(pressTimer);
        return;
      }
      if (isLongPressConfirmed) return;
      if (e.touches && e.touches.length > 0) {
        const touch = e.touches[0];
        const dx = Math.abs(touch.clientX - startX);
        const dy = Math.abs(touch.clientY - startY);
        if (dx > 8 || dy > 8 || e.touches.length > 1) {
          touchHasMoved = true;
          clearTimeout(pressTimer);
          isUserTouchDragging = true;
          lastUserTouchDragTime = Date.now();
          isScrolling = true;
          lastScrollTime = Date.now();
          disableImeForScroll();
          clearTimeout(scrollResetTimer);
          scrollResetTimer = setTimeout(() => { isScrolling = false; }, 350);
        }
      } else {
        clearTimeout(pressTimer);
      }
    }, true);

    editorNode.addEventListener('touchend', e => {
      clearTimeout(pressTimer);
      if (isScrollbarTarget(e.target)) {
        return;
      }
      isUserTouchDragging = false;
      if (isLongPressConfirmed) {
        e.preventDefault();
        e.stopPropagation();
        isLongPressConfirmed = false;
        suppressMouseUntil = Date.now() + 300;
        return;
      }
      if (Date.now() < suppressMouseUntil) {
        e.preventDefault();
        e.stopPropagation();
        return;
      }
      if (touchHasMoved || isScrolling) {
        lastScrollTime = Date.now();
        disableImeForScroll();
        clearTimeout(scrollResetTimer);
        scrollResetTimer = setTimeout(() => { isScrolling = false; }, 350);
        return;
      }

      const touch = e.changedTouches && e.changedTouches[0];
      if (touch) {
        const dx = Math.abs(touch.clientX - startX);
        const dy = Math.abs(touch.clientY - startY);
        if (dx < 8 && dy < 8) {
          if (isMarginTarget(e.target)) {
            // Tapping on line numbers / margin: select line via Monaco, don't open IME keyboard
            return;
          }
          const target = editor.getTargetAtClientPoint(touch.clientX, touch.clientY);
          if (target && target.position) {
            editor.setPosition(target.position);
            scheduleUiUpdate();
          }
          enableImeForTyping();
        }
      }
    }, true);

    editorNode.addEventListener('touchcancel', () => {
      clearTimeout(pressTimer);
      isLongPressConfirmed = false;
      isUserTouchDragging = false;
      touchHasMoved = false;
    }, true);

    editorNode.addEventListener('click', e => {
      const target = e.target;
      if (target && target.closest && (target.closest('.selection-handle') || target.closest('#floating-toolbar'))) {
        return;
      }
      if (isMarginTarget(target)) {
        return;
      }
      if (Date.now() < suppressMouseUntil || isLongPressConfirmed || isScrolling || (Date.now() - lastScrollTime) < 300) {
        disableImeForScroll();
        e.stopPropagation();
        e.stopImmediatePropagation();
        e.preventDefault();
        return;
      }
      const t = editor.getTargetAtClientPoint(e.clientX, e.clientY);
      if (t && t.position) {
        editor.setPosition(t.position);
        scheduleUiUpdate();
      }
    }, true);

    function beginHandleDrag(isStart, event) {
      event.preventDefault();
      event.stopPropagation();
      suppressMouseUntil = 0;
      const model = editor.getModel();
      const initialSelection = editor.getSelection();
      if (!model || !initialSelection || initialSelection.isEmpty()) return;

      floatingToolbar.classList.remove('visible');
      isHandleMoving = true;
      clearTimeout(handleStillTimer);

      const fixedAnchor = isStart ? initialSelection.getEndPosition() : initialSelection.getStartPosition();
      const pointerId = event.pointerId;
      const targetHandle = isStart ? startHandle : endHandle;
      if (pointerId !== undefined && typeof targetHandle.setPointerCapture === 'function') {
        try { targetHandle.setPointerCapture(pointerId); } catch (_) {}
      }
      activeDrag = { isStart };
      let pendingDragRaf = null;
      let latestX = event.clientX, latestY = event.clientY;

      function processDrag() {
        pendingDragRaf = null;
        if (!activeDrag) return;
        const target = editor.getTargetAtClientPoint(latestX, latestY);
        if (!target || !target.position) return;
        const moving = target.position;
        const fixedOffset = model.getOffsetAt(fixedAnchor);
        const movingOffset = model.getOffsetAt(moving);
        const start = movingOffset <= fixedOffset ? moving : fixedAnchor;
        const end = movingOffset <= fixedOffset ? fixedAnchor : moving;

        editor.setSelection({
          startLineNumber: start.lineNumber,
          startColumn: start.column,
          endLineNumber: end.lineNumber,
          endColumn: end.column
        });

        const current = editor.getSelection();
        if (current) {
          const wrapRect = container.getBoundingClientRect();
          const editorRect = editorNode.getBoundingClientRect();
          positionForHandle(startHandle, current.getStartPosition(), true, wrapRect, editorRect);
          positionForHandle(endHandle, current.getEndPosition(), false, wrapRect, editorRect);
        }
      }

      function move(e) {
        if (!activeDrag || e.pointerId !== pointerId) return;
        if (!isHandleMoving) {
          isHandleMoving = true;
          floatingToolbar.classList.remove('visible');
        }
        latestX = e.clientX;
        latestY = e.clientY;
        clearTimeout(handleStillTimer);
        handleStillTimer = setTimeout(() => {
          isHandleMoving = false;
          scheduleUiUpdate();
        }, 250);

        if (!pendingDragRaf) pendingDragRaf = requestAnimationFrame(processDrag);
      }

      function finish(e) {
        if (e.pointerId !== pointerId) return;
        clearTimeout(handleStillTimer);
        isHandleMoving = false;
        if (pendingDragRaf) { cancelAnimationFrame(pendingDragRaf); pendingDragRaf = null; }
        if (pointerId !== undefined && typeof targetHandle.releasePointerCapture === 'function') {
          try { targetHandle.releasePointerCapture(pointerId); } catch (_) {}
        }
        window.removeEventListener('pointermove', move, true);
        window.removeEventListener('pointerup', finish, true);
        window.removeEventListener('pointercancel', finish, true);
        activeDrag = null;
        scheduleUiUpdate();
      }

      window.addEventListener('pointermove', move, true);
      window.addEventListener('pointerup', finish, true);
      window.addEventListener('pointercancel', finish, true);
    }

    startHandle.addEventListener('pointerdown', e => beginHandleDrag(true, e));
    endHandle.addEventListener('pointerdown', e => beginHandleDrag(false, e));

    ['touchstart', 'touchmove'].forEach(evt => {
      startHandle.addEventListener(evt, e => { e.preventDefault(); e.stopPropagation(); }, { passive: false });
      endHandle.addEventListener(evt, e => { e.preventDefault(); e.stopPropagation(); }, { passive: false });
    });

    setupInputAreaFocusGuard();
    disableImeForScroll(true);

    editor.onDidChangeCursorSelection(scheduleUiUpdate);
    editor.onDidScrollChange((e) => {
      scheduleUiUpdate();
      const isTouchScroll = isUserTouchDragging || (Date.now() - lastUserTouchDragTime < 350);
      if (isTouchScroll && (!e || e.scrollTopChanged || e.scrollLeftChanged)) {
        isScrolling = true;
        lastScrollTime = Date.now();
        disableImeForScroll();
        clearTimeout(scrollResetTimer);
        scrollResetTimer = setTimeout(() => { isScrolling = false; }, 350);
      }
    });
    editor.onDidLayoutChange(scheduleUiUpdate);
    window.addEventListener('resize', scheduleUiUpdate);
  }

})();
