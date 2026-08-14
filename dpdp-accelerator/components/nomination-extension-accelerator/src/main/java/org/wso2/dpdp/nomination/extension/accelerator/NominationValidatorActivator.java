package org.wso2.dpdp.nomination.extension.accelerator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.wso2.carbon.identity.oauth2.impersonation.validators.ImpersonationValidator;

/**
 * Registers {@link NominationImpersonationValidator} as an OSGi
 * {@link ImpersonationValidator} service the moment this bundle starts. We do it
 * from a BundleActivator instead of Declarative Services because it does not
 * depend on the SCR runtime processing our component descriptor - as long as the
 * bundle reaches ACTIVE, the service is registered and IS's
 * OAuth2ServiceComponent (@Reference cardinality=MULTIPLE) picks it up.
 */
public class NominationValidatorActivator implements BundleActivator {

    private static final Log LOG = LogFactory.getLog(NominationValidatorActivator.class);

    private ServiceRegistration<ImpersonationValidator> registration;

    @Override
    public void start(BundleContext context) {
        registration = context.registerService(
                ImpersonationValidator.class,
                new NominationImpersonationValidator(),
                null);
        LOG.info("OpenFGC NominationImpersonationValidator registered.");
    }

    @Override
    public void stop(BundleContext context) {
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
    }
}
