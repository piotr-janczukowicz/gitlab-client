# Rate limiting in depth

Previous: [Authentication in depth](authentication.md) · Next: [Exception handling](exception-handling.md) · [Guide index](README.md)

GitLab answers a rate-limited request with `429`; see
[user and IP rate limits](https://docs.gitlab.com/administration/settings/user_and_ip_rate_limits/) and
[GitLab.com rate limits](https://docs.gitlab.com/user/gitlab_com/rate_limits/). The client waits and retries for you.

## How a limit is read

- A response is rate-limited when its status is `429`, never because of its text: the body is configurable, and no other
  status is a rate limit, so a `403` is never one.
- `Retry-After`, the seconds until the quota is restored, is the wait that GitLab asks for. When it is missing or not a
  positive number, the retry layer waits its own default and doubles it per limit.
- When `RateLimit-Remaining` is zero, the throttle that the headers describe fired. It holds back every request of the
  credential until `RateLimit-Reset`, a Unix time.
- Otherwise the `429` is an application limit, such as issue creation, that the headers do not describe. It holds back only
  requests with the same method and path, so a limit of a day on creating projects does not stop reads.
- A successful response with `RateLimit-Remaining` zero and a reset still ahead holds the next requests of the credential
  until the reset, as GitLab advises.

## Long waits fail fast

A wait is accepted up to `maxRateLimitWait` of `RetryConfiguration`. A longer wait is not slept: the call throws
`GitLabRateLimitException`. A block is held for at most `maxRateLimitBlock`. Both, the number of attempts
(`maxRateLimitAttempts`), the backoff and the jitter are options of `RetryConfiguration`:

```java
RetryConfiguration retry = RetryConfiguration.builder()
        .maxRateLimitWait(Duration.ofMinutes(2))
        .maxRateLimitAttempts(3)
        .build();
GitLabClientConfiguration configuration = GitLabClientConfiguration.builder().retry(retry).build();
```

## Monitoring

`client.lastRateLimit()` returns the latest `GitLabRateLimitSnapshot` as an `Optional`. To see the quota of every response,
set a `rateLimitListener` on the configuration; `rateLimitEventListener` of `RetryConfiguration` reports the waits.

## Pacing

`mutationInterval` spaces requests that change state, which keeps a bulk update under an application limit before GitLab
has to refuse it. `coordinateRateLimits` of `RetryConfiguration` shares a wait between concurrent calls of the same
credential, so threads do not all hit the limit in turn.
