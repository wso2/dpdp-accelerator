# Components

OSGi bundles that extend WSO2 Identity Server itself — configuration parsers, consent management
extension points, identity extensions, shared utilities. Each is built as a `bundle` and installed
into `<IS_HOME>/repository/components/dropins` by the accelerator's antrun step.

Nothing lives here yet. The consent portal deliberately avoids an OSGi component: it reads its
configuration from `web.xml` context-params overridden by
`<IS_HOME>/repository/conf/dpdp-portal.properties`, and uses the JDK HTTP client, so it needs no
shared bundle.

This directory has **no aggregator `pom.xml`**, following the Financial Services accelerator. Add each
module to `dpdp-accelerator/pom.xml` with its path prefix:

```xml
<module>components/org.wso2.dpdp.accelerator.common</module>
```

A new component must also be copied into the distribution — add a `<copy>` for its `target` directory
to the antrun `create-solution` execution in `accelerators/dpdp-is/pom.xml`.
