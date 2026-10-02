# R14 backend verification reproducibility — 2026-10-02 UTC

## Final result

- Repository: /workspace/scratch/058c18b9b398/nexus-mainline-r11
- HEAD at verification: 2bddf43280cbfac0349b510892a3cba8a07bf54e; backend source/build configuration unchanged from HEAD: True
- Clean offline verify: BUILD SUCCESS, 722 tests, 0 failures, 0 errors, 4 intentional skips; 718 passed
- Skips: one operator-only live AI smoke and three explicitly opt-in Redis tests
- Main and test compilation, jar/repackage, JaCoCo report and original coverage gates passed
- Fresh target directory and isolated coverage from the final clean run:
- com/li/lipicturecloud/domain/airuntime: 447/504 branches (88.69%), gate 85%
- com/li/lipicturecloud/domain/companion: 541/626 branches (86.42%), gate 85%

## Exact command

Run from the repository above. No source, POM, global settings, credentials, TLS validation, sandbox, IPC or attach-security settings were changed.

```bash
JAVA_HOME=/workspace/shared/nexus-tooling/jdk-21.0.12.1+1 \
PATH="/workspace/shared/nexus-tooling/jdk-21.0.12.1+1/bin:$PATH" \
MAVEN_USER_HOME=/tmp/lpc-r14-maven \
MAVEN_OPTS=-Dmaven.user.home=/tmp/lpc-r14-maven \
COMPANION_VISION_LIVE_TEST=false \
./mvnw -o -B -ntp \
  -Dmaven.repo.local=/tmp/lpc-r14-maven/repository \
  -Dspring.profiles.active=test \
  '-DargLine=-javaagent:/tmp/lpc-r14-maven/repository/org/jacoco/org.jacoco.agent/0.8.13/org.jacoco.agent-0.8.13-runtime.jar=destfile=/workspace/scratch/058c18b9b398/nexus-mainline-r11/target/jacoco.exec -javaagent:/tmp/lpc-r14-maven/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  clean verify
```

The additional Mockito startup agent is a test-only process option from Mockito's official Java 21 guidance; the original JaCoCo agent and project gates remain active. Cached Mockito 5.17.0 JAR SHA-1 matched Maven Central's artifact checksum; its manifest declares Premain-Class org.mockito.internal.PremainAttach.

Official guidance: https://javadoc.io/static/org.mockito/mockito-core/5.17.0/org.mockito/org/mockito/Mockito.html#0.3

## Prepared toolchain

- Full Eclipse Temurin 21.0.12.1+1: /workspace/shared/nexus-tooling/jdk-21.0.12.1+1
- Official archive size 207473347 bytes; SHA-256 ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94, checked against Adoptium API metadata saved beside the JDK
- Maven Wrapper 3.3.4 / distribution 3.9.15; writable wrapper and dependency cache /tmp/lpc-r14-maven
- Portable Maven fallback: /tmp/lpc-r14-tools/apache-maven-3.9.15; ZIP matched official SHA-512

The preinstalled Java 21 runtime has jdk.compiler but lacks ct.sym and cannot compile --release 21. The complete JDK resolves this without compiler flag changes. System Java with its existing trust store performed certificate-verified dependency preparation via the executor-provided proxy. Because portable vendor Java lacks that proxy's trusted CA, final verification is offline. No CA import or TLS bypass was used. Local proxy ports are per-execution, so any future online preparation must derive a fresh temporary process-local Maven proxy config from that execution's existing environment, never reuse an old port.

Preparation goals under system Java were dependency:go-offline and dependency:get -Dartifact=org.apache.maven.surefire:surefire-junit-platform:3.5.5.

## Preserved evidence

- baseline-selfattach-reports/ and baseline-selfattach-verify.log: 722 tests, 366 identical MockMaker setup errors, 0 assertion failures, 4 skips
- startup-agent-success-reports/ and startup-agent-success-verify.log: first green run; coverage included previous execution data
- clean-success-reports/, clean-success-jacoco/, clean-success-jacoco.exec, clean-success-verify.log: final independent evidence

All directories above are relative to /workspace/shared/nexus-tooling/r14-validation.

## Remaining R14/API E2E prerequisites

- R14 remains V3 and requires frontend validation plus existing browser E2E/real API paths; a backend verify pass alone does not complete it
- Real API/browser E2E uses test,e2e profiles with in-memory H2 fixtures; no MySQL, COS or real AI credentials are needed (external calls are stubbed)
- Requires isolated Redis 7 at 127.0.0.1:6380, no password, database 15. At the original verify checkpoint Redis was absent; the subsequent R14-API-VALIDATION round prepared official Redis7.2.11 and passed all3 opt-in tests plus the HTTP story. Every test-owned instance is shut down afterward
- Separate Redis collaboration gate defaults to 127.0.0.1:6379 and is enabled by -Dredis.integration.enabled=true; override redis.port only when intentionally targeting an isolated test Redis
- Node 22/frontend dependencies, functioning Chromium runtime with permitted IPC, and available localhost ports 18124/backend and 15173/frontend
- Existing frontend command npm run test:e2e starts the backend through scripts/start-e2e-backend.mjs and the strict-port Vite server; reuseExistingServer is false
- Harmless IPv4 localhost socket/bind/listen passed; Unix-domain socket creation returned EPERM.
- apt-cache reported permission denied on its apt retry configuration; no apt install or retry was attempted

## Subsequent isolated HTTP round

See R14-API-VALIDATION.md for the reusable owner-process runner and actual HTTP evidence. The original 722/718/4 counts above remain the original full verify result; additional Redis 3/3 and HTTP stages are separate runs. Browser acceptance remains pending.

## Subsequent Recipe timestamp fix verification

The R14 creation HTTP round found and fixed RecipeExecution terminal transitions overwriting createdTime. A new clean verify compiled the modified code and passed 728 tests total: 724 passed, 4 skipped, 0 failures/errors. Original branch gates remain 88.69%/86.42%. Six newly added domain cases failed before the fix and passed afterward. The original 722-run evidence above is preserved separately. See R14-CREATION-API-VALIDATION.md for exact current evidence paths and HTTP replay coverage.

## Subsequent batch cache fix verification

The batch-edit cache round adds one post-success invalidation call and six controller cases. New clean verify: 734 total, 730 passed, 4 skipped, 0 failures/errors; 144 XML reports, both original branch gates passed at 88.69%/86.42%. Targeted tests failed in two successful-refresh cases before the fix and passed 6/6 afterward. Previous 722/728 evidence is retained separately. See R14-CACHE-VALIDATION.md.
