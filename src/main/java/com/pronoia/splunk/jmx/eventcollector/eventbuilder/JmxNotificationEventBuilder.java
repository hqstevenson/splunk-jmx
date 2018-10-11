/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pronoia.splunk.jmx.eventcollector.eventbuilder;

import java.util.HashMap;
import java.util.Map;

import javax.management.Notification;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import com.pronoia.splunk.eventcollector.EventBuilder;
import com.pronoia.splunk.eventcollector.EventCollectorClient;
import com.pronoia.splunk.eventcollector.eventbuilder.EventBuilderSupport;

import static com.pronoia.splunk.eventcollector.EventCollectorInfo.EVENT_BODY_KEY;

public class JmxNotificationEventBuilder extends JmxEventBuilderSupport<Notification> {
    public static final String NOTIFICATION_TYPE_KEY = "notificationType";
    public static final String NOTIFICATION_MESSAGE_KEY = "notificationMessage";
    public static final String NOTIFICATION_SEQUENCE_NUMBER_KEY = "notificationSequenceNumber";
    public static final String NOTIFICATION_SOURCE_KEY = "notificationSource";
    public static final String NOTIFICATION_USER_DATA_KEY = "userData";

    boolean includeNotificationMessage;
    boolean includeNotificationSequenceNumber;
    boolean includeNotificationSource;
    boolean includeNotificationType;
    boolean includeUserData = true;

    public boolean isIncludeNotificationMessage() {
        return includeNotificationMessage;
    }

    public void setIncludeNotificationMessage(boolean includeNotificationMessage) {
        this.includeNotificationMessage = includeNotificationMessage;
    }

    public boolean isIncludeNotificationSequenceNumber() {
        return includeNotificationSequenceNumber;
    }

    public void setIncludeNotificationSequenceNumber(boolean includeNotificationSequenceNumber) {
        this.includeNotificationSequenceNumber = includeNotificationSequenceNumber;
    }

    public boolean isIncludeNotificationSource() {
        return includeNotificationSource;
    }

    public void setIncludeNotificationSource(boolean includeNotificationSource) {
        this.includeNotificationSource = includeNotificationSource;
    }

    public boolean isIncludeNotificationType() {
        return includeNotificationType;
    }

    public void setIncludeNotificationType(boolean includeNotificationType) {
        this.includeNotificationType = includeNotificationType;
    }

    public boolean isIncludeUserData() {
        return includeUserData;
    }

    public void setIncludeUserData(boolean includeUserData) {
        this.includeUserData = includeUserData;
    }

    @Override
    public EventBuilder<Notification> duplicate() {
        JmxNotificationEventBuilder answer = new JmxNotificationEventBuilder();

        answer.copyConfiguration(this);

        return answer;
    }

    @Override
    public String getSourceFieldValue(EventCollectorClient client) {
        if (hasEventBody()) {
            return getEventBody().getSource().toString();
        }

        return super.getSourceFieldValue(client);
    }

    @Override
    public String getTimestampFieldValue() {
        if (hasEventBody()) {
            return String.format("%.3f", getEventBody().getTimeStamp() / 1000.0);
        }

        return super.getTimestampFieldValue();
    }

    @Override
    protected void addAdditionalFieldsToMap(EventCollectorClient client, Map<String, Object> map) {
        super.addAdditionalFieldsToMap(client, map);

        if (includeNotificationType) {
            map.put(NOTIFICATION_TYPE_KEY, getEventBody().getType());
        }

        if (includeNotificationMessage) {
            addField(NOTIFICATION_MESSAGE_KEY, getEventBody().getMessage());
        }

        if (includeNotificationSequenceNumber) {
            addField(NOTIFICATION_SEQUENCE_NUMBER_KEY, Long.toString(getEventBody().getSequenceNumber()));
        }

        if (includeNotificationSource) {
            addField(NOTIFICATION_SOURCE_KEY, getEventBody().getSource().toString());
        }
    }

    @Override
    protected void addEventBodyToMap(Map<String, Object> map) {
        Map<String, Object> notificationEvent = new HashMap<>();

        if (includeUserData) {
            Object userData = getEventBody().getUserData();
            if (userData != null) {
                if (userData instanceof CompositeData) {
                    log.trace("Processing Composite Data for 'userData'");
                    addCompositeData(notificationEvent, (CompositeData) userData);
                } else if (userData instanceof TabularData) {
                    log.trace("Processing Tabular Data for 'userData'");
                    addTabularData(notificationEvent, (TabularData) userData);
                } else {
                    log.debug("Processing {} for {}", userData.getClass().getName(), "userData");
                    notificationEvent.put(NOTIFICATION_USER_DATA_KEY, userData.toString());
                }
            }

            map.put(EVENT_BODY_KEY, notificationEvent);
        }
    }

    @Override
    protected void copyConfiguration(EventBuilderSupport<Notification> sourceEventBuilder) {
        super.copyConfiguration(sourceEventBuilder);

        if (sourceEventBuilder instanceof JmxNotificationEventBuilder) {
            JmxNotificationEventBuilder sourceJmxNotificationEventBuilder = (JmxNotificationEventBuilder) sourceEventBuilder;
            this.includeNotificationMessage = sourceJmxNotificationEventBuilder.includeNotificationMessage;
            this.includeNotificationSequenceNumber = sourceJmxNotificationEventBuilder.includeNotificationSequenceNumber;
            this.includeNotificationSource = sourceJmxNotificationEventBuilder.includeNotificationSource;
            this.includeNotificationType = sourceJmxNotificationEventBuilder.includeNotificationType;
            this.includeUserData = sourceJmxNotificationEventBuilder.includeUserData;
        }
    }

    @Override
    protected void appendConfiguration(StringBuilder builder) {
        super.appendConfiguration(builder);

        builder.append(" includeNotificationMessage='").append(includeNotificationMessage).append('\'')
            .append(" includeNotificationSequenceNumber='").append(includeNotificationSequenceNumber).append('\'')
            .append(" includeNotificationSource='").append(includeNotificationSource).append('\'')
            .append(" includeNotificationType='").append(includeNotificationType).append('\'')
            .append(" includeUserData='").append(includeUserData).append('\'');

        return;
    }

}
