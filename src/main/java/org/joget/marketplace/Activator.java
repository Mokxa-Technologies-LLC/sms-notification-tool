package org.joget.marketplace;

import java.util.ArrayList;
import java.util.List;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected List<ServiceRegistration> registrationList = new ArrayList<ServiceRegistration>();

    @Override
    public void start(BundleContext context) {
        // FIXED: Registering the service under its exact class name
        registrationList
                .add(context.registerService(smsNotificationTool.class.getName(), new smsNotificationTool(), null));
    }

    @Override
    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}