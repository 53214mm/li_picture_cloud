# Round 13 implementation record

## Goal

Turn architectural, security, coverage, and production-configuration expectations into repeatable build checks.

## Changes

1. Domain source dependency guard test.
2. JaCoCo report generation and CI artifact upload.
3. High-severity production npm dependency audit.
4. Vulnerable production transitive dependencies refreshed in lockfile.
5. Duplicate SLF4J provider excluded from DashScope SDK.
6. Production profile disables documentation endpoints and SQL logging, secures cookies, and requires an explicit collaboration origin.

## Acceptance commands

- Backend: Maven `verify` with test profile.
- Frontend: clean install, lint, test, build, bundle budget, production audit.
- Repository: diff whitespace check and clean working tree after commit.
