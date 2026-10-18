# JIN BEST TUTORING Class Deduction System

This repository uses a split frontend and backend structure:

- `doc/`: business, UI, database, and delivery planning documents
- `docs/`: confirmed business rules and maintenance references
- `frontend/`: Vue 3 + Vite admin application
- `backend/`: Spring Boot backend service

The confirmed 1-on-1 teacher-hour field definitions and schedule-period rules are in
[docs/one-on-one-hours-business-rules.md](docs/one-on-one-hours-business-rules.md).

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

The V2 frontend proxies `/api` to port `8085` by default. Start the backend with
the `group-course-v2` profile and point it at the V2 database:

```bash
cd backend
export DB_V2_URL='jdbc:mysql://127.0.0.1:3306/class_deduction_system_v2?useUnicode=true&characterEncoding=utf8&serverTimezone=America/New_York'
export DB_V2_USERNAME='<database user>'
export DB_V2_PASSWORD='<database password>'
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
SPRING_PROFILES_ACTIVE=group-course-v2 mvn spring-boot:run
```

To open the local site on a phone, connect it to the same Wi-Fi network and use
`http://<computer LAN IP>:5173`. Browser API requests go through the Vite proxy
on the computer; the phone does not need to reach the backend port directly.
