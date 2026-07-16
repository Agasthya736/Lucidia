# Lucidia

Multi-agent AI system for CT scan report generation, with a dual-agent
consensus/arbitration layer, an explainability agent producing saliency
heatmaps, and edge AI pre-screening on the mobile client.

VeriRad is a documentation and second-read support tool. It does not
make diagnostic decisions - every report requires clinician review and
sign-off before it is finalized.

## Repo layout

```
verirad/
├── backend/         Spring Boot API - auth, orchestration, agents, image vault
├── mobile/           Mobile client (capture, edge AI triage, report review)
├── docs/               Architecture, evaluation methodology, ADRs
└── .github/workflows/    CI
```

## Pipeline

```
CT scan --> Vision A + Vision B (independent reads)
        --> Arbiter (consensus check, flags disagreement)
        --> Explainability (saliency heatmap)
        --> Writing (structured report draft)
        --> Verifier (checks claims against findings)
        --> Clinician review + sign-off
```

See [`docs/system_design.md`](docs/system_design.md) for the full architecture,
threat model for CT image security, and evaluation methodology.

## Status

Early scaffold. Backend module structure is in place; agent implementations
are stubbed pending model provider selection and dataset decisions.

## Getting started (backend)

```bash
cd backend
./mvnw spring-boot:run
```

Requires a running PostgreSQL instance and a configured LLM API key -
see `backend/src/main/resources/application.yml` for the environment
variables it expects.

## License

TBD.
