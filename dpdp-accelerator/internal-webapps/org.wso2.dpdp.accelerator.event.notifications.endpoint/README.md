# Event Notification REST models

`src/main/resources/event-notifications.yaml` owns the public JSON contract.
OpenAPI Generator produces endpoint-local models in `src/gen/java`.
Keep these generated files under version control and do not edit them by hand.

From the repository root, regenerate and verify this module with:

```sh
mvn -f dpdp-accelerator/internal-webapps/org.wso2.dpdp.accelerator.event.notifications.endpoint/pom.xml \
  -Ddpdp.dto.codegen.skip=false verify
```

This command requires the current reactor dependencies to be installed locally.
Normal builds compile the committed models with generation disabled. The generator
version is pinned in the parent POM. Review the specification and generated-source
diff together; a second regeneration must produce identical Java sources. Remove
obsolete generated models explicitly when deleting or renaming schemas.

## Mapping and compatibility

`EventNotificationDtoMapper` converts generated create requests to service DTOs,
and service results to generated response models, including pagination and nested
delivery history. Handlers retain their service DTO interfaces. The service and DAO
modules do not depend on generated endpoint models.

The API keeps epoch-millisecond timestamps, string-valued event response payloads,
null and empty collection behavior, status codes, and error envelopes. Response
delivery settings expose a null shared secret. Legacy metadata accepted in create
requests is documented as compatibility input; the existing handlers still derive
tenant/group context and ignore caller-supplied metadata on creation.

Generation maps URI properties to strings so the service remains responsible for
URL validation. Automatic bean validation is disabled to retain existing service
validation and error codes. Enum deserializers preserve the common enum parsers'
case handling, whitespace handling, aliases, and exception causes.

`src/main/openapi-templates/enumOuterClass.mustache` is the pinned generator's CXF
enum template with support added for `x-class-extra-annotation`. This attaches
the compatibility deserializers without editing generated enums. Review this
template whenever upgrading the generator.

Polling and completion requests remain raw strings through the endpoint and
handler because their signatures depend on the original body. Their generated
request models document the contract; they are not bound or reserialized before
signature verification. Polling responses are mapped normally.

## Verification

The TestNG suite includes mapper JSON comparisons against the previous service DTO
representation, nested fields, legacy request fields, enum aliases, error codes,
secret suppression, and exact signed-body forwarding. Maven verification enforces
the existing coverage threshold for handwritten code; generated DTOs are excluded.
Inspect the resulting WAR for generated classes and runtime dependencies before
performing a smoke test in Identity Server.
