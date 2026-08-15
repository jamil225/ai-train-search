<div align="center">

# 🚆 AI Train Search

### **Autonomous AI-Powered Indian Railways Train Search & Availability Companion**

[![Android](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin_2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![OpenAI](https://img.shields.io/badge/AI-OpenAI_GPT--4o--mini-412991?style=for-the-badge&logo=openai&logoColor=white)](https://openai.com)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

---

<img src="TrainSearch/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="AI Train Search App Icon" />

<p align="center">
  <b>Search Indian Railways trains in plain conversational English, Hindi, or Hinglish.</b><br/>
  Combines natural language AI parsing with real-time seat availability scoring across multi-station origin & destination groups.
</p>

</div>

---

## 🌟 Key Features

- 🎙️ **Native Speech-to-Text (Hindi & English)**: Speak trip requests in Hindi (*"जोधपुर से पुणे 1 सितंबर स्लीपर"*) or English (*"Jaipur to Pune tomorrow"*). Powered by Android SpeechRecognizer with extended silence tolerance.
- 🔀 **Multi-Source & Multi-Destination Expansion**: Express complex multi-station queries (*"Ajmer, Jaipur, Kishangarh, Jodhpur to Pune"*). Automatically resolves station codes (`AII`, `JP`, `KSG`, `JU` $\rightarrow$ `PUNE`).
- 🤖 **Smart LLM Intent Parser**: Uses `gpt-4o-mini` to extract travel dates, classes, and station groups from natural conversation in English, Devanagari Hindi, or Hinglish.
- 🎨 **Station Board Card UI**: Classic dark railway station board theme with status pill badges (**AVL Green**, **RAC Amber**, **WL Red**), prominent `JU ➔ PUNE` route typography, and greyed-out train numbers.
- 🎛️ **Floating Filter & Sort Bar**: Instant multi-dimensional filtering by Rank, Date, Class (`SL`, `3A`, `3E`, `2A`, `1A`), and Availability status (`AVL`, `RAC`, `WL`).
- 🕒 **Non-Blocking Search History Suggestions**: Inline Compose history suggestions container providing one-tap query re-execution without interrupting soft keyboard focus.
- 🔒 **Privacy-First & Read-Only**: Pure search and availability analytics. Never performs transactions, stores personal identity, or requires login details.

---

## 📸 Interface Showcase

<div align="center">

| Search Results Board | Station Route Typography | Filter Chips |
| :---: | :---: | :---: |
| <img src="screenshots/Screenshot_20260815_222102.png" width="240" /> | <img src="screenshots/Screenshot_20260815_223750.png" width="240" /> | <img src="screenshots/Screenshot_20260815_224527.png" width="240" /> |

</div>

---

## ⚙️ Architecture & Data Flow

```mermaid
flowchart TD
    User["🗣️ Spoken Voice or Typed Query<br/>'Ajmer, Jaipur, Jodhpur to Pune 1 Sep sleeper'"] --> Parser["🤖 OpenAI gpt-4o-mini (Llm.kt)"]
    
    Parser -->|"Structured JSON"| Query["📋 TripQuery(origin, destination, dates, classes)"]
    Query --> Resolver["📍 Station Resolver (Stations.kt)"]
    
    Resolver -->|"Station Codes (AII, JP, KSG, JU -> PUNE)"| ConfirmTkt["⚡ ConfirmTkt Real-Time API (ConfirmTkt.kt)"]
    
    ConfirmTkt -->|"Live Snapshots"| Ranker["⚡ Scoring & Ranking Engine (Ranking.kt)"]
    Ranker -->|"AVL > RAC > WL"| UI["📱 Jetpack Compose Station Board (BoardScreen.kt)"]
```

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Jellyfish (2024.1.1) or newer
- **JDK**: Java 21 (`openjdk@21`)
- **Android Device**: Android 8.0 (API 26) or higher

### Building & Running
1. **Clone the Repository**:
   ```bash
   git clone https://github.com/jamil225/ai-train-search.git
   cd ai-train-search/TrainSearch
   ```

2. **Run Unit Tests**:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

3. **Install Debug Build on Phone**:
   ```bash
   ./gradlew :app:installDebug
   ```

---

## 🛡️ Security & Privacy

This application is strictly **read-only** and uses official public endpoints to query live train availability.
- No payment or booking functionality.
- No passenger details or identity collection.
- API keys are stored locally on-device using Android `SharedPreferences` and are never committed to git.
