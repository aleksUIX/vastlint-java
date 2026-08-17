# Security Policy

vastlint-java is a blocking gRPC client for `vastlint-grpc`. It does not parse
VAST XML itself. Untrusted tags are sent to the server as opaque document
strings.

## Supported versions

Only the latest released version receives vulnerability fixes.

## Reporting a vulnerability

Do not open a public GitHub issue for security vulnerabilities.

Report privately via
[GitHub Security Advisories](https://github.com/aleksUIX/vastlint-java/security/advisories/new)
or email aleks@vastlint.org with details and a reproducing payload if you have
one.

You will receive a response within 48 hours acknowledging the report. We aim
to disclose a fix within 7 days for critical issues and 30 days for others.
We follow coordinated vulnerability disclosure.

A vulnerability here means anything that lets a caller of this client be
exploited through crafted RPC responses, channel configuration, or
dependency issues in the generated stubs. Parser crashes on malformed VAST
belong in [aleksUIX/vastlint](https://github.com/aleksUIX/vastlint/security/advisories/new).
