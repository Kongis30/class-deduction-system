# JIN BEST TUTORING Class Deduction System

This repository uses a split frontend and backend structure:

- `doc/`: business, UI, database, and delivery planning documents
- `frontend/`: Vue 3 + Vite admin application
- `backend/`: Spring Boot backend service

## Local Environment

- Frontend: `Node.js 20.19+`
- Backend: `Java 17+` for the Spring Boot `3.5.x` service

If `openjdk@17` is installed through Homebrew, set `JAVA_HOME` explicitly before running backend commands.

## Recommended Startup

### Frontend

```bash
cd frontend
npm install
npm run dev
```

### Backend

```bash
cd backend
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
mvn spring-boot:run
```
