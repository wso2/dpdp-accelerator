# Localizing the Consent Portal

The portal ships 23 languages out of the box (`frontend/public/i18n/`, listed
in `meta.json`). There are two independent kinds of translatable content, and
they're kept in separate files for a reason - read [How catalog.json
works](#how-catalogjson-works) before touching it.

## The two kinds of content

| | Static UI text | Dynamic catalog text |
|---|---|---|
| File | `public/i18n/<lang>/common.json` | `public/i18n/<lang>/catalog.json` |
| What it holds | Labels, buttons, messages — text built into the app | Names/descriptions of purposes and elements, created at runtime by admins |
| Who edits it | Translators, by hand | A sync script + translators |
| When it changes | When a developer adds a new UI string | Whenever an admin creates/renames a purpose or element |

## How `catalog.json` works

Purposes and elements aren't known at build time - an admin can create a new
one (e.g. "Finance Info") from the portal UI at any moment, stored in the
Consent Server's database, not in any file in this repo. So the list of
*things needing translation* can only be discovered by asking the server.

That's what `frontend/scripts/sync-catalog-i18n.mjs` does:

- `en/catalog.json` is **regenerated every run** from the server's current
  purposes/elements - it's a report of what exists, not something anyone
  hand-edits. English text comes from whatever the admin typed when they
  created the item.
- Every other `<lang>/catalog.json` gets the same set of keys, but **existing
  translations are never touched or removed**, and new keys are added with
  blank `displayName`/`description` for a translator to fill in.
- Keys for purposes/elements later deleted or renamed on the server are kept,
  never dropped — consents already granted still reference that exact
  name/version, so its translation must stay available.

### Running the sync

The script needs the current purpose/element list, either from a live BFF
call or a JSON dump (useful when you don't have a bearer token handy, since
the portal's auth is split-cookie based and awkward for a plain script):

```bash
# Live, if you have a portal bearer token:
CATALOG_API_BASE=https://<host>:9443/consent-portal CATALOG_API_TOKEN=<token> pnpm i18n:catalog

# Or from a dump fetched directly from the Consent Server (no portal auth needed):
curl "http://<consent-server>:3000/api/v1/consent-purposes?limit=100" -H "org-id: <org-id>" > /tmp/purposes.json
curl "http://<consent-server>:3000/api/v1/consent-elements?limit=100" -H "org-id: <org-id>" > /tmp/elements.json
# combine into {"purposes": [...], "elements": [...]} as catalog-dump.json, then:
pnpm i18n:catalog --input catalog-dump.json

# Report what's missing without writing anything:
pnpm i18n:catalog --check
```

Run `pnpm lint:fix` afterwards to format the files it wrote.

## Translating with Gemini / Sarvam AI

Either tool works for either language — there's no fixed split; use
whichever is convenient for a given batch. Both the web UI (copy-paste) and
API access are fine.

### Workflow

1. Run the sync script (above) so `en/catalog.json` reflects the current
   catalog and every language has the new keys (blank where untranslated).
2. For **`common.json`** (static UI text): diff the target language's file
   against `en/common.json` to find missing/blank keys. Paste the English
   value with its key path for context (e.g. `sidebar.nominations`) into
   Gemini or Sarvam AI, ask for the target-language translation, and write it
   back at the same key path. Keep the JSON structure identical — only values
   change, never keys.
3. For **`catalog.json`** (purpose/element names and descriptions): the sync
   script already lists exactly what's missing per language (`pnpm
   i18n:catalog --check` prints an X/Y count per language). For each blank
   entry, translate the paired `displayName`/`description` from the matching
   entry in `en/catalog.json`, using the item's `name` as context so the
   translation fits the actual meaning (e.g. `account_information_access` →
   "Account information access").
4. Paste translated values back into the file at the same key. A
   version-suffixed key (`name@version`) only exists when a purpose/element
   was reworded in a later version — translate it separately so an older
   version doesn't get the newer wording.
5. Run `pnpm lint:fix`, then `pnpm i18n:catalog --check` again to confirm the
   language now shows "complete", and spot-check a few strings in the running
   app with that language selected.

### What to send the translation tool

Keep the prompt narrow — one key (or a small batch) at a time, with enough
context to translate correctly, not the whole file:

```text
Translate to Hindi. Keep it natural for a consent-management UI, not literal.

Key: sidebar.nominations
English: "Nominations"
```

For catalog entries, include the purpose/element's internal `name` alongside
the English `displayName`/`description`, since the name often disambiguates
intent that the short display text alone doesn't carry.

### Review

Machine translation from either tool is a first draft, not a merge-ready
translation — DPDP consent language is legally meaningful, so anything
touching purpose/element wording (what a person is actually consenting to)
should get a native-speaker or legal review pass before shipping, even if the
UI-chrome strings (buttons, labels) don't strictly need one.


