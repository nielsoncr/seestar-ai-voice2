# Project Plan

SeeStarVoice2 Phase 1: Implement offline TTS, local LLM for intent processing, and wake word detection ("SeeStar"). The UI is a single page with a top display for feedback/images and a bottom resizable log panel. Focus on converting vocal prompts to actions. Target device is Galaxy Tab A9+. Offline operation is mandatory.

## Project Brief

# SeeStarVoice2 Project Brief


## Features
*   **Offline Voice Activation & Intent Processing**: Implements a dedicated "SeeStar" wake word listener and utilizes
 an on-device Large Language Model (LLM) to parse vocal commands into telescope actions without requiring an internet connection.

*   **On-Device AI Feedback**: Provides real-time text and vocal feedback using offline Text-to-Speech
 (TTS), allowing the user to receive status updates and command confirmations audibly.
*   **Adaptive Command Center UI**: A
 single-page Material 3 interface optimized for the Galaxy Tab A9+, featuring a primary display for telescope imagery/feedback and a res
izable, hideable log panel for technical monitoring.
*   **Autonomous Action Engine**: Logic-ready stubs for Phase
 1 actions including telescope arm deployment, object focusing, photo capture/display, and visibility queries.

## High-Level Technical
 Stack
*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose with Material Design 3 (
M3)
*   **Navigation**: Jetpack Navigation 3 (State-driven architecture)
*   **Adaptive
 Strategy**: Compose Material Adaptive library (optimized for tablet multi-pane layouts)
*   **Concurrency**: Kotlin Coroutines & Flow

*   **Intelligence Layer**: On-device LLM & TTS (utilizing frameworks like MediaPipe or TensorFlow Lite
 for offline execution)
*   **Display & Media**: Coil for image loading and CameraX for potential local preview integration

## Implementation Steps

### Task_1_UI_Foundation: Implement the Material 3 theme with a vibrant color scheme, full edge-to-edge display, and the adaptive main screen optimized for Galaxy Tab A9+. Create the single-page interface featuring a top feedback/image display area and a resizable, hideable bottom log panel.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - M3 theme with light/dark support and vibrant colors implemented
  - Main screen with feedback area and resizable log panel functional
  - Edge-to-edge display enabled
  - App builds and runs on tablet emulator/device
- **StartTime:** 2026-05-07 16:23:15 EDT

### Task_2_Offline_Intelligence: Integrate MediaPipe LLM Inference for offline intent processing and configure the Android Text-to-Speech (TTS) engine for offline audible feedback. Ensure models are bundled or downloadable for offline use.
- **Status:** PENDING
- **Acceptance Criteria:**
  - MediaPipe LLM dependency added and functional offline
  - Offline TTS provides audible confirmation of intents
  - Intent parsing logic correctly identifies basic telescope commands

### Task_3_Voice_Activation_And_Logic: Implement 'SeeStar' wake word detection using a suitable offline library. Connect the wake word trigger to the LLM intent processor and implement the Autonomous Action Engine stubs (arm deployment, focusing, photo capture).
- **Status:** PENDING
- **Acceptance Criteria:**
  - 'SeeStar' wake word detection triggers the listening state
  - Voice commands are processed into actions via the LLM
  - Action engine stubs log their execution to the UI log panel

### Task_4_Finalize_And_Verify: Create an adaptive app icon matching the 'SeeStar' theme. Perform a final verification of the application's stability, ensuring all offline components work in unison without crashes and adhere to Material 3 design guidelines.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Adaptive app icon implemented
  - Full voice-to-action flow verified offline
  - Build passes and app does not crash
  - Material 3 and Edge-to-Edge requirements fully met

