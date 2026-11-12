# gitlab-client

Java 21 transport for the GitLab REST API: an authenticated, retrying, rate-limit aware HTTP client for gitlab.com and
self-managed GitLab, with GitLab's error mapping, tier guard and pagination built in.

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Java 21](https://img.shields.io/badge/Java-21-orange.svg)

## Why this library

- **Authentication.** Personal, project and group access tokens and OAuth bearer tokens (`personalToken`, `oauth`), job tokens and rotating tokens
  (`GitLabAuthenticationProviders`).
- **Retries and rate limits.** Rate limit headers are tracked per resource and waited out; a failure to connect is
  retried.
- **Errors.** A failed response becomes a typed exception (`GitLabNotFoundException`, `GitLabRateLimitException`,
  `GitLabValidationException`, `GitLabTierException`, ...) that carries GitLab's message.
- **Tier guard.** `@RequiresTier` and `GitLabCaller.requireTier` reject a call before it is sent when the configured
  tier or flavor lacks the operation.
- **Pagination.** Offset and keyset pages (`Link` headers) are followed lazily as a `GitLabPage`, with a page limit and a
  guard against a `Link` that leaves the API.
- **JSON.** A codec for GitLab's JSON (snake_case, unknown properties ignored, immutable collections). A library on the
  path can add the Jackson modules of its own models by providing a `GitLabJsonModule` through the `ServiceLoader`.

## Install

The library is a Java module, `io.github.piotr_janczukowicz.gitlabclient`, and also works on the class path.

```xml
<dependency>
    <groupId>io.github.piotr-janczukowicz</groupId>
    <artifactId>gitlab-client</artifactId>
    <version>LATEST_VERSION</version>
</dependency>
```

Replace `LATEST_VERSION` with the newest entry on the
[releases page](https://github.com/piotr-janczukowicz/gitlab-client/releases).

## Quick start

The client sends requests to `/api/v4` of an instance root (`GitLabClientConfiguration.baseUri`, default
`https://gitlab.com`). Close it when done.

```java
try (GitLabClient client = GitLabClients.create(
        GitLabAuthenticationProviders.personalToken(token), "my-app/1.0")) {
    GitLabCaller caller = GitLabCaller.of(client);
    // resource APIs extend GitLabResourceApi and send through the caller
}
```

The typed operations of the REST API are in
[gitlab-api-internal](https://github.com/piotr-janczukowicz/gitlab-api-internal), which is built on this library.

## Disclaimer

Not affiliated with, endorsed by or sponsored by GitLab Inc. GitLab is a trademark of its owner.

## License

MIT, see [LICENSE](LICENSE).
