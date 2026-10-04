# PRD-10: Aurora chat workspace

## User experience

- Match the Android light/dark setting. Use a restrained teal/lavender aurora background with navy-blue primary actions.
- Use the supplied `aircall_ai_icon.jpg` as the launcher and drawer/empty-chat artwork.
- A fresh launcher entry creates a new chat. Configuration changes, OAuth returns and notification deep links preserve the current conversation. An active voice session stays attached to its current room.
- Swipe right or tap Menu to open the drawer. Tap a room title to restore its transcript; Manage offers rename and confirmed deletion. New rooms derive their initial title from the first user message; a manually edited title is retained.
- Switching rooms cancels the current text/voice turn and clears pending tool approvals before restoring the selected transcript. Late replies from a previous room cannot append to another room.
- Chat titles and transcripts are encrypted using Android Keystore and saved atomically in app-private storage. Failure to read an existing store must not replace it with an empty store.

## Settings

1. AI and models: local/cloud selection, local model management, cloud endpoint/model/API key.
2. Tools and accounts: GitHub login, Google login for Gmail and Calendar; local notes need no login. No manual service-token or OAuth-client-ID inputs.
3. Display and notifications: system theme behavior and a link to Android notification settings.
4. Tool approvals: inspect and revoke persisted action approvals.
5. Privacy: actual data flows and local chat persistence.

Cloud inference API keys remain separate from service login: these providers do not share GitHub/Google tool authorization.

## Google Calendar setup

Calendar now uses Google's primary calendar API rather than the device Calendar Provider. Existing Google users reconnect once to grant `https://www.googleapis.com/auth/calendar.events`, in addition to the existing Gmail scopes. Enable Google Calendar API in the OAuth project's Google Cloud configuration. Google consent/testing restrictions still apply. This repository cannot change that external console configuration.

The Android AuthorizationClient returns short-lived access tokens. The app requests authorization again when required; it does not claim to possess a refresh token.

References:
- https://developers.google.com/workspace/calendar/api/auth
- https://developers.google.com/workspace/calendar/api/v3/reference/events/list
- https://developers.google.com/workspace/calendar/api/v3/reference/events/insert

## Background work and notification actions

| Notification | Action | Expected behavior |
| --- | --- | --- |
| Voice | Pause | Immediately cancel the current recognition/generation/playback turn and show Paused |
| Voice | Resume | Wait for cancelled-turn cleanup, then start exactly one new loop |
| Voice | Mute output | Stop any current TTS playback; future TTS is suppressed while recognition remains active |
| Voice | End | Stop the loop and TTS, remove the notification |
| Voice | Tap body | Return to the voice screen without creating a new room |
| Download | Cancel | Cancel the model job and interrupt its active connection; remove partial data |
| Download | Tap body | Open the model screen, including after a completed download |

Downloads use a user-started `dataSync` foreground service with progress updates, per-model action identities and a bounded partial wake lock. Leaving the screen or removing the Activity from Recents does not stop the service. Final files are published only after size and SHA-256 verification. Force-stopping/killing the process is not resumable; a fresh user request restarts the download. Android notification permission is needed to display notification controls.

Reference: https://developer.android.com/develop/background-work/services/fgs/service-types

## Verification

JVM regression coverage: encrypted room serialization, rename/delete/history isolation, late reply isolation, immediate pause/resume cleanup, exact Google Calendar payload timestamps; existing tool/voice/download integrity tests continue to run.

Physical-device acceptance checks (not substituted by JVM tests):
- Open from launcher, rotate, switch apps and return via each notification; check room and route behavior.
- Switch light/dark modes; inspect drawer, long titles, keyboard, small screen and large font layouts.
- Log in through GitHub and Google, reconnect Google with Calendar permission, then read/create an event with approval.
- Start a download, lock the screen, reopen, cancel from the notification, download again, verify completion and apply.
- Exercise every voice action during STT, inference and TTS, and confirm ending removes the notification.

## Readability restoration

The readability branch originally diverged from pre-Aurora main, so its APK did not include the accepted workspace features. Restore the verified Aurora tree first; typography changes must build on that tree.

All Compose screens use shared text styles: body 16–17sp with 26–28sp line height, section titles 19sp, and 16sp button labels. Text respects Android font scaling. Explanatory text uses bodyMedium rather than tiny captions. Light/dark palettes have explicit text pairs verified at a minimum 4.5:1 contrast for normal text. Settings and model action rows wrap; model name/status stack; long approval content scrolls; drawer room titles wrap. The overlay also uses 16sp system-scaled text with explicit light/dark contrast.
