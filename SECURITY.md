# Security reporting

Security fixes are maintained on the default branch. Use the latest reviewed
release; older versions do not have a separate backport commitment.

Use the repository's **Security → Report a vulnerability** option when private
reporting is enabled. If it is unavailable, open an issue requesting a private
contact method, without exploit details or sensitive data. Maintainers should
enable private vulnerability reporting before the first public release.

Useful reports include affected versions, reproduction steps, impact and a minimal
sanitized example. Never send signing keys, full backup files, paid-media URLs,
passwords or personal images in a public issue. No response-time SLA is promised.

## App trust boundaries

Sva processes user-selected playlists, guide feeds, public albums, calendar feeds,
local files and game ROMs. Use sources you trust. The native emulator and Android's
media/image decoders also process this content; input bounds reduce resource abuse
but do not establish that hostile files are safe.

HTTP support is intentional for IPTV and LAN sources. Prefer HTTPS. JSON exports
are unencrypted and include sensitive configuration. System cloud/device-transfer
backup is excluded; user-requested exports are still available.

All-files access, notification access and the launcher accessibility override are
optional. Permissions are explained in the README. Sva does not include analytics
or upload the library/configuration to a project-operated service.

## Repository hygiene

Run `python3 scripts/audit-source.py --history` before first publication, and use
an independent secret scanner as part of maintainer review. The included checker
only recognizes high-confidence key patterns, URL credentials and private build
artifacts. Rotate any credential that has been exposed; deleting its current file
is not enough to remove it from Git history.
