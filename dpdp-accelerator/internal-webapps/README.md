# Internal webapps

Web applications deployed into `<IS_HOME>/repository/deployment/server/webapps` — typically JAX-RS
APIs or authentication endpoints that run inside the Identity Server. WAR names use `#` as a path
separator, so `api#dpdp#consent.war` is served at `/api/dpdp/consent`.

The consent portal is a `react-apps` module rather than an internal webapp because it ships a
user-facing SPA alongside its backend-for-frontend; this directory is for server-side endpoints with
no UI, such as `org.wso2.dpdp.accelerator.complaint.mgt.endpoint`
(`api#dpdp#complaints.war`, served at `/api/dpdp/complaints`), the JAX-RS API backing DPDP grievance
redressal.

This directory has **no aggregator `pom.xml`**, following the Financial Services accelerator. Add each
module to `dpdp-accelerator/pom.xml` with its path prefix:

```xml
<module>internal-webapps/org.wso2.dpdp.accelerator.consent.mgt.endpoint</module>
```

A new webapp must also be unzipped into the distribution — add an `<unzip>` for its WAR to the antrun
`create-solution` execution in `accelerators/dpdp-is/pom.xml`.
