# Lottie Assets

All animation cases are committed locally and loaded from app assets or the iOS bundle. Runtime network loading is intentionally not used.

## Provenance

- `heavy_matte_mask.json` is a generated local shape-only `ANIMAX` wordmark stress case for dense track matte and layer mask coverage.
- It does not declare fonts, use text layers, or reference external image assets.
- The active animation contains 10 layer masks and 10 track matte pairs.

License copies for runtime dependencies are stored in `third_party_licenses/`.

## Selection Rules

- Keep the active manifest focused on the single mask/matte stress case.
- Keep the JSON pure vector shape data so Android and iOS exercise the same local loading path.
- Avoid adding external image folders unless both engines have an identical local image-provider path in the harness.
