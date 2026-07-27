# Edge AI STT - Native Android (Kotlin)

A high-performance Native Android application for **On-Device Speech-to-Text**, **FunctionGemma Tool Calling / Intent Triage**, and **Real-Time Edge AI Performance Vitals Monitoring**.

---

## ⚡ Features

1. **FunctionGemma Tool Calling (Google AI Edge / MediaPipe)**:
   - Evaluates user transcript & text input locally on-device.
   - Detects safety intents and triggers native Android tool actions (e.g. `emergency_helper`).
2. **Whisper STT Audio Streaming (Sherpa-ONNX & AudioRecord)**:
   - High-efficiency 16kHz 16-bit PCM microphone streaming via native Android `AudioRecord`.
   - Voice Activity Detection (VAD) audio segmenting.
3. **AI Performance Vitals Dashboard & HUD**:
   - **Token Speed**: Live Tokens Per Second (`tok/s`) during LLM generation.
   - **Latency**: Time-To-First-Token (`TTFT ms`), Total Generation Time (`ms`), STT Latency (`ms`).
   - **Battery Draw**: Battery level (`%`), temperature (`°C`), charge current (`mA`).
   - **System Throughput**: Used RAM vs Total RAM (`MB`), Native C++ Heap, Thermal Status.

---

## 🚀 Building & Running

### Requirements
- Android Studio Ladybug or newer
- Android SDK 35 (Target API 35, Min API 26)
- JDK 17+

### Terminal Build
```bash
./gradlew assembleDebug
```
