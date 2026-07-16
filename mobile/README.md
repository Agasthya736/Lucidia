# VeriRad mobile

Not yet scaffolded.

Planned responsibilities:
- CT scan capture / import
- On-device edge AI triage (TFLite or ONNX Runtime Mobile) before upload
- Biometric-gated login (JWT session against the Spring Boot backend)
- Pipeline progress view (vision -> arbitration -> explainability -> writing -> verified)
- Report viewer with heatmap overlay and clinician sign-off flow

Framework decision (Flutter vs. React Native) pending - see `docs/system_design.md` open items.
