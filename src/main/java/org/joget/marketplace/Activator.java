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
        // Registering both Process Tool and Form Element services in OSGi context
        registrationList
                .add(context.registerService(smsNotificationTool.class.getName(), new smsNotificationTool(), null));
        registrationList
                .add(context.registerService(smsNotificationFormElement.class.getName(), new smsNotificationFormElement(), null));
    }

    @Override
    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}