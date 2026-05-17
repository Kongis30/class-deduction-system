# Email Delivery Implementation Guide

## Goal

This guide explains how the backend email feature is designed, why it is intentionally conservative, and how to enable it safely without accidentally sending unwanted emails.

## Current Design

The backend does **not** send emails directly inside lesson confirmation transactions.

Instead, the flow is:

1. Lesson confirmation succeeds.
2. The backend writes one or more `notification_log` records with status `PENDING`.
3. An admin reviews the logs if needed.
4. An admin manually triggers one batch dispatch call.
5. The backend claims logs one by one, sends them, and updates status to `SUCCESS` or `FAILED`.

This is an **outbox pattern**. It is much safer than sending mail inline because:

- a mail server outage will not break lesson confirmation
- retry and audit are preserved in the database
- duplicate sends are easier to prevent
- admins can inspect what would be sent before enabling live delivery

## Safety Defaults

The system is configured with several guardrails:

- `app.notification.email.enabled=false`
  - no live email will be sent until you explicitly enable it
- manual dispatch only
  - there is no automatic scheduler yet
- strict allowlist enabled by default
  - only recipients listed in `allowed-recipients` can receive mail
- batch size cap
  - one dispatch call can only process a limited number of emails
- claim-before-send
  - logs are moved from `PENDING` to `PROCESSING` before send to reduce duplicate sends from repeated clicks

## Required Configuration

In `backend/src/main/resources/application.yml`, mail-related settings are:

```yml
spring:
  mail:
    host: ${MAIL_HOST:}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}

app:
  notification:
    email:
      enabled: false
      from-address: ${MAIL_FROM_ADDRESS:no-reply@example.com}
      from-name: JIN BEST TUTORING
      strict-allowlist: true
      allowed-recipients:
        - support@example.com
      max-batch-size: 20
      max-retries: 5
```

Before live use, you need to provide:

- SMTP host
- SMTP port
- SMTP username
- SMTP password
- a valid `from-address`

## Recommended Rollout

Use this rollout sequence:

1. Keep `enabled=false`
2. Confirm that lesson confirmation creates `PENDING` rows in `notification_log`
3. Configure SMTP credentials
4. Keep `strict-allowlist=true`
5. Add only internal test inboxes to `allowed-recipients`
6. Set `enabled=true`
7. Trigger `/api/v1/system/notification-logs/dispatch` with a small batch
8. Verify subject, body, sender, and timestamps
9. Only after validation, decide whether to relax the allowlist

## Recommended Provider Choices

For this project, a transactional email provider is better than personal SMTP.

Common options:

- Resend
- SendGrid
- Amazon SES
- Postmark

If you use these, you usually get:

- delivery logs
- bounce handling
- domain verification
- safer production reputation management

## Common Failure Cases

If a log becomes `FAILED`, likely causes are:

- SMTP credentials are wrong
- `from-address` is not configured
- recipient is not in the allowlist
- provider rejected the sender domain
- rendered payload is incomplete

Failed logs can be requeued manually after the problem is fixed.

## Operational Advice

- Do not disable the allowlist on the first day
- Do not add automatic scheduling until manual dispatch is stable
- Keep one or two internal monitoring inboxes in the allowlist for smoke tests
- Review `notification_log` regularly during rollout
- Prefer domain-based transactional email accounts instead of personal mailboxes

## Related Backend Endpoints

- `GET /api/v1/system/notification-templates`
- `PUT /api/v1/system/notification-templates/{id}`
- `GET /api/v1/system/notification-logs`
- `GET /api/v1/system/notification-logs/{id}`
- `POST /api/v1/system/notification-logs/{id}/requeue`
- `POST /api/v1/system/notification-logs/dispatch`
- `GET /api/v1/system/brand-config/current`
- `PUT /api/v1/system/brand-config/current`
