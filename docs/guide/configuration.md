# Configuration

Next: [Authentication in depth](authentication.md) · [Guide index](README.md)

`GitLabClientConfiguration.builder()` builds an immutable configuration; `GitLabClientConfiguration.defaults()` targets
gitlab.com. Pass it to `GitLabClients.create(authentication, userAgent, configuration)`.

| Option | What it sets |
|---|---|
| `baseUri` | The instance root, without `/api/v4`, which the client adds. Default `https://gitlab.com`. A sub-path is allowed for a relative URL root (`https://example.com/gitlab`); GitLab does not document that form, it follows what python-gitlab, the Go client and gitlab4j-api do |
| `defaultHeaders` | Headers sent with every request, such as `Sudo`; never `User-Agent`, which is an argument of `create` |
| `transport` | Connection and concurrency limits of the HTTP transport |
| `retry` | How a failed request is retried; see [Rate limiting](rate-limiting.md) |
| `mutationInterval` | The least time between two requests that change state; zero for no pacing |
| `conditionalCacheMaxEntries`, `conditionalCacheMaxBodyBytes`, `conditionalCacheMaxBytes` | Limits of the conditional (ETag) cache |
| `rateLimitListener` | Receives the quota that responses report |
| `callObserver` | Observes every call |
| `allowInsecureHttp` | Allows `http://` for a base URI; off by default |
| `flavor` | `GITLAB_COM`, `DEDICATED` or `SELF_MANAGED`; taken from the host unless set |
| `tier` | `FREE`, `PREMIUM` or `ULTIMATE`, for self-managed and Dedicated only |
| `pagination` | `perPage` and `maxPages` (or `unlimitedPages()`) |

The Javadoc of `GitLabClientConfiguration` has the defaults and limits of each option.

## Flavor and tier

An operation that needs a tier carries `@RequiresTier`; a library built on the client calls `caller.requireTier(...)`
first, so a call the configured tier lacks fails with `GitLabTierException` before any request is sent. A tier cannot be
set for gitlab.com: a subscription belongs to a top-level group, so one token can reach groups of different tiers.

## Example

```java
GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
        .baseUri(URI.create("https://gitlab.example.com"))
        .tier(GitLabTier.ULTIMATE)
        .pagination(PaginationConfiguration.builder().perPage(100).maxPages(20).build())
        .build();

try (GitLabClient client = GitLabClients.create(
        GitLabAuthenticationProviders.personalToken(token), "my-app/1.0", configuration)) {
    GitLabCaller caller = GitLabCaller.of(client);
}
```
