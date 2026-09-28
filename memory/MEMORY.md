# Project Memory

- [Highest priority UI principle](feedback/user-perspective.md) — page design must stay simple, start from the user's workflow, and minimize the learning cost of using the system
- [User perspective](feedback/user-perspective.md) — always design from the end-user's vantage point, not the developer's
- [Deploy to production convention](feedback/deploy-to-production.md) — "更新到线上"默认包含最新代码（jar+dist）与全部未应用的 DB schema 迁移，部署后必须验证
- [Git collaboration and release convention](feedback/git-workflow.md) — small UI/localized changes are made directly on `main`; large features, risky fixes, or cross-module changes use temporary branches and merge back to `main`
