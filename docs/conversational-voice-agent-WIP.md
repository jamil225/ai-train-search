# Conversational Voice Search Agent — WIP

Status: **brainstorming in progress**

## Goal
A live voice conversation interface for TrainSearch: the user speaks their
trip request, the system listens, searches, shows results as a table, and
*speaks* a summary back — so the interaction feels like a conversation, not
a form. Designed for less-educated users; multilingual with English and
Hindi (Hindi first).

## Open questions being worked through
- Deployment target: Android-native (building on the just-merged
  `SpeechManager`), a new web interface, or both sharing a backend agent
  service?
- Real-time conversation tech: OpenAI Realtime API vs. other providers vs.
  a simpler turn-based (record → transcribe → respond → speak) loop?
- Cost/latency/accuracy tradeoffs for Hindi speech recognition and TTS
  specifically.
- How spoken summaries and the results table stay in sync (what gets said
  vs. what only appears visually).

This file is a placeholder to give the PR something to track against. It
will be replaced by the actual PRD/design doc once brainstorming concludes.
