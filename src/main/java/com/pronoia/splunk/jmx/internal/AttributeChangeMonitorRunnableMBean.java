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
package com.pronoia.splunk.jmx.internal;

import java.util.Date;
import java.util.List;
import java.util.Set;


/**
 * Utility class for sending JMX Notifications to Splunk
 *
 * source = ObjectName of the bean which emits the Notification
 * sourcetype = JMX Notification Type ( returned from Notification.getType() )
 * timestamp = JMX Notification Timestamp ( returned from Notification.getTimeStamp() )
 *
 * <p>index can be specified
 */
public interface AttributeChangeMonitorRunnableMBean {
    String getChangeMonitorId();
    String getChangeMonitorRunnableId();

    int getMaxSuppressedDuplicates();

    Date getLastPollTime();
    long getLastPollObjectCount();

    String getObjectNameQuery();
    Set<String> getObservedAttributes();
    Set<String> getCollectedAttributes();
    Set<String> getExcludedAttributes();

    boolean isRunning();

}
