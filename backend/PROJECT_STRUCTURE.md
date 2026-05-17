# Backend Project Structure Guide

This document helps contributors understand the current backend layout, where real implementations live, and how future modules should be added.

## Recommended Reading Order

If you are new to the backend, read the project in this order:

1. `backend/DEVELOPMENT_PROGRESS.md`
   Use it to understand the current delivery stage and the next priority module.
2. `backend/src/main/resources/application.yml`
   Use it to inspect ports, datasource settings, Redis, OpenAPI, and timezone configuration.
3. `backend/src/main/java/com/classdeduction/system/ClassDeductionSystemApplication.java`
   Use it to understand the Spring Boot entry point and component scanning boundary.
4. `backend/src/main/java/com/classdeduction/system/modules/base/`
   This is the first fully implemented business module and the best style reference for future work.

## Directory Snapshot

```text
backend/
├── DEVELOPMENT_PROGRESS.md
├── PROJECT_STRUCTURE.md
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/classdeduction/system/
    │   │   ├── ClassDeductionSystemApplication.java
    │   │   ├── common/
    │   │   ├── config/
    │   │   ├── infrastructure/
    │   │   └── modules/
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/com/classdeduction/system/
```

## Responsibility by Area

### `common/`

Shared project-level capabilities.

- `common/config/`
  - `SecurityConfig`: current lightweight security setup
  - `WebMvcConfig`: local frontend-backend CORS support
- `common/controller/`
  - `HealthController`: health endpoint
- `common/dto/`
  - legacy DTOs kept for early skeletons
  - `common/dto/PageQuery` is transitional only
- `common/exception/`
  - `BusinessException`
  - `GlobalExceptionHandler`
- `common/model/`
  - `ApiResponse`
  - `PageQuery`
  - `PageResponse`
  - `BaseEntity`
- `common/util/`
  - `JsonHelper`
- `common/time/`
  - shared application timezone helpers

### `config/`

Framework-facing configuration that does not belong under `common/`.

- `MybatisPlusConfig`
  - paging interceptor
  - optimistic locking
  - automatic audit field filling

### `modules/`

Business modules split by domain. This is the main delivery area for future work.

Current modules:

- `auth/`: authentication and authorization baseline implementation
- `base/`: base data module and current reference implementation
- `enrollment/`: enrollment orders, course packages, payment records, and receipt query implementation
- `schedule/`: recurring rules, generated occurrences, classroom occupancy, and adjustment logging
- `lesson/`: lesson confirmation, rule-driven deduction, ledger, and balance snapshot implementation
- `systemconfig/`: lesson status rules, brand settings, notification templates, notification logs, and manual email dispatch

## Reference Module: `modules/base/`

`modules/base/` is the first complete business module and should be reused as the structural template for new modules.

```text
modules/base/
├── controller/
├── dto/
├── entity/
├── mapper/
├── service/
├── service/impl/
└── vo/
```

Typical call chain:

`Controller -> Service -> ServiceImpl -> Mapper`

Typical responsibilities:

- `dto/`: request payloads and query objects
- `entity/`: table-mapped persistence models
- `mapper/`: MyBatis-Plus mappers
- `service/impl/`: business rules and transactions
- `vo/`: stable response payloads

## Implemented vs Skeleton Areas

### Already implemented

- `modules/auth/**`
- `modules/base/**`
- `modules/enrollment/**`
- `modules/schedule/**`
- `modules/lesson/**`
- `common/model/**`
- `common/exception/**`
- `common/config/**`
- `config/MybatisPlusConfig.java`
- `common/controller/HealthController.java`
- `common/time/**`

### Still evolving

- `modules/systemconfig/**`

This module now has real implementations for brand config and notifications, but it is still expanding and remains the main place for future runtime settings.

## Annotation and Readability Conventions

The backend relies on a consistent annotation stack:

- Spring annotations: `@RestController`, `@RequestMapping`, `@Validated`
- Validation annotations: `@NotBlank`, `@NotNull`, `@Min`, `@Email`
- MyBatis-Plus annotations: `@TableName`, `@TableId`, `@TableLogic`, `@Version`
- Lombok annotations: `@Data`, `@Getter`, `@Setter`, `@Builder`, `@EqualsAndHashCode`, `@ToString`
- OpenAPI annotations: `@Tag`, `@Operation`, `@Schema`, `@ParameterObject`
- Auth access annotations: `@RequireRoles`

The goal is to make the codebase readable even before opening the database design document.

## Runtime and Documentation Entry Points

- Base API path: `/api`
- Health check: `/api/health`
- OpenAPI JSON: `/api/v3/api-docs`
- Swagger UI: `/api/swagger-ui.html`

## Recommended Expansion Order

1. `systemconfig/`: system rule management and richer notification operations
