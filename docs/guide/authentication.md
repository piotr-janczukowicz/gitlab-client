# Authentication in depth

Previous: [Configuration](configuration.md) · Next: [Rate limiting in depth](rate-limiting.md) · [Guide index](README.md)

`GitLabAuthenticationProviders` creates a provider for each token type that GitLab documents for its REST API
([Authentication](https://docs.gitlab.com/api/rest/authentication/)).

| Factory | Token | Sent as |
|---|---|---|
| `personalToken(token)` | Personal, project or group access token | `Authorization: Bearer` |
| `oauth(token)` | OAuth 2.0 access token | `Authorization: Bearer` |
| `jobToken(token)` | `CI_JOB_TOKEN` of a CI/CD job | `JOB-TOKEN` header, no `Authorization` header |
| `rotatingToken(identity, supplier)` | A bearer token that you replace over time | `Authorization: Bearer` |

A job token reaches only the endpoints that the project allows. Deploy tokens cannot be used with the GitLab API, and
GitLab refuses a token sent two ways with a 400, so there is no factory for either.

## Rotating tokens

An OAuth access token lasts two hours, and an access token can be rotated. `rotatingToken(identity, tokens)` asks
`tokens` for the current token on each request.

```java
AuthenticationProvider provider = GitLabAuthenticationProviders.rotatingToken("ci-bot", refresher::currentToken);
```

- The identity names the principal, such as a service account. The rate-limit state and the response cache are kept per
  identity, so they survive each replacement. It must be unique per principal, appears in logs and is never a secret.
- A 401 is not retried; a new token is picked up by the next request.
- An exception that the supplier throws reaches the caller of that request unchanged. A blank token is an
  `IllegalArgumentException`, a `null` token a `NullPointerException`.

## Security

- A token is sent in a header, never in the query string.
- A token does not show in `toString` of a provider, and secrets in response headers are hidden when an exception is
  printed.
- Keep tokens out of source code and logs; the identity of a rotating provider is logged.
