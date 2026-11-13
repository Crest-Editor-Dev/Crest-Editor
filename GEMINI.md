# Crest-Editor AI Agent Guidelines (GEMINI.md)

Welcome to **Crest-Editor**! This document provides instructions, constraints, and architecture guidelines for AI coding assistants working in this repository.

---

## 🤝 1. Human Collaboration & Decision-Making Protocol (STRICT)

1. **Discuss Before Adding Features**:
   - **Never** add new features, sub-systems, or major components unilaterally.
   - Always propose the idea, explain the reasoning and scope to the human developer, and wait for confirmation before writing code.
2. **Present Multiple Solutions with Trade-offs**:
   - If an issue or feature has more than one valid approach or fix, **do not make the core architectural decision on your own**.
   - Present the alternatives clearly with their pros and cons (e.g., performance vs. complexity vs. maintenance), and let the human decide which path to take.
3. **Ask When Confused or Ambiguous**:
   - Never make blind assumptions if requirements, existing implementations, or intended behaviors are unclear.
   - Stop and ask the human for clarification immediately.

---

## 🛑 2. Developer Environment & Build Rules (CRITICAL)

### 🚫 STRICT PROHIBITION: NO LOCAL GRADLE BUILDS
1. **Never run `gradle`, `./gradlew`, or any Gradle build commands** (such as `assembleDebug`, `assembleRelease`, `build`, etc.) on this machine.
2. **Mobile Data Conservation**: Development occurs on Termux with limited mobile data quota. Downloading Gradle distributions and Android dependencies will deplete the user's data.
3. **CI/CD Build Architecture**: All compilation, tests, and APK builds are performed automatically in the cloud via **GitHub Actions CI/CD workflows**.
4. **Intentional Offline Mode**: `~/.gradle/gradle.properties` contains `org.gradle.offline=true` and proxy restrictions intentionally. **DO NOT attempt to modify, remove, or bypass these settings.**
5. When the user wants to test or build APKs, advise them to commit and push their changes to trigger GitHub Actions.

### ⚙️ Termux Environment & Execution
- **Kernel Sandbox Limitation**: Standard Linux namespaces/containers are not supported on Android/Termux kernels.
- Terminal commands executed via tools must run with `BypassSandbox: true`.

### 📦 Web Bundle Compilation (`editor-core`)
- Building the web editor bundle is allowed locally since it runs lightweight Node/Vite tooling without Gradle:
  ```bash
  cd editor-core && npm run build
  ```
  *(Output is automatically emitted into `app/src/main/assets/editor/` as configured in `vite.config.js`).*

---

## 🏗️ 3. Architecture Overview

Crest-Editor brings a desktop-grade VS Code workflow to Android through a hybrid architecture:

```text
Crest-Editor
├── app/                      # Android Native Shell (Jetpack Compose UI)
│   ├── src/main/java/com/cresteditor/
│   │   ├── MainActivity.kt   # Main Entrypoint & window lifecycle
│   │   ├── core/             # Base utilities, state, and services
│   │   ├── editor/           # WebView wrapper, JS Bridge interface, touch hooks
│   │   ├── runtime/          # PRoot Linux environment & process managers
│   │   ├── ui/               # Compose UI (Tabs, Tree view, Settings, Toolbars)
│   │   └── workspace/        # Project folder, multi-tab state, dirty tracking
│   └── src/main/assets/      # Packaged Monaco Editor & Shiki web assets
│
├── editor-core/              # Monaco + Shiki Web Bundle (Vite + TypeScript)
│   ├── src/                  # Monaco customization, Shiki TextMate, workers
│   ├── index.html            # Editor entry webview HTML
│   └── vite.config.js        # Builds to app/src/main/assets/editor
│
├── terminal-emulator/        # Terminal emulation core (VT100/ANSI parsing)
└── terminal-view/            # Native Android view for terminal rendering
```

### Key Subsystems:
1. **UI Layer (`app/src/main/java/.../ui/`)**: Built entirely with Jetpack Compose and Material 3. Handles window insets, IME (virtual keyboard) padding, file explorer, and tab navigation.
2. **Editor Bridge (`com.cresteditor.editor`)**: Manages the bidirectional bridge between Android Kotlin and Monaco JavaScript inside the WebView (file load/save, dirty state, cursor sync, selection actions).
3. **Syntax & Editor Engine (`editor-core`)**: Monaco Editor powered by `@shikijs/monaco` for accurate TextMate highlighting, alongside inlined web workers for HTML, CSS, JSON, and TS language services.
4. **Terminal & Sandbox (`terminal-emulator`, `terminal-view`, `runtime`)**: PRoot-based environment providing local shell access and Linux developer tooling.

---

## 📜 4. Coding Standards & Guidelines

### Kotlin & Jetpack Compose
- **State Management**: Use reactive state flows (`StateFlow` / `collectAsStateWithLifecycle`). Minimize unnecessary recompositions.
- **Coroutines & Lifecycles**: Scope background work strictly to proper lifecycle coroutine scopes (`lifecycleScope`, `viewModelScope`).
- **Error Handling**: Never silently swallow exceptions. Always log or propagate errors to the UI when user feedback is required.
- **Code Preservation**: Maintain documentation and existing comments unless specifically modifying the associated logic. Explain *why* tricky code exists.

### 🧩 Modular Architecture & Component Naming (STRICT)
- **No Giant / God Files**: Never dump multiple complex widgets, dialogs, or state handlers into one massive screen file (e.g. 500+ lines). Split independent UI controls and widgets into dedicated, focused files under `ui/components/`.
- **Clean Idiomatic Naming (No Unnecessary Brand Prefixes)**:
  - Do NOT prefix generic, reusable components with `Crest...` (e.g. use `NumberField.kt`, `NumberStepper.kt`, `SegmentedControl.kt` — NOT `CrestNumberStepper.kt` or `CrestNumberField.kt`).
  - Reserve `Crest...` strictly for root-level brand entities (like `CrestTheme`, `CrestEditorApp`, `CrestFileDrawer`). Follow the clean conventions of Zed and VS Code.
- **Single Responsibility**: Keep component files focused, typically under 150–200 lines, with clean parameter contracts and zero hidden side effects.

### Editor Core (TypeScript / Vite)
- **Mobile Touch First**: Monaco was designed for desktop; all changes must preserve touch selection handles, context menus, and mobile virtual keyboard (IME) compatibility.
- **Bundle & Memory Efficiency**: Android WebViews run in constrained memory. Keep Shiki themes/grammars and web workers optimized.

---

## 🔀 5. Git & Commit Guidelines

- **Commit Style**: Use Conventional Commits with clear scopes matching existing commit history:
  - `feat(editor): ...`
  - `fix(html): ...`
  - `refactor(ui): ...`
  - `perf(terminal): ...`
- **Clean Diffs**: Avoid modifying formatting or re-indenting unrelated files.
