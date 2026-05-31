# lsmkv — Build Your Own Key-Value Storage Engine

An incremental, LSM-tree based key-value storage engine exposed over REST, following
The Coder Cafe's *"Build Your Own Key-Value Storage Engine"* series
(<https://read.thecoder.cafe/p/build-your-own-kv-engine-1>).

Each week of the series is implemented as a **separate commit**, building on the previous
one. This is distinct from the sibling `keyvaluestorage` package, which is a Bitcask-style
(append-log + in-memory hash index) store.

## Roadmap

| Week | Topic | Status |
|------|-------|--------|
| 1 | In-memory store (REST PUT/GET) | ✅ |
| 2 | LSM foundations (memtable, JSON SSTables, MANIFEST) | ✅ |
| 3 | Durability (WAL, fsync, crash recovery) | ✅ |
| 4 | Deletes, tombstones, compaction | ✅ |
| 5 | Range scans (SCAN) | ✅ |
| 6 | Leveled compaction | ✅ |
| 7 | Block-based SSTables, sparse index, Bloom filters, trie memtable | ✅ |
| 8 | Concurrency | ✅ |

## API

- `PUT /{key}` — body is the value; creates or updates; returns `200 OK`.
- `GET /{key}` — returns `200` + value, or `404` if absent.
- `DELETE /{key}` — deletes the key (writes a tombstone); returns `200 OK`.
- `GET /scan?start=&end=` — returns live key-value pairs in `[start, end]` as a sorted JSON object.

Keys are lowercase ASCII; values are ASCII.

## Running

```bash
mvn spring-boot:run
# then, in another shell:
curl -X PUT localhost:8080/foo -d 'bar'        # -> OK
curl localhost:8080/foo                        # -> bar
curl -i localhost:8080/missing                 # -> 404
curl -X DELETE localhost:8080/foo              # -> OK
curl 'localhost:8080/scan?start=a&end=z'       # -> {"...":"..."}
```

## Configuration

Set via system properties or `application.properties`:

| Property | Default | Meaning |
|----------|---------|---------|
| `lsmkv.data-dir` | `lsmkv-data` | directory for SSTables, MANIFEST, and WAL |
| `lsmkv.memtable-max-entries` | `1024` | flush threshold; also the max entries per level-1+ SSTable |
| `lsmkv.l0-compaction-trigger` | `4` | level-0 SSTable count that triggers compaction |
| `lsmkv.level-fanout` | `10` | per-level size budget multiplier |
