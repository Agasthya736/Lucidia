# VeriRad — System Design

Fresh architecture, superseding earlier drafts. Multi-agent CT scan report
generation with a dual-agent consensus layer, explainability, edge AI
pre-screening, and a Spring Boot backend.

## Pipeline

```
CT scan --> Vision A + Vision B (independent reads, in parallel)
        --> Arbiter (compares reads, flags disagreement rather than averaging)
        --> Explainability (saliency/heatmap over the region driving the finding)
        --> Writing (drafts structured report sections)
        --> Verifier (checks every claim against the vision findings)
        --> Clinician review + sign-off (required before FINALIZED)
```

The dual-agent + arbiter step is the core research contribution: instead of
trusting a single vision agent's read, two independent agents analyze the
same scan and an arbiter explicitly surfaces disagreement rather than
silently resolving it. This gives a natural ablation for evaluation
(single-agent vs. dual-consensus accuracy and hallucination rate) and is
a more honest failure mode than one model quietly being wrong.

## Stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3.4, Java 21 |
| Agent orchestration | Spring AI (ChatClient, tool calling); custom `PipelineOrchestrator` supervisor to start, migrate to Spring AI's Sequential/Parallel agent primitives as agents mature |
| Auth | Spring Security, JWT (OAuth2 resource server), biometric unlock on mobile |
| Persistence | PostgreSQL |
| Image storage | Encrypted vault (AES-256 at rest), DICOM de-identification before any agent sees the scan |
| Mobile | TBD (Flutter recommended) - handles capture, edge AI triage, report review/sign-off |
| Edge AI | On-device triage model (TFLite / ONNX Runtime Mobile) - flags obviously-normal scans and gives instant low-confidence signal before upload |

## Security model for CT scan data

- TLS 1.3 in transit, always.
- AES-256 at rest for stored images; encryption keys from a secrets manager
  (Vault / cloud KMS) in any environment beyond local dev - never in source.
- DICOM metadata stripped (patient name, ID, institution tags) before
  storage or agent processing.
- Tamper-evident audit log: every access/edit writes a hash-chained entry
  (`AuditLogEntry` - each entry hashes the previous one). Altering a past
  entry breaks every hash after it, which is detectable on verification.
  This is a hash chain, not a blockchain - no distributed consensus needed
  for a single-writer log, but the same tamper-evidence property.
- Role-based access: clinicians see only scans/reports they're assigned to;
  admins can view audit logs but not raw scan content by default.

## Data contract

`PipelineState` (see `orchestrator/PipelineState.java`) is the shared
object every agent reads/writes. Its full history is what gets persisted
per job and is the artifact used for evaluation - entity/finding accuracy,
hallucination rate (verifier flags vs. ground truth), and the consensus
ablation above.

## Human-in-the-loop

VeriRad is documentation/second-read support, not a diagnostic system.
No report reaches `FINALIZED` state without explicit clinician sign-off
via the mobile client. This should be stated plainly in the app itself,
not just in this doc.

## Open items

- [ ] Choose LLM/vision model provider(s) for Vision A vs. Vision B -
      using two *different* providers/architectures (not the same model
      twice) makes the consensus check meaningful rather than redundant.
- [ ] Dataset: synthetic or public de-identified CT data recommended over
      real patient data for a first project.
- [ ] Decide mobile framework (Flutter vs. React Native) and edge AI
      runtime accordingly (TFLite pairs naturally with either; ONNX Runtime
      Mobile if you want one model format across platforms).
- [ ] Evaluation harness: single-agent baseline, dual-consensus system,
      and per-agent ablations, scored via entity precision/recall, ROUGE/
      BERTScore, hallucination rate, and clinician-rated usability.
