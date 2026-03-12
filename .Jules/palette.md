
## 2024-03-12 - Explicit Semantics Roles on Custom Clickables
**Learning:** Found that custom clickable areas (like `Row` components with `Modifier.clickable`) that perform actions like opening external links don't inherently announce their interactability role to screen readers in Jetpack Compose, causing an accessibility gap.
**Action:** Always provide `role = Role.Button` within the `clickable` modifier parameters for components acting as buttons or links to ensure proper screen reader context.
