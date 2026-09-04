# Event payload rules

Rules are keyed by topic name. The tool resolves the name through a join between `EVENT` and `TOPIC` constrained by both `TOPIC_ID` and `ORG_ID`.

Paths use RFC 6901 JSON Pointer escaping, plus one restricted extension: `*` iterates array elements. For example:

```json
{
  "topic": "example.topic",
  "paths": ["/userId", "/data/authorizations/*/userId"]
}
```

Only textual scalar values exactly matching a trusted identity are replaced. A configured path resolving to a non-string value fails the run.
Topics without rules are counted as unconfigured and are not inspected recursively.
