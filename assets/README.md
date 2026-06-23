# Lottie Assets

All animation cases are committed locally and loaded from app assets or the iOS bundle. Runtime network loading is intentionally not used.

## Provenance

The assets in `lotties/` were copied from official Airbnb Lottie repositories, both Apache-2.0 licensed:

- `hamburger_arrow.json`, `lottie_logo_2.json`, `twitter_heart.json`, `pin_jump.json`, `base64_image.json`, `text_animated_properties.json`, `shadow_effect_animated.json`, and `motion_corpse.json` come from `airbnb/lottie-ios` at commit `906e79b0648c16f02ad5844e345481ae05a94afe`.
- `nine_squares_al_boardman.json`, `boat_loader.json`, `icon_transitions.json`, `switch.json`, `watermelon.json`, and `success.json` come from `airbnb/lottie-ios` at commit `906e79b0648c16f02ad5844e345481ae05a94afe`.
- `track_mattes.json`, `gradient_fill_blur.json`, `shape_types.json`, `repeater.json`, `trim_paths.json`, and `laugh4.json` come from `airbnb/lottie-android` at commit `05ea92e90381eb8a8ae06855ea2b74f322bebbec`.

License copies are stored in `third_party_licenses/`.

## Selection Rules

- Prefer pure JSON or embedded-base64 assets so Android and iOS exercise the same local loading path.
- Keep a mix of small, complex, text, matte, gradient, image, effect, and large-canvas cases.
- Keep at least 20 unique JSON files so the Android x20 scene can render without repeating an asset.
- Avoid visually redundant variants in the active manifest, even when their JSON files differ.
- Avoid cases that require external image folders until both engines have an identical local image-provider path in the harness.
