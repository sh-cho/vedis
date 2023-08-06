# Vedis

[![CI](https://github.com/sh-cho/vedis/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/sh-cho/vedis/actions/workflows/ci.yml)

> [!NOTE]
> This project is just for study. It is not intended to be used in production. :)

RESP2-compatible Redis Server implementation in Java

Vedis is built with [netty](https://github.com/netty/netty).

## Supported protocols
- GET
- SET
- DEL
- SHUTDOWN

## References
- ["켠 김에 PR까지 15화: Netty로 Redis 서버를 만들어 볼까?" by Trustin's Hands-on](https://www.youtube.com/watch?v=KNRofx1b8_M)
- [RESP protocol spec](https://redis.io/docs/reference/protocol-spec/)
