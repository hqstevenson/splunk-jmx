/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pronoia.splunk.jmx;

import com.pronoia.splunk.eventcollector.EventBuilder;
import com.pronoia.splunk.eventcollector.EventCollectorClient;
import com.pronoia.splunk.eventcollector.EventDeliveryException;
import com.pronoia.splunk.eventcollector.SplunkMDCHelper;
import com.pronoia.splunk.jmx.eventcollector.eventbuilder.JmxNotificationEventBuilder;

import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;


/**
 * Utility class for sending JMX Notifications to Splunk
 *
 * source = ObjectName of the bean which emits the Notification
 * sourcetype = JMX Notification Type ( returned from Notification.getType() )
 * timestamp = JMX Notification Timestamp ( returned from Notification.getTimeStamp() )
 *
 * <p>index can be specified
 */
public interface SplunkJmxNotificationListenerMBean {
    String getNotificationListenerId();

    Date getStartTime();
    Date getStopTime();
    Date getLastNotificationTime();
    String getLastNotificationType();

    Set<String> getSourceMBeans();

    void start();
    void stop();
    void restart();

    boolean isRunning();

}
