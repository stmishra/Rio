/*
 * Copyright to the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rioproject.cybernode;

import net.jini.admin.Administrable;
import net.jini.admin.JoinAdmin;
import org.rioproject.core.jsb.ServiceBeanContext;
import org.rioproject.core.jsb.ServiceElementChangeListener;
import org.rioproject.jsb.ServiceBeanSLAManager;
import org.rioproject.jsb.ServiceElementUtil;
import org.rioproject.log.LoggerConfig;
import org.rioproject.logging.WrappedLogger;
import org.rioproject.opstring.ServiceBeanConfig;
import org.rioproject.opstring.ServiceElement;
import org.rioproject.sla.ServiceLevelAgreements;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listen for ServiceElement changes
 *
 * @author Dennis Reedy
 */
public class ServiceElementChangeManager implements ServiceElementChangeListener {
    private final ServiceBeanContext context;
    /* Manage declared SLAs */
    private final ServiceBeanSLAManager serviceBeanSLAManager;
    private final Object serviceProxy;
    private static final WrappedLogger logger = WrappedLogger.getLogger(ServiceElementChangeManager.class.getName());

    public ServiceElementChangeManager(ServiceBeanContext context,
                                       ServiceBeanSLAManager serviceBeanSLAManager,
                                       Object serviceProxy) {
        this.context = context;
        this.serviceBeanSLAManager = serviceBeanSLAManager;
        this.serviceProxy = serviceProxy;
    }

    /* (non-Javadoc)
    * @see org.rioproject.core.jsb.ServiceElementChangeListener#changed
    */
    public void changed(ServiceElement preElem, ServiceElement postElem) {
        if (logger.isLoggable(Level.FINEST))
            logger.fine("[%s] ServiceElementChangeManager notified", context.getServiceElement().getName());
        /* ------------------------------------------*
       *  SLA Update Processing
       * ------------------------------------------*/

        /* Get the new SLAs */
        ServiceLevelAgreements slas = postElem.getServiceLevelAgreements();
        /* Modify service SLAs */
        serviceBeanSLAManager.updateSLAs(slas.getServiceSLAs());

        /* --- End SLA Update Processing ---*/

        /* --- Update Logging --- */
        if (ServiceElementUtil.hasDifferentLoggerConfig(preElem, postElem)) {
            Map map = postElem.getServiceBeanConfig().getConfigurationParameters();
            LoggerConfig[] newLoggerConfigs = (LoggerConfig[]) map.get(ServiceBeanConfig.LOGGER);
            map = preElem.getServiceBeanConfig().getConfigurationParameters();
            LoggerConfig[] currentLoggerConfigs = (LoggerConfig[]) map.get(ServiceBeanConfig.LOGGER);
            for (LoggerConfig newLoggerConfig : newLoggerConfigs) {
                if (LoggerConfig.isNewLogger(newLoggerConfig, currentLoggerConfigs)) {
                    newLoggerConfig.getLogger();
                } else if (LoggerConfig.levelChanged(newLoggerConfig, currentLoggerConfigs)) {
                    Logger.getLogger(newLoggerConfig.getLoggerName()).setLevel(newLoggerConfig.getLoggerLevel());
                }
            }
        }
        /* --- End Update Logging --- */

        /* --- Update Discovery --- */

        /* If the groups or LookupLocators have changed, update the
         * attributes using JoinAdmin capabilities */

        boolean hasDifferentGroups = ServiceElementUtil.hasDifferentGroups(preElem, postElem);
        boolean hasDifferentLocators = ServiceElementUtil.hasDifferentLocators(preElem, postElem);
        if (hasDifferentGroups || hasDifferentLocators) {
            logger.finest("[%s] Discovery has changed", context.getServiceElement().getName());
            if (serviceProxy instanceof Administrable) {
                try {
                    Administrable admin = (Administrable) serviceProxy;
                    Object adminObject;
                    adminObject = admin.getAdmin();
                    if (adminObject instanceof JoinAdmin) {
                        JoinAdmin joinAdmin = (JoinAdmin) adminObject;
                        /* Update groups if they have changed */
                        if (hasDifferentGroups) {
                            joinAdmin.setLookupGroups(postElem.getServiceBeanConfig().getGroups());
                        }
                        /* Update locators if they have changed */
                        if (hasDifferentLocators)
                            joinAdmin.setLookupLocators(postElem.getServiceBeanConfig().getLocators());
                    } else {
                        logger.fine("No JoinAdmin capabilities for %s", context.getServiceElement().getName());
                    }
                } catch (RemoteException e) {
                    logger.log(Level.SEVERE, "Modifying Discovery attributes", e);
                }
            } else {
                logger.fine("No Administrable capabilities for %s", serviceProxy.getClass().getName());
            }
            /* --- End Update Discovery --- */
        }
    }
}