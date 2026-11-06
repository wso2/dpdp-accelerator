# verify-is-api results

- Date: 2026-10-13
- Identity Server: WSO2 IS 7.3.0, U2 update level 19, local pack, H2, DPDP accelerator 1.0.0-SNAPSHOT (branch poc-cookie-consent) merged
- Chosen `subjectQuery`: `userId` (check 5 passed)

```
PASS  1. Token carries all four scopes
PASS  2. cookie_analytics has type Cookie, a latest version and the _demo_analytics element
PASS  3. Create a REJECTED consent for a subject that is not an IS user
PASS  4. Create an ACTIVE consent for the same subject
PASS  5. List by userId=<visitor id> returns exactly the two consents
PASS  6. List by properties.subjectRef returns exactly the two consents
PASS  7. Summaries carry a ms timestamp, purposes[].versionId and properties
PASS  8. M2M revoke turns the ACTIVE consent REVOKED
PASS  9. List after revoke still returns the consent, now REVOKED

9/9 checks passed. Test subject: v-747faf9b-8f29-496a-88eb-623277e37e03
```
