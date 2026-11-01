# Crest-Editor

A high-performance Android code editor built with Jetpack Compose and a desktop-grade editor engine, designed for mobile developers who want a VS Code-like workflow on Android.

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(SDK%2026--34)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](#)
[![Editor](https://img.shields.io/badge/Editor-Monaco%20Editor%20(VS%20Code)-007ACC?style=for-the-badge&logo=visualstudiocode&logoColor=white)](#)
[![Sandbox](https://img.shields.io/badge/Linux%20Sandbox-PRoot%20%2B%20Terminal-0D597F?style=for-the-badge&logo=alpinelinux&logoColor=white)](#)
[![License](https://img.shields.io/badge/License-MIT-brightgreen?style=for-the-badge)](#)

## Overview

Crest-Editor brings the feel of a desktop IDE to Android devices. It combines a touch-optimized Monaco editor, a Linux-based development environment, and a native Compose interface to provide a fast, flexible, and familiar coding experience for mobile development.

This project aims to make coding on Android feel less like a compromise and more like a proper workspace: multi-tab editing, terminal access, filesystem tools, settings management, and developer workflows that work well on smaller screens.

## Key Features

### Desktop-grade code editor
- Monaco editor with VS Code-style editing behavior
- Touch-aware selection and interaction model for mobile screens
- Floating actions for copy, paste, select all, and editing shortcuts
- Keyboard-friendly workflow with support for common developer actions

### Linux development environment
- PRoot-based sandbox for running Linux tooling on Android
- Built-in terminal for shell workflows and project tasks
- Support for common developer runtimes and command-line tools
- Workspace access for local files and project folders

### Android-native experience
- Jetpack Compose UI designed for mobile workflows
- Multi-tab workspace layout
- Settings screen inspired by VS Code preferences
- Auto-save and dirty state tracking for editing reliability

### VS Code-style configuration
- JSON-based settings management
- Reactive configuration updates
- A familiar editor experience for users coming from desktop IDEs

## Architecture

```text
CrestEditor Android Shell
    |
    +-- Jetpack Compose UI
    |   +-- Tabs and workspace explorer
    |   +-- Settings and preferences
    |   +-- Terminal and developer tools
    |
    +-- Monaco Editor Core
    |   +-- Syntax highlighting and editing
    |   +-- Mobile-focused interactions
    |
    +-- Runtime and Sandbox Layer
        +-- Linux environment via PRoot
        +-- Terminal session management
        +-- File system and project access

    +-- Core Management Layer
        +-- Settings manager
        +-- Workspace manager
        +-- Process and terminal bridge
```

## Project Structure

```text
Crest-Editor/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/
│   │   ├── java/com/cresteditor/
│   │   ├── core/
│   │   ├── editor/
│   │   ├── runtime/
│   │   ├── settings/
│   │   ├── ui/
│   │   └── workspace/
│   └── build.gradle.kts
├── editor-core/
│   ├── src/
│   ├── vite.config.js
│   └── package.json
├── README.md
├── LICENSE
└── gradle.properties
```

## Configuration Reference

The project uses a `settings.json`-style configuration layer for editor behavior and runtime preferences.

| Setting key | Type | Default | Description |
| --- | --- | --- | --- |
| `files.autoSave` | `string` | `afterDelay` | Save mode for dirty files |
| `files.autoSaveDelay` | `number` | `1000` | Delay before auto-save in milliseconds |
| `files.showHiddenFiles` | `boolean` | `false` | Show dotfiles in workspace view |
| `editor.fontSize` | `number` | `14` | Editor font size |
| `editor.tabSize` | `number` | `4` | Number of spaces per tab |
| `editor.wordWrap` | `string` | `on` | Word wrap mode |
| `editor.minimap.enabled` | `boolean` | `false` | Toggle minimap |
| `editor.theme` | `string` | `vs-dark` | Editor color theme |
| `terminal.integrated.fontSize` | `number` | `13` | Terminal font size |

## Requirements

- Android SDK with compile support for SDK 34
- Minimum Android SDK: 26
- JDK 17 or newer
- Node.js 18+ for editor-core build steps
- Arm64 target support for native runtime components

## Build and Run

### Build Monaco editor core

```bash
cd editor-core
npm install
node ./node_modules/vite/bin/vite.js build
```

This generates the editor assets used by the Android app.

### Build the Android app

```bash
./gradlew assembleDebug
```

The produced APK is typically located under:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## License

This project is licensed under the MIT License. See the `LICENSE` file for more information.

## Contributing

Contributions are welcome. If you want to improve the editor, fix bugs, or add features, open an issue or submit a pull request with a clear description of the change.

## Contact

For project updates and discussions, use the GitHub repository and issue tracker.
