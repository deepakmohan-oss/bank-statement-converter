# OrtúsPro — AU Bank Statement Converter

Kotlin/Ktor backend + React frontend. Deploys to Railway via nixpacks.

## Supported Banks
Commonwealth, NAB, Westpac, ANZ, Macquarie, BOQ, Auswide,
Bankwest, Bendigo, ING, St George

## Architecture
- **Backend**: Kotlin + Ktor (Netty), Apache PDFBox, Apache POI (XLSX)
- **Frontend**: React + Vite (built into backend static resources)
- **OCR**: Tesseract + Poppler (system packages, CLI-based)
- **Deploy**: Railway (nixpacks builder)

## Local Development
```bash
# Backend
cd backend && ./gradlew run

# Frontend (separate terminal)
cd frontend && npm install && npm run dev
```

## Railway Deployment
Push to GitHub — Railway auto-deploys via nixpacks.toml.
After deploy, check `/health` for OCR status.

## Endpoints
- `GET  /health`          — status + OCR availability + supported banks
- `POST /api/upload`      — upload PDF, returns parsed Statement JSON
- `POST /api/export/csv`  — POST Statement JSON, returns CSV download
- `POST /api/export/xlsx` — POST Statement JSON, returns XLSX download
- `POST /api/merge`       — POST List<Statement> JSON, returns merged Statement
