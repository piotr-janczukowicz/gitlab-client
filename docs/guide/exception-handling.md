# Exception handling

Previous: [Rate limiting in depth](rate-limiting.md) · [Guide index](README.md)

## What reaches your code

| Exception | When | Type |
|---|---|---|
| `GitLabUnauthorizedException` | 401: the token is missing, expired, revoked or malformed | unchecked |
| `GitLabForbiddenException` | 403: missing permission or blocked access (never a rate limit) | unchecked |
| `GitLabNotFoundException` | 404: the resource is missing, or not visible to this credential | unchecked |
| `GitLabValidationException` | 400 with a field map, or 422: the payload was rejected; `errors()` lists the fields | unchecked |
| `GitLabRateLimitException` | a rate limit the client did not wait out (see [Rate limiting](rate-limiting.md#long-waits-fail-fast)) | unchecked |
| `GitLabApiException` | any other unsuccessful status, such as 409 or 5xx after the retries | unchecked |
| `GitLabTierException` | the configured tier lacks the operation; thrown before any request; `operation()`, `required()`, `configured()` | unchecked |
| `IOException` | the transport failed: connection, timeout, TLS | checked |

The type depends on the status only. Every `GitLabApiException` carries `statusCode()`, `rawBody()`, `headers()`,
`method()` and `uri()`.

## What the client handles for you

- **Rate limits** are waited out within `maxRateLimitWait`; see [Rate limiting](rate-limiting.md).
- **Transient failures** are retried: I/O failures and the retryable `5xx` statuses of `RetryConfiguration`. A `POST`,
  `PATCH`, `PUT` or `DELETE` that fails after the request was sent is not repeated, because the change may have been
  applied.
- **A 304** is handled by the conditional cache; you do not see it.

## What you should handle

- **Not found.** `GitLabNotFoundException` does not prove the resource is missing: GitLab answers 404 for a private
  resource too. For a lookup of one resource, use `caller.fetchOrEmpty(...)`, which turns a 404 into an empty result.
- **Validation.** Read `errors()` of `GitLabValidationException` and fix the payload; retrying does not help.
- **Rate limits that outlast the wait.** Catch `GitLabRateLimitException` and decide whether to schedule the call later.
- **Tier.** `GitLabTierException` means the call cannot work on this instance; do not retry.
