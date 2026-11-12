# User guide

The [README](../../README.md) is the short version: a client in a few lines. These pages are the detail behind it, for
everything the transport does: configuration, authentication, rate limits and errors. They apply to every library built
on `gitlab-client`. Read them in order, or jump to the one you need.

| Chapter | What it covers |
|---|---|
| 1. [Configuration](configuration.md) | Every client option, flavor and tier, with an example |
| 2. [Authentication in depth](authentication.md) | The token types, rotating tokens, security |
| 3. [Rate limiting in depth](rate-limiting.md) | How a `429` is read, what it holds back, long waits, monitoring |
| 4. [Exception handling](exception-handling.md) | Every exception that reaches your code, what the client handles for you and what you should handle |

The examples send plain calls with `GitLabCaller`. A library built on the client gives you typed methods for the same
calls.
