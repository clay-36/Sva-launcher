# AccessibilityService disclosure and resubmission

Sva Launcher is a launcher, not a disability accessibility tool. Declare the service for app functionality; do not claim `isAccessibilityTool` eligibility.

## In-app behavior

On first launch (including the first launch after upgrading to this disclosure), a separate disclosure explains the optional Home override, window-change events and package names, local processing, and absence of event storage or sharing. No thanks and Back continue without consent. Agree and open settings records consent and opens Android Accessibility settings. Enabling the service remains a separate Android action. Settings → Home override → Open Accessibility settings shows the disclosure again. Declining it revokes app consent; the service ignores events until consent is granted again. Users can disable the service in Android settings.

The service is restricted to window-state events from the four supported built-in TV launcher packages. Window-content retrieval is disabled. Consent is excluded from exported launcher backups.

## Suggested Play listing disclosure

Sva Launcher uses the AccessibilityService API for its optional Home override feature. With your consent, it detects when your TV opens its built-in launcher and automatically opens Sva Launcher instead. It accesses window-change events and launcher package names only on your TV. It does not read screen contents or typed text, store this event data, or send or share it. You can use Sva Launcher without this feature and disable the service at any time in Android Accessibility settings.

## Resubmission steps

1. Build and upload a new release with a higher version code than the rejected submission.
2. Update the store listing with the disclosure above and verify the privacy policy and Data safety answers match the release.
3. Complete/update the AccessibilityService declaration in Play Console: app functionality; no collection off device or sharing of accessibility event data. Describe the deterministic trigger (supported built-in launcher opens) and action (open Sva Launcher).
4. Record the actual release on a TV/emulator: launch the app; show the full disclosure (scroll slowly if needed); choose No thanks and show the launcher remains usable; reopen the disclosure through Home override; choose Agree and open settings; enable Sva Launcher in Android Accessibility settings; press Home to demonstrate the override. Include captions if the behavior is unclear. Also show disabling the service.
5. Supply an accessible video URL in the declaration, tell reviewers the exact disclosure path, and submit the release for review.

Google Play approval remains subject to review of the submitted artifact and declaration.

References: https://support.google.com/googleplay/android-developer/answer/10964491 and https://support.google.com/googleplay/android-developer/answer/11150561

## Verification in this checkout

- Built the app and instrumentation APKs with a temporary Gradle init script excluding the pre-existing unfinished music sources/tests. The normal build currently fails because those sources reference missing Media3 session and music-provider dependencies.
- Installed verification builds under a separate application ID, preserving the existing emulator installation.
- Passed five instrumentation tests covering affirmative consent, cancellation, consent withdrawal, and accessibility-settings fallback behavior.
- Visually verified the first-launch disclosure at 1280×720: all disclosure text and both buttons fit; No thanks receives remote focus. New disclosure strings currently use the English fallback for other app languages.
- No release was uploaded or submitted to Google Play.
