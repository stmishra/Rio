/*
 * Copyright 2008 the original author or authors.
 * Copyright 2005 Sun Microsystems, Inc.
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
package org.rioproject.sla;

import com.sun.jini.admin.DestroyAdmin;
import net.jini.admin.Administrable;
import org.rioproject.core.jsb.ServiceBeanManager;
import org.rioproject.opstring.OperationalStringException;
import org.rioproject.watch.Calculable;
import org.rioproject.watch.ThresholdType;
import org.rioproject.watch.ThresholdValues;

import java.rmi.RemoteException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The RedeployPolicyHandler will redeploy a service based on declared
 * thresholds.
 *
 * @author Dennis Reedy
 */
public class RedeployPolicyHandler extends SLAPolicyHandler {
    /** The description of the SLA Handler */
    private static final String description = "Redeploy Policy Handler";
    /** Dampening value for upper thresholds being crossed */
    private long upperThresholdDampeningTime;
    /** Dampening value for lower thresholds being crossed */
    private long lowerThresholdDampeningTime;
    /** The Timer to use for scheduling a redeploy task */
    private Timer taskTimer;
    /** The RedeployTask for process redeploy */
    private RedeployTask redeployTask;
    /** Actions that indicate status of a redeploy request */
    enum Action { REDEPLOY_PENDING, REDEPLOY_FAILURE, REDEPLOY_SUCCEEDED }    
    /** Logger for this component */
    static Logger logger = Logger.getLogger("org.rioproject.sla");

    /**
     * Construct a RedeployPolicyHandler
     * 
     * @param sla The SLA for the RedeployPolicyHandler
     */
    public RedeployPolicyHandler(SLA sla) {
        super(sla);
        taskTimer = new Timer(true);
    }

    /**
     * Override parent's method to return description for this SLA Handler
     * 
     * @return The descriptive attribute for this SLA Handler
     */
    @Override
    public String getDescription() {
        return (description);
    }

    /**
     * Override parent's method to shutdown timer
     */
    @Override
    public void disconnect() {
        if(taskTimer != null)
            taskTimer.cancel();
        super.disconnect();
    }

    @Override
    public void setSLA(SLA sla) {
        super.setSLA(sla);
        upperThresholdDampeningTime =
            (getSLA().getUpperThresholdDampeningTime()==0?1000:
             getSLA().getUpperThresholdDampeningTime());

        lowerThresholdDampeningTime =
            (getSLA().getLowerThresholdDampeningTime()==0?1000:
             getSLA().getLowerThresholdDampeningTime());
    }    

    @Override
    public void notify(Calculable calculable, ThresholdValues thresholdValues, ThresholdType type) {
        if(logger.isLoggable(Level.FINE)) {
            String status = (type == ThresholdType.BREACHED? "breached":"cleared");
            logger.fine("RedeployPolicyHandler [" + getID() + "]: Threshold ["
                        + calculable.getId() + "] " + status + " value ["
                        + calculable.getValue() + "\n] low ["
                        + thresholdValues.getCurrentLowThreshold() + "]" + " high ["
                        + thresholdValues.getCurrentHighThreshold() + "]");
        }
        if(type == ThresholdType.BREACHED) {
            double tValue = calculable.getValue();
            if(tValue > thresholdValues.getCurrentHighThreshold()) {
                doRedeploy(upperThresholdDampeningTime, "upper");
            } else {
                doRedeploy(lowerThresholdDampeningTime, "lower");
            }

            /* Threshold has been cleared */
        } else {
            if(redeployTask!=null) {
                redeployTask.cancel();
                redeployTask = null;
            }
        }
        sendSLAThresholdEvent(calculable, thresholdValues, type);
    }

    /*
     * Determine whether a RedeployTask should be created based on the
     * delay value provided, or to relocate immediately
     */
    private void doRedeploy(long delay, String type) {
        if(delay > 0) {
            redeployTask = new RedeployTask();
            long now = System.currentTimeMillis();
            if(logger.isLoggable(Level.FINE))
                logger.fine("["+context.getServiceElement().getName()+"] "+
                            "RedeployPolicyHandler ["+getID()+"]: "+
                            "Schedule redeploy task in "+
                            "["+delay+"] millis");
            try {
                taskTimer.schedule(redeployTask, new Date(now+delay));
            } catch(IllegalStateException e) {
                logger.log(Level.WARNING,
                           "Force disconnect of "+
                           "["+context.getServiceElement().getName()+"] "+
                           "RedeployPolicyHandler",
                           e);
                disconnect();
            }
        } else {
            logger.info("["+context.getServiceElement().getName()+"] "+
                        "RedeployPolicyHandler ["+getID()+"]: "+
                        "no "+type+" dampener, perform redeploy");
            doRedeploy();
        }
    }


    /**
     * Perform the redeploy
     */
    void doRedeploy() {
        notifyListeners(new SLAPolicyEvent(this,
                                           getSLA(),
                                           Action.REDEPLOY_PENDING.name()));
        ServiceBeanManager mgr = context.getServiceBeanManager();
        try {
            mgr.getOperationalStringManager().redeploy(context.getServiceElement(),
                                                       mgr.getServiceBeanInstance(),
                                                       false,
                                                       true,
                                                       0,
                                                       null);
        } catch (OperationalStringException e) {
            notifyListeners(new SLAPolicyEvent(this,
                                               getSLA(),
                                               Action.REDEPLOY_FAILURE.name()));
            if(!e.isManaged()) {
                logger.warning("Attempt to redeploy service " +
                               "["+context.getServiceElement().getName()+"] "+
                               "failed, it is not under management control. " +
                               "Terminating the service.");
                try {
                    Administrable admin =
                        (Administrable)mgr.getServiceBeanInstance().getService();
                    DestroyAdmin dAdmin = (DestroyAdmin) admin.getAdmin();
                    dAdmin.destroy();
                } catch(Exception ex) {
                    logger.log(Level.SEVERE,
                               "Unable to destroy service "+
                               "["+context.getServiceElement().getName()+"] "+
                               ex);
                }
            } else {
                getSLA().resetHighThreshold();
                getSLA().resetLowThreshold();
            }
        } catch(RemoteException e) {
            logger.warning("Attempt to redeploy service failed ["+
                           e.getClass().getName()+" : "+
                           e.getLocalizedMessage()+"]");
            notifyListeners(new SLAPolicyEvent(this,
                                               getSLA(),
                                               Action.REDEPLOY_FAILURE.name()));
            getSLA().resetHighThreshold();
            getSLA().resetLowThreshold();
        }
    }

    /**
     * The RedeployTask is used to schedule a process redeploy be performed at
     * some time in the future.
     */
    class RedeployTask extends TimerTask {
        public void run() {
            doRedeploy();
        }
    }
}
