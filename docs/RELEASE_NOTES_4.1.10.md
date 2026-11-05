# Sva Launcher 4.1.10

Version code: 15.

- Added a separate AccessibilityService disclosure on first launch and before opening accessibility settings for the optional Home override feature. It explains the events and launcher package names accessed, local processing, and how to decline or disable the feature.
- Home override now requires affirmative in-app consent, including for previously enabled services. Declining withdraws consent without blocking normal launcher use.
- Limited accessibility events to supported built-in TV launcher packages. Screen-content retrieval remains disabled. Consent is excluded from exported/imported settings backups.
- Translated the disclosure and consent buttons across all 23 supported interface languages.
- Fixed disclosure readability in light mode and verified consent-button layout and default remote focus in both app themes.

## Upgrade and Google Play review

Users who already enabled the service must accept the new disclosure before Home override resumes. Enabling or disabling the Android service remains a separate action in Android Accessibility settings.

A Google Play submission also requires an updated AccessibilityService declaration, accurate store listing disclosure and a video demonstrating both consent choices and the feature. See [review instructions](ACCESSIBILITY_PLAY_REVIEW.md) and [verification results](PREPRODUCTION_ACCESSIBILITY_REVIEW.md).
