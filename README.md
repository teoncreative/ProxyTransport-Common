# ProxyTransport-Common

Host-agnostic downstream implementation of the [ProxyTransport](https://github.com/teoncreative/ProxyTransport)
protocol, built on CloudburstMC/Protocol.

ProxyTransport replaces RakNet between a WaterdogPE proxy and its downstream servers with raw TCP (and QUIC).
A frame on the wire is:

```
[4-byte big-endian length][1-byte compression type][compressed Bedrock batch]
```

There is no `0xFE` game-packet byte. The compression byte uses the vanilla Bedrock values plus `-2` (`254`) for
Zstd, which ProxyTransport adds.

This library provides the codecs, the per-connection pipeline and the listeners. It is consumed as a **git
submodule** by each host integration:

- `ProxyTransport-Geyser` - Geyser extension

The single seam is `PeerFactory`: each host supplies its own `BedrockPeer` (hosts have differing peer base
classes), and applies the shared behaviour via `ProxyTransportPeerSupport`.
