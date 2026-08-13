# Components

OSGi bundles that extend WSO2 Identity Server itself — configuration parsers, consent management
extension points, identity extensions, shared utilities. Each is built as a `bundle` and installed
into `<IS_HOME>/repository/components/dropins` by the accelerator's antrun step.

The consent portal deliberately avoids an OSGi component: it reads its configuration from `web.xml`
context-params overridden by `<IS_HOME>/repository/conf/dpdp-portal.properties`, and uses the JDK
HTTP client, so it needs no shared bundle.

`org.wso2.dpdp.accelerator.complaint.mgt.dao` and `org.wso2.dpdp.accelerator.complaint.mgt.service`
are the one exception: plain jars (not OSGi bundles) holding the complaint management persistence
and business logic, consumed only by
`internal-webapps/org.wso2.dpdp.accelerator.complaint.mgt.endpoint`. They live here rather than
inside that webapp module because nothing else in the accelerator combines three Maven modules into
one deployable, and splitting them keeps each layer independently testable.

This directory has **no aggregator `pom.xml`**, following the Financial Services accelerator. Add each
module to `dpdp-accelerator/pom.xml` with its path prefix:

```xml
<module>components/org.wso2.dpdp.accelerator.common</module>
```

A new OSGi component must also be copied into the distribution — add a `<copy>` for its `target`
directory to the antrun `create-solution` execution in `accelerators/dpdp-is/pom.xml`. The complaint
DAO/service jars need no such step: they are pulled in as regular Maven dependencies of the endpoint
WAR, which is what actually gets distributed.
