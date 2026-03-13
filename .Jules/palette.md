## 2026-03-13 - Accessibility of External Links in Jetpack Compose
**Learning:** In Jetpack Compose, using `Modifier.clickable` for opening external links (like Intents) without explicitly setting a role or click label leaves screen readers without context about the action. The user only hears 'clickable', which is not descriptive.
**Action:** Always include `role = Role.Button` and a descriptive `onClickLabel` (e.g., 'Open link in browser') when using `Modifier.clickable` for actions that navigate outside the app or open web links to ensure proper screen reader announcements.
