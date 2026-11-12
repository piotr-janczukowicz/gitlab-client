# gitlab-client

Java 21 transport for the GitLab REST API: an authenticated, retrying, rate-limit aware HTTP client for gitlab.com,
GitLab Dedicated and self-managed GitLab, with GitLab's error mapping, tier guard and pagination built in.

![Java 21](https://img.shields.io/badge/Java-21-orange.svg)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Build](https://github.com/piotr-janczukowicz/gitlab-client/actions/workflows/main.yml/badge.svg)](https://github.com/piotr-janczukowicz/gitlab-client/actions/workflows/main.yml)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/a3ea4adf4ac04c56b3bd8e9557c62d7c)](https://app.codacy.com/gh/piotr-janczukowicz/gitlab-client/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

## Why this library

- **Authentication.** Personal, project and group access tokens, OAuth 2.0 tokens, CI job tokens, and rotating tokens for
  a client that outlives one token.
- **Retries and rate limits.** A `429` is waited out from `Retry-After` and the `RateLimit-*` headers, and the latest
  quota is available from the client. A failure to connect is retried; a `POST` is not repeated after the request may
  have reached GitLab.
- **Errors.** A failed response becomes a typed exception (`GitLabNotFoundException`, `GitLabRateLimitException`,
  `GitLabValidationException`, ...) that carries GitLab's message and validation errors.
- **Pagination.** Offset and keyset pages are followed lazily as a `GitLabPage`, with a page limit and a guard against a
  `Link` that leaves the API.
- **Self-managed GitLab.** The flavor comes from the host, a sub-path for a relative URL root is supported, and a call can
  be rejected before it is sent when the configured tier lacks the operation.
- **JSON.** A codec for GitLab's JSON (snake_case, unknown properties ignored, immutable collections). A library on the
  path can add the Jackson modules of its own models by providing a `GitLabJsonModule` through the `ServiceLoader`.

## Install

**Requirements:** Java 21. The library is a Java module, `io.github.piotr_janczukowicz.gitlabclient`, and also works on
the class path.

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

```java
try (GitLabClient client = GitLabClients.create(
        GitLabAuthenticationProviders.personalToken(token), "my-app/1.0")) {
    GitLabCaller caller = GitLabCaller.of(client);
}
```

`GitLabCaller` sends a call and decodes the answer: `fetch` returns the decoded body, `fetchOrEmpty` returns an empty
`Optional` on a 404, and `list` returns a `GitLabPage`. A library that models GitLab's operations builds its calls and
hands them to the caller.

## Authentication

```java
// a personal, project or group access token, sent as Authorization: Bearer
GitLabAuthenticationProviders.personalToken(token);

// an OAuth 2.0 token, which lasts two hours: use a rotating token for a long-lived client
GitLabAuthenticationProviders.oauth(token);

// the token of a CI/CD job, sent as the JOB-TOKEN header
GitLabAuthenticationProviders.jobToken(jobToken);

// a token that you replace over time; the identity keeps the rate-limit state and the cache
GitLabAuthenticationProviders.rotatingToken("ci-bot", tokens::current);
```

A token is never put in the query string and never shows in `toString`. Deploy tokens cannot be used with the GitLab API,
so there is no provider for them.

## Configuration

`GitLabClients.create(authentication, userAgent, configuration)` takes a `GitLabClientConfiguration`, which wraps the HTTP
settings (timeouts, retries), the pagination limits (`perPage`, `maxPages`) and the instance: the base URI, the flavor and
the tier. Without a configuration the client talks to `https://gitlab.com`.

```java
GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
        .baseUri(URI.create("https://gitlab.example.com"))
        .tier(GitLabTier.PREMIUM)
        .build();
```

## User guide

The [user guide](docs/guide/README.md) has a chapter for each topic below.

| Topic | In short |
|---|---|
| [Configuration](docs/guide/configuration.md) | Every option, and what it means on gitlab.com, Dedicated and self-managed |
| [Authentication in depth](docs/guide/authentication.md) | The token types, rotating tokens, security notes |
| [Rate limiting](docs/guide/rate-limiting.md) | Waited out for you; which `429` holds back what; a wait over `maxRateLimitWait` throws `GitLabRateLimitException` |
| [Exception handling](docs/guide/exception-handling.md) | HTTP errors are unchecked `GitLabApiException` subclasses; what the client handles and what you should |

## Disclaimer

This library is the transport of the other libraries in this family, and it is mainly tested against mock servers. It is
new and not yet proven in production. It is not affiliated with, endorsed by or sponsored by GitLab Inc.; GitLab is a
trademark of its owner.

**Verify the behavior of any call you depend on before using it in production.**

## License

[MIT](LICENSE)
