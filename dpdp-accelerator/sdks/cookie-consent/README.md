# Cookie consent

Cookie banners whose choices are stored as DPDP consents in WSO2 Identity Server. A website's own
backend runs an SDK; the browser talks only to that backend, and the backend calls IS with the
site's M2M application. See `docs/content/cookie-consent-guide.md` for the feature itself.

| Folder | What it holds |
|---|---|
| `browser/` | The loader that runs in the visitor's browser. Shared: every SDK serves these same files. |
| `spec/` | The contract every SDK implements: the browser API, the visitor cookie, the shape of the consents in IS, and a script that checks IS behaves as the SDKs expect. |
| `node/` | The SDK for Node.js and Express, `@wso2-dpdp/cookie-consent`. The reference implementation. |

An SDK for another backend language goes beside `node/`, serves the files in `browser/` and the
banner library at the version `spec/` names, and follows `spec/README.md`.

```sh
cd browser && npm test   # loader tests
cd node && npm test      # SDK tests
```
