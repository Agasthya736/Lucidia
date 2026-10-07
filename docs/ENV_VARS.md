# Lucidia Backend — Required Environment Variables

All env vars below must be set in Cloud Run (or `.env` for local dev). **Never commit real secrets to the repository.**

---

## Critical (Production)

| Variable | Required | Description |
|---|---|---|
| `LUCIDIA_JWT_SECRET` | ✅ Yes | HS512 signing key, **minimum 64 characters**. Generate with `openssl rand -base64 64`. |
| `GCS_BUCKET` | ✅ Yes | Google Cloud Storage bucket name for scan image storage. Example: `lucidia-scans-prod`. |
| `SPRING_DATASOURCE_URL` | ✅ Yes | Full JDBC URL for the Cloud SQL PostgreSQL instance. |
| `SPRING_DATASOURCE_USERNAME` | ✅ Yes | Database username. |
| `SPRING_DATASOURCE_PASSWORD` | ✅ Yes | Database password. |
| `SPRING_AI_GOOGLE_GENAI_API_KEY` | ✅ Yes | Gemini API key used server-side for AI synthesis. |
| `LUCIDIA_GOOGLE_CLIENT_ID` | ✅ Yes | OAuth 2.0 Web Client ID for Google Sign-In verification. |
| `IMAGE_ENCRYPTION_KEY_REF` | ✅ Yes | Reference name for the image encryption key (Cloud KMS or Secret Manager). |

## Optional / Defaults

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | HTTP port the app listens on. Cloud Run sets this automatically. |
| `MEDSAM_BASE_URL` | `http://localhost:8001` | MedSAM segmentation service URL. Leave default if not deployed. |
| `LUCIDIA_ALLOW_MOCK` | `false` | **Never set to `true` in production.** Enables mock Google sign-in for local dev only. |
| `SPRING_PROFILES_ACTIVE` | (none) | Set to `prod` on Cloud Run to activate the production config profile. |

---

## Cloud Run Deployment Checklist

```sh
gcloud run deploy lucidia-backend \
  --image gcr.io/YOUR_PROJECT/lucidia-backend \
  --region asia-south1 \
  --platform managed \
  --set-env-vars="SPRING_PROFILES_ACTIVE=prod,GCS_BUCKET=lucidia-scans-prod,MEDSAM_BASE_URL=..." \
  --set-secrets="LUCIDIA_JWT_SECRET=lucidia-jwt-secret:latest,\
SPRING_AI_GOOGLE_GENAI_API_KEY=gemini-api-key:latest,\
LUCIDIA_GOOGLE_CLIENT_ID=google-client-id:latest,\
IMAGE_ENCRYPTION_KEY_REF=image-enc-key-ref:latest,\
SPRING_DATASOURCE_URL=db-url:latest,\
SPRING_DATASOURCE_USERNAME=db-user:latest,\
SPRING_DATASOURCE_PASSWORD=db-pass:latest"
```

---

## GCS Bucket Setup

1. Create the bucket: `gsutil mb -l asia-south1 gs://lucidia-scans-prod`
2. Grant the Cloud Run service account access:
   ```sh
   gsutil iam ch serviceAccount:YOUR_SA@YOUR_PROJECT.iam.gserviceaccount.com:objectAdmin gs://lucidia-scans-prod
   ```
3. Application Default Credentials (ADC) are used automatically on Cloud Run — no key file needed.

---

## MedSAM Service

The MedSAM segmentation service (`medsam-service/`) is **optional**. If `MEDSAM_BASE_URL` is left at the default (`http://localhost:8001`), the pipeline will gracefully skip segmentation. The app is fully functional without it for educational use.

---

## Local Development

Create a `.env` file in `backend/` (gitignored):

```env
JWT_SECRET=dev-only-secret-replace-me-before-any-real-deployment-64chars!!
GCS_BUCKET=                          # leave blank to use local disk
GEMINI_API_KEY=your-gemini-api-key
GOOGLE_CLIENT_ID=747503445476-...    # your OAuth client ID
LUCIDIA_ALLOW_MOCK=true              # allow mock Google sign-in in dev
```
