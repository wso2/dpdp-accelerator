---
title: Localizing the Consent Portal
sidebar_position: 4
---

# Localizing the Consent Portal

Complete this after [`configuration-guide.md`](configuration-guide.md). This
covers correcting or adding translated text on a running deployment — no
rebuild of the accelerator is needed for anything described here, unless
stated otherwise.

The portal ships with English plus the 22 languages listed in the Eighth
Schedule to the Constitution of India, per section 5(3) of the DPDP Act.
Readers switch between them from the language selector in the portal header;
nothing needs to be configured on the server for that to work.

## 1. Where the translation files live

After running `merge.sh` (see `setup-guide.md`), each language's files are at:

```text
<IS_HOME>/repository/deployment/server/webapps/consent-portal/i18n/<lang-code>/
├── common.json    # the portal's own UI text (buttons, labels, messages)
└── catalog.json   # wording for Purposes and Elements created in this deployment
```

Use this table to map the languages.

| Code | Language | Native name |
|------|----------|-------------|
| `en` | English | English |
| `hi` | Hindi | हिन्दी |
| `as` | Assamese | অসমীয়া |
| `bn` | Bengali | বাংলা |
| `brx` | Bodo | बड़ो |
| `doi` | Dogri | डोगरी |
| `gu` | Gujarati | ગુજરાતી |
| `kn` | Kannada | ಕನ್ನಡ |
| `ks` | Kashmiri | کٲشُر |
| `kok` | Konkani | कोंकणी |
| `mai` | Maithili | मैथिली |
| `ml` | Malayalam | മലയാളം |
| `mni` | Manipuri (Meitei) | মৈতৈলোন্ |
| `mr` | Marathi | मराठी |
| `ne` | Nepali | नेपाली |
| `or` | Odia | ଓଡ଼ିଆ |
| `pa` | Punjabi | ਪੰਜਾਬੀ |
| `sa` | Sanskrit | संस्कृतम् |
| `sat` | Santali | ᱥᱟᱱᱛᱟᱲᱤ |
| `sd` | Sindhi | سنڌي |
| `ta` | Tamil | தமிழ் |
| `te` | Telugu | తెలుగు |
| `ur` | Urdu | اردو |

This is the same list the portal's language switcher uses
(`public/i18n/meta.json`)

These are plain JSON files served directly by the Identity Server — editing
one in place takes effect immediately, with no server restart. If a browser
already has the page open, ask the reader to refresh so it picks up the
change.

## 2. Correcting the portal's own UI text (`common.json`)

Use this for fixing a typo or improving the wording of the portal's built-in
labels, buttons, and messages — not for anything related to a specific
Purpose or Element (see [section 3](#3-localizing-a-purpose-or-element-catalogjson)
for that).

1. Open `<IS_HOME>/repository/deployment/server/webapps/consent-portal/i18n/<lang-code>/common.json`
   for the language you're correcting.
2. Find the key and edit its value. Keep the key name and JSON structure
   exactly as they are — only change the text between the quotes.
3. Save. No restart needed.

**Adding a brand-new piece of UI text** (one that doesn't exist in any
language yet) is not an operator task — it requires a code change and a
rebuild of the accelerator, since something has to be built to display it.
Raise that with the engineering team.

## 3. Localizing a Purpose or Element (`catalog.json`)

Purposes and Elements are created at run time by administrators through the
portal, so their wording isn't part of the accelerator build — each
language's `catalog.json` starts out empty and is filled in as items get
translated:

```json
{
  "purposes": {},
  "elements": {}
}
```

**Purposes can only have their `description` translated; Elements can have
both `displayName` and `description`.

To add a translation for an Element or Purpose:

1. Open `catalog.json` for the target language.
2. Add an entry keyed by the item's exact name — `displayName` and/or
   `description` under `elements`, `description` only under `purposes`:

   ```json
   {
     "purposes": {
       "marketing_via_email@v1": {
         "description": "ईमेल के माध्यम से मार्केटिंग"
       }
     },
     "elements": {
       "email-address": {
         "displayName": "ईमेल पता",
         "description": "उपयोगकर्ता का ईमेल पता"
       }
     }
   }
   ```

   The key must match the item's name **exactly**, character for character.

3. For a Purpose whose wording differs between versions, key that version
   specifically as `name@version` (for example `"marketing_via_email@v2"`)
   instead of just `name`. If a Purpose has no version-specific entry, its
   plain `name` entry (if any) applies to every version. Elements are never
   versioned, so this only applies under `purposes`.
4. Save. No restart needed.

**Until a language has an entry for a given item, that's expected** — readers
in that language simply see the English wording the administrator entered
when creating it, not an error or a blank field. There's no requirement to
translate every item into every language before going live; treat this file
as an ongoing translation backlog, not a deployment blocker.

## 4. Adding a new language

Not currently supported as a configuration change — the set of languages
offered by the switcher is built into the accelerator. Adding one requires an
engineering change and a rebuild; raise it with the engineering team rather
than attempting it by adding a new `i18n/<lang-code>/` folder on a running
deployment.
