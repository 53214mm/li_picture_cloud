# Real observation region capture — correction

The user requests real image pixels and allowed text, not geometric placeholders. Previous SAFE_LAYOUT PNGs must never reach a paid vision model. Disable that route first; retain independent text observation, all keys, quotas, archives and database data.

## Authorized implementation

Only GALLERY and PICTURE_DETAIL, up to six currently visible authorized picture IDs. Server rechecks picture:view and immutable picture metadata. It reads the original JPEG/PNG from the configured COS bucket using the existing credential configuration, never a user supplied URL. Unsupported origins/formats, oversized images, failed reads or stale permissions skip vision explicitly without a model charge. WebP displayed by the gallery may use its same-image JPEG/PNG original; WebP-only originals are currently unsupported and disclosed.

A separate observation region contains the real full image and only its server-owned name/category/tags. Reflow is intentional and disclosed; it is not a page or desktop screenshot. No DOM text extraction, inputs, avatars, account/payment content, chat pixels or arbitrary webpage images. Known model keys are removed from allowed text before painting. An actual browser canvas displays and captures that region; model input is the identical server-controlled raster, never arbitrary uploaded client pixels. With visual off: no storage download, PNG render, canvas capture or vision call. Text remains unchanged.

Native ImageIO dimensions are checked before decoding, with subsampling and byte/pixel limits. COS requests have bounded timeouts/concurrency and no retry. Capture caches are short-lived and permission/version-bound. Recheck before paid dispatch and after completion; never reuse expired or revoked context. Existing quotas, route binding, tracing and owner isolation remain.

## Ordered verification and deployment

1. Red/green regression disables legacy paid layout; guarded original source update and restart only native frontend/backend.
2. Tests prove real colors and allowed text, authorization-before-read, no nonwhitelist reads, unsupported images skipping, known-key redaction, off zero capture/model.
3. Implement authorized image source, bounded real raster and explicit browser canvas capture; revise UI/trace/prompt/docs honestly.
4. Fresh read-only independent review; fix findings. Run backend verify, frontend tests/lint, both flag builds and bundle budget.
5. Actual isolated Edge browser fixture: real image colors and permitted text, secret sentinels outside observation region, inputs and nonwhitelisted areas excluded, off zero canvas/provider; all APIs/models mocked, no billing.
6. Export incremental sixth patch from protected fifth baseline, replay all six in memory, verify final tree. Guard original branch/HEAD, all before hashes, private config, data process/container identities; compile, restart related native processes only, read-only health/JMX/schema/quota/browser checks.
7. Save a new complete Library recovery document and verify remote text SHA; retain all five prior archives and Library identities. No commit, PR, main changes, quota reset, database/container rebuild, real model or production access.

## Interfaces

AuthorizedObservationImages.load(owner, pictureRef) validates server record and configured storage key before bounded bytes; RealObservationRegion.capture(owner, snapshot, redaction) returns immutable PNG with source REAL_OBSERVATION_REGION and content digest. Service exposes capability only for the real renderer; legacy rectangle renderer has no provider path. Browser capture helper accepts only bounded PNG data URLs from prepare/preview, draws only that safe raster on its own canvas and exports PNG, never DOM/screenshots of the surrounding page. Tests inspect actual exported pixel buffers.

Official references: https://github.com/tencentyun/cos-java-sdk-v5 ; https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/imageio/ImageReader.html ; https://developer.mozilla.org/en-US/docs/Web/HTML/How_to/CORS_enabled_image ; https://developer.mozilla.org/en-US/docs/Web/API/HTMLCanvasElement/toBlob .
