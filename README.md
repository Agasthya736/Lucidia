# Lucidia

Multi-agent AI system for CT scan report generation. Two independent vision
agents read each scan, an arbiter surfaces disagreement rather than silently
resolving it, an explainability agent produces saliency heatmaps, and a
verifier cross-checks every claim before the draft reaches the clinician.

> **Important:** Lucidia is a documentation and second-read support tool.
> It does not make diagnostic decisions — every report requires explicit
> clinician review and sign-off before it is finalized.

## Repo layout

```
lucidia/
├── backend/          Spring Boot API — auth, orchestration, agents, image vault
│   └── src/main/java/com/lucidia/backend/
│       ├── agents/           Vision A, Vision B, Arbiter, Explainability, Writing, Verifier
│       ├── orchestrator/     PipelineOrchestrator + PipelineState
│       ├── api/              REST controllers
│       ├── auth/             JWT + OAuth2 resource server
│       ├── scan/             Image vault (AES-256 at rest, DICOM de-identification)
│       ├── audit/            Hash-chained tamper-evident audit log
│       ├── triage/           Edge-AI triage result ingestion
│       ├── synthesis/        Report synthesis utilities
│       ├── responsibleai/    Responsible-AI guardrails
│       ├── quota/            Usage quota enforcement
│       ├── dto/              Shared data-transfer objects
│       └── config/           Security, CORS, WebSocket config
├── medsam-service/   Python/FastAPI sidecar — MedSAM segmentation & HU-value extraction
├── mobile/           Flutter client — capture, edge-AI triage, report review & sign-off
├── docs/             Architecture, evaluation methodology, ADRs
│   └── system_design.md
└── .github/workflows/  CI
```

## Pipeline

```
CT scan ──► Vision A ─┐
                      ├─► Arbiter (consensus check; flags disagreement)
           Vision B ──┘         │
                                ▼
                      Explainability (saliency heatmap)
                                │
                                ▼
                      Writing (structured report draft)
                                │
                                ▼
                      Verifier (cross-checks every claim)
                                │
                                ▼
                      Clinician review + sign-off  ──► FINALIZED
```

The dual-agent + arbiter step is the core research contribution: two
independent agents read the same scan and the arbiter explicitly surfaces
disagreement instead of silently averaging or masking it.

See [`docs/system_design.md`](docs/system_design.md) for the full
architecture, security model, and evaluation methodology.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4.1, Java 21 |
| Agent orchestration | Spring AI 2.0 (`ChatClient`, tool calling), custom `PipelineOrchestrator` |
| Auth | Spring Security · JWT (JJWT 0.12) · OAuth2 resource server · Google Sign-In |
| Persistence | PostgreSQL 16 · Spring Data JPA · Flyway migrations |
| Real-time | WebSocket / STOMP (Spring Integration) |
| Segmentation sidecar | Python · FastAPI · MedSAM (`medsam_vit_b`) · PyTorch |
| Mobile | Flutter (Dart ≥ 3.12) · `local_auth` · `flutter_secure_storage` |
| PDF export | OpenPDF 1.3 |

## Getting started

### Prerequisites

- Java 21
- Maven (or use the included `./mvnw` wrapper)
- Docker & Docker Compose
- Flutter SDK ≥ 3.12 (for mobile)

### 1 — Start supporting services

```bash
docker compose up -d
```

This starts:
- **PostgreSQL 16** on port `5433` (mapped from container's `5432`)
- **MedSAM service** on port `8001`

> The MedSAM container requires the model checkpoint at
> `medsam-service/work_dir/MedSAM/medsam_vit_b.pth`. GPU support is
> available by uncommenting the `deploy.resources` block in
> `docker-compose.yml`.

### 2 — Configure environment variables

```bash
cp backend/.env.example backend/.env
# then edit backend/.env with real values
```

| Variable | Description |
|---|---|
| `GEMINI_API_KEY` | Google Gemini API key (used by Spring AI) |
| `JWT_SECRET` | Secret for signing JWTs — use a long random string |
| `DB_USERNAME` | PostgreSQL username (default: `lucidia`) |
| `DB_PASSWORD` | PostgreSQL password |

### 3 — Run the backend

```bash
cd backend
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

### 4 — Run the mobile app

```bash
cd mobile
flutter pub get
flutter run
```

## MedSAM service

The `medsam-service/` sidecar exposes two endpoints:

| Endpoint | Method | Description |
|---|---|---|
| `/segment` | `POST` | Accepts a CT slice image + bounding-box prompt; returns mask, HU statistics, and confidence score |
| `/health` | `GET` | Liveness check; reports `device` (`cuda` or `cpu`) |

## Security

- **TLS 1.3** in transit.
- **AES-256** at rest for stored images; keys from a secrets manager (never in source).
- **DICOM de-identification** — patient metadata stripped before storage or agent processing.
- **Tamper-evident audit log** — hash-chained entries; altering any past entry invalidates all subsequent hashes.
- **RBAC** — clinicians see only their assigned scans/reports; admins can view audit logs but not raw scan content by default.
- **Biometric unlock** on mobile (`local_auth`).

## Status

Active development. Backend module structure and core pipeline are in place.
Agent implementations are being wired to model providers; mobile UI is under
active development.

## License

TBD.
