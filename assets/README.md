# Lottie Assets

All animation cases are committed locally and loaded from app assets or the iOS bundle. Runtime network loading is intentionally not used.

## Provenance

- `heavy_matte_mask.json` is a generated local shape-only animated `ANIMAX` wordmark stress workload for dense track matte and layer mask coverage.
- It does not declare fonts, use text layers, or reference external image assets.
- The active animation contains 15 animated ordinary add masks and 15 animated alpha track matte pairs.
- The JSON intentionally avoids luma mattes, inverted mattes, inverted masks, and non-add mask modes so Android Lottie and lottie-ios consume the same mask/matte feature subset as AnimaX.

License copies for runtime dependencies are stored in `third_party_licenses/`.

## Selection Rules

- Keep the active manifest focused on the single mask/matte stress workload.
- Keep the JSON pure vector shape data so Android and iOS exercise the same local loading path.
- Keep matte usage alpha-only unless the benchmark is explicitly testing features unsupported by one of the comparison engines.
- Avoid adding external image folders unless both engines have an identical local image-provider path in the harness.
