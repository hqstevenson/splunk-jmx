/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.pronoia.splunk.eventcollector.EventCollectorClient;
import com.pronoia.splunk.stub.EventCollectorClientStub;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import javax.management.ObjectName;

import org.junit.Before;
import org.junit.Test;

public class SplunkJmxAttributeChangeMonitorConfigurationTest {

  SplunkJmxAttributeChangeMonitor instance;

  String[] rawInitialObjectNameStringArray = new String[]{"java.lang:type=GarbageCollector,name=*"};
  String[] rawObjectNameStringArray = new String[]{"org.apache.activemq:type=Broker,brokerName=*", "java.lang:type=GarbageCollector,name=PS MarkSweep"};

  String[] initialObjectNameStringArray = new String[rawInitialObjectNameStringArray.length];
  String[] objectNameStringArray = new String[rawObjectNameStringArray.length];

  ObjectName[] initialObjectNameArray = new ObjectName[rawInitialObjectNameStringArray.length];
  ObjectName[] objectNameArray = new ObjectName[rawObjectNameStringArray.length];

  List<ObjectName> initialObjectNameList = new LinkedList<>();
  List<ObjectName> objectNameList = new LinkedList<>();

  List<String> initialObjectNameStringList = new LinkedList<>();
  List<String> objectNameStringList = new LinkedList<>();

  Set<ObjectName> initialObjectNameSet = new TreeSet<>();
  Set<ObjectName> expectedObjectNameSet = new TreeSet<>();
  Set<ObjectName> combinedObjectNameSet = new TreeSet<>();

  String[] initialAttributeArray = new String[]{"BrokerId"};
  String[] attributeArray = new String[]{"TotalEnqueueCount", "TotalDequeueCount", "TotalConsumerCount", "TotalMessageCount"};

  List<String> initialAttributeList = new LinkedList<>();
  List<String> attributeList = new LinkedList<>();
  List<String> combinedAttributeList = new LinkedList<>();

  Set<String> initialAttributeSet = new TreeSet<>();
  Set<String> attributeSet = new TreeSet<>();
  Set<String> combinedAttributeSet = new TreeSet<>();

  @Before
  public void setUp() throws Exception {
    instance = new SplunkJmxAttributeChangeMonitor();
    // Generate the initial ObjectName set
    for (String rawInitialObjectNameString : rawInitialObjectNameStringArray) {
      ObjectName tmpObject = new ObjectName(rawInitialObjectNameString);
      String canonicalName = tmpObject.getCanonicalName();
      initialObjectNameSet.add(new ObjectName(canonicalName));
      combinedObjectNameSet.add(new ObjectName(canonicalName));
    }

    // Populate all the initial ObjectName collections - use canonical names;
    for (ObjectName object : initialObjectNameSet) {
      String canonicalName = object.getCanonicalName();
      initialObjectNameList.add(new ObjectName(canonicalName));
      initialObjectNameStringList.add(canonicalName);
    }

    for (int i = 0; i < initialObjectNameStringList.size(); ++i) {
      String canonicalName = initialObjectNameStringList.get(i);
      initialObjectNameArray[i] = new ObjectName(canonicalName);
      initialObjectNameStringArray[i] = canonicalName;
    }

    // Generate the expected ObjectName set
    for (String rawObjectNameString : rawObjectNameStringArray) {
      ObjectName tmpObject = new ObjectName(rawObjectNameString);
      String canonicalName = tmpObject.getCanonicalName();
      expectedObjectNameSet.add(new ObjectName(canonicalName));
      combinedObjectNameSet.add(new ObjectName(canonicalName));
    }

    // Populate all the ObjectName collections - use canonical names;
    for (ObjectName object : expectedObjectNameSet) {
      String canonicalName = object.getCanonicalName();
      objectNameList.add(new ObjectName(canonicalName));
      objectNameStringList.add(canonicalName);
    }

    for (int i = 0; i < objectNameStringList.size(); ++i) {
      String canonicalName = objectNameStringList.get(i);
      objectNameArray[i] = new ObjectName(canonicalName);
      objectNameStringArray[i] = canonicalName;
    }

    // Populate the sorted set of initial attributes
    for (String attribute : initialAttributeArray) {
      initialAttributeSet.add(attribute);
      combinedAttributeSet.add(attribute);
    }

    // Get the list of initial attribute names in the same order as the set
    for (String attribute : initialAttributeSet) {
      initialAttributeList.add(attribute);
    }

    // Populate the sorted set of attributes
    for (String attribute : attributeArray) {
      attributeSet.add(attribute);
      combinedAttributeSet.add(attribute);
    }

    // Get the list of attribute names in the same order as the set
    for (String attribute : attributeSet) {
      attributeList.add(attribute);
    }

    // Get the list of combined attribute names in the same order as the set
    for (String attribute : combinedAttributeSet) {
      combinedAttributeList.add(attribute);
    }
  }

  @Test
  public void testGetExecutorPoolSize() throws Exception {
    assertEquals("Unexpected default value", 1, instance.executorPoolSize);
    assertEquals(1, instance.getExecutorPoolSize());
    instance.executorPoolSize = 10;
    assertEquals(10, instance.getExecutorPoolSize());
  }

  @Test
  public void testSetExecutorPoolSize() throws Exception {
    assertEquals("Unexpected default value", 1, instance.executorPoolSize);
    instance.setExecutorPoolSize(10);
    assertEquals(10, instance.executorPoolSize);
  }

  @Test
  public void testSetObservedObjectsFromListOfStrings() throws Exception {
    assertTrue("Unexpected default value", instance.observedObjects.isEmpty());

    instance.observedObjects = initialObjectNameSet;
    assertFalse("Unexpected initial value", instance.observedObjects.isEmpty());

    instance.setObservedObjects(objectNameStringList);

    assertEquals(expectedObjectNameSet, instance.observedObjects);
  }

  @Test
  public void testSetObservedObjectsFromVarargStrings() throws Exception {
    assertTrue("Unexpected default value", instance.observedObjects.isEmpty());

    instance.observedObjects = initialObjectNameSet;
    assertFalse("Unexpected initial value", instance.observedObjects.isEmpty());

    instance.setObservedObjects(objectNameStringArray);

    assertEquals(expectedObjectNameSet, instance.observedObjects);
  }

  @Test
  public void testSetObservedObjectsFromVarargObjectNames() throws Exception {
    assertTrue("Unexpected default value", instance.observedObjects.isEmpty());

    instance.observedObjects = initialObjectNameSet;
    assertFalse("Unexpected initial value", instance.observedObjects.isEmpty());

    instance.setObservedObjects(objectNameArray);

    assertEquals(expectedObjectNameSet, instance.observedObjects);
  }

  @Test
  public void testAddObservedObjectsFromVarargStrings() throws Exception {
    assertTrue("Unexpected default value", instance.observedObjects.isEmpty());

    instance.observedObjects = initialObjectNameSet;
    assertFalse("Unexpected initial value", instance.observedObjects.isEmpty());

    instance.addObservedObjects(objectNameStringArray);

    assertEquals(combinedObjectNameSet, instance.observedObjects);
  }

  @Test
  public void testAddObservedObjectsFromVarargObjectNames() throws Exception {
    assertTrue("Unexpected default value", instance.observedObjects.isEmpty());

    instance.observedObjects = initialObjectNameSet;
    assertFalse("Unexpected initial value", instance.observedObjects.isEmpty());

    instance.addObservedObjects(objectNameArray);

    assertEquals(combinedObjectNameSet, instance.observedObjects);
  }

  @Test
  public void testRemoveObservedObjectFromString() throws Exception {
    assertTrue(instance.observedObjects.isEmpty());
    instance.setObservedObjects(objectNameStringArray);
    assertEquals(expectedObjectNameSet, instance.observedObjects);

    instance.removeObservedObject("java.lang:type=GarbageCollector,name=PS MarkSweep");
    assertNotEquals(expectedObjectNameSet, instance.observedObjects);
    expectedObjectNameSet.remove(new ObjectName("java.lang:type=GarbageCollector,name=PS MarkSweep"));
    assertEquals(expectedObjectNameSet, instance.observedObjects);
  }

  @Test
  public void testRemoveObservedObjectFromObjectName() throws Exception {
    assertTrue(instance.observedObjects.isEmpty());
    instance.setObservedObjects(objectNameStringArray);
    assertEquals(expectedObjectNameSet, instance.observedObjects);

    instance.removeObservedObject(new ObjectName("java.lang:type=GarbageCollector,name=PS MarkSweep"));
    assertNotEquals(expectedObjectNameSet, instance.observedObjects);
    expectedObjectNameSet.remove(new ObjectName("java.lang:type=GarbageCollector,name=PS MarkSweep"));
    assertEquals(expectedObjectNameSet, instance.observedObjects);
  }

  @Test
  public void testContainsObservedObjectFromString() throws Exception {
    assertTrue(instance.observedObjects.isEmpty());
    instance.setObservedObjects(objectNameStringArray);

    assertTrue(instance.containsObservedObject("java.lang:type=GarbageCollector,name=PS MarkSweep"));
    assertTrue(instance.containsObservedObject("java.lang:name=PS MarkSweep,type=GarbageCollector"));
    assertFalse(instance.containsObservedObject("java.lang:type=GarbageCollector,name=PS MarkSweep,dummy=value"));
  }

  @Test
  public void testContainsObservedObjectFromObjectName() throws Exception {
    assertTrue(instance.observedObjects.isEmpty());
    instance.setObservedObjects(objectNameStringArray);

    assertTrue(instance.containsObservedObject(new ObjectName("java.lang:type=GarbageCollector,name=PS MarkSweep")));
    assertTrue(instance.containsObservedObject(new ObjectName("java.lang:name=PS MarkSweep,type=GarbageCollector")));
    assertFalse(instance.containsObservedObject(new ObjectName("java.lang:type=GarbageCollector,name=PS MarkSweep,dummy=value")));
  }

  @Test
  public void testGetObservedObjects() throws Exception {
    assertTrue(instance.observedObjects.isEmpty());
    instance.setObservedObjects(objectNameStringArray);

    assertEquals(objectNameList, instance.getObservedObjects());
  }

  @Test
  public void testGetObservedObjectNames() throws Exception {
    assertTrue(instance.observedObjects.isEmpty());
    instance.setObservedObjects(objectNameStringArray);

    assertEquals(objectNameStringList, instance.getObservedObjectNames());
  }

  @Test
  public void testGetObservedAttributes() throws Exception {
    assertTrue("Unexpected default value", instance.observedAttributes.isEmpty());

    instance.observedAttributes = attributeSet;

    assertEquals(attributeList, instance.getObservedAttributes());
  }

  @Test
  public void testSetObservedAttributesFromListOfStrings() throws Exception {
    assertTrue("Unexpected default value", instance.observedAttributes.isEmpty());

    instance.observedAttributes = initialAttributeSet;
    assertFalse("Unexpected initial value", instance.observedAttributes.isEmpty());

    instance.setObservedAttributes(attributeList);

    assertEquals(attributeList, instance.getObservedAttributes());
  }

  @Test
  public void testSetObservedAttributesFromVarargStrings() throws Exception {
    assertTrue("Unexpected default value", instance.observedAttributes.isEmpty());

    instance.observedAttributes = initialAttributeSet;
    assertFalse("Unexpected initial value", instance.observedAttributes.isEmpty());

    instance.setObservedAttributes(attributeArray);

    assertEquals(attributeList, instance.getObservedAttributes());
  }

  @Test
  public void testAddObservedAttributes() throws Exception {
    assertTrue("Unexpected default value", instance.observedAttributes.isEmpty());

    instance.observedAttributes = initialAttributeSet;
    assertFalse("Unexpected initial value", instance.observedAttributes.isEmpty());

    instance.addObservedAttributes(attributeArray);

    assertEquals(combinedAttributeList, instance.getObservedAttributes());
  }

  @Test
  public void testGetCollectedAttributes() throws Exception {
    assertTrue("Unexpected default value", instance.collectedAttributes.isEmpty());

    instance.collectedAttributes = attributeSet;

    assertEquals(attributeList, instance.getCollectedAttributes());
  }

  @Test
  public void testSetCollectedAttributesFromListOfStrings() throws Exception {
    assertTrue("Unexpected default value", instance.collectedAttributes.isEmpty());

    instance.collectedAttributes = initialAttributeSet;
    assertFalse("Unexpected initial value", instance.collectedAttributes.isEmpty());

    instance.setCollectedAttributes(attributeList);

    assertEquals(attributeList, instance.getCollectedAttributes());
  }

  @Test
  public void testSetCollectedAttributesFromVarargStrings() throws Exception {
    assertTrue("Unexpected default value", instance.collectedAttributes.isEmpty());

    instance.collectedAttributes = initialAttributeSet;
    assertFalse("Unexpected initial value", instance.collectedAttributes.isEmpty());

    instance.setCollectedAttributes(attributeArray);

    assertEquals(attributeList, instance.getCollectedAttributes());
  }

  @Test
  public void testAddCollectedAttributes() throws Exception {
    assertTrue("Unexpected default value", instance.collectedAttributes.isEmpty());

    instance.collectedAttributes = initialAttributeSet;
    assertFalse("Unexpected initial value", instance.collectedAttributes.isEmpty());

    instance.addCollectedAttributes(attributeArray);

    assertEquals(combinedAttributeList, instance.getCollectedAttributes());
  }

  @Test
  public void testGetObservedAndCollectedAttributes() throws Exception {
    assertTrue("Unexpected default value", instance.observedAttributes.isEmpty());
    assertTrue("Unexpected default value", instance.collectedAttributes.isEmpty());

    instance.observedAttributes = initialAttributeSet;
    assertFalse("Unexpected initial value", instance.observedAttributes.isEmpty());

    instance.collectedAttributes = attributeSet;
    assertFalse("Unexpected initial value", instance.collectedAttributes.isEmpty());

    assertEquals(combinedAttributeList, instance.getObservedAndCollectedAttributes());
  }

  @Test
  public void testGetGranularityPeriod() throws Exception {
    assertEquals("Unexpected default value", 15, instance.granularityPeriod);
    assertEquals(15, instance.getGranularityPeriod());
    instance.granularityPeriod = 60;
    assertEquals(60, instance.getGranularityPeriod());
  }

  @Test
  public void testSetGranularityPeriod() throws Exception {
    assertEquals("Unexpected default value", 15, instance.granularityPeriod);
    instance.setGranularityPeriod(60);
    assertEquals(60, instance.granularityPeriod);
  }

  @Test
  public void testGetMaxSuppressedDuplicates() throws Exception {
    assertEquals("Unexpected default value", -1, instance.maxSuppressedDuplicates);
    assertEquals(-1, instance.getMaxSuppressedDuplicates());
    instance.maxSuppressedDuplicates = 5;
    assertEquals(5, instance.getMaxSuppressedDuplicates());
  }

  @Test
  public void testSetMaxSuppressedDuplicates() throws Exception {
    assertEquals("Unexpected default value", -1, instance.maxSuppressedDuplicates);
    instance.setMaxSuppressedDuplicates(5);
    assertEquals(5, instance.maxSuppressedDuplicates);
  }

  @Test
  public void testEmptyAttributesIncluded() throws Exception {
    assertTrue("Unexpected default value", instance.includeEmptyAttrs);

    instance.includeEmptyAttrs = false;

    assertFalse(instance.emptyAttributesIncluded());
  }

  @Test
  public void testIncludeEmptyAttributes() throws Exception {
    assertTrue("Unexpected default value", instance.includeEmptyAttrs);

    instance.includeEmptyAttrs = false;
    instance.includeEmptyAttributes();

    assertTrue(instance.includeEmptyAttrs);
  }

  @Test
  public void testExcludeEmptyAttributes() throws Exception {
    assertTrue("Unexpected default value", instance.includeEmptyAttrs);

    instance.excludeEmptyAttributes();

    assertFalse(instance.includeEmptyAttrs);
  }

  @Test
  public void testEmptyObjectNameListsIncluded() throws Exception {
    assertFalse("Unexpected default value", instance.includeEmptyLists);

    instance.includeEmptyLists = true;

    assertTrue(instance.emptyObjectNameListsIncluded());
  }

  @Test
  public void testIncludeEmptyObjectNameLists() throws Exception {
    assertFalse("Unexpected default value", instance.includeEmptyLists);

    instance.includeEmptyObjectNameLists();

    assertTrue(instance.includeEmptyLists);
  }

  @Test
  public void testExcludeEmptyObjectNameLists() throws Exception {
    assertFalse("Unexpected default value", instance.includeEmptyLists);

    instance.includeEmptyLists = true;
    instance.excludeEmptyObjectNameLists();

    assertFalse(instance.includeEmptyLists);
  }

  @Test
  public void testGetSplunkClient() throws Exception {
    assertNull("Unexpected default value", instance.splunkClient);

    EventCollectorClient stub = new EventCollectorClientStub();
    instance.splunkClient = stub;

    assertSame(stub, instance.getSplunkClient());
  }

  @Test
  public void testSetSplunkClient() throws Exception {
    assertNull("Unexpected default value", instance.splunkClient);

    EventCollectorClient stub = new EventCollectorClientStub();
    instance.setSplunkClient(stub);

    assertSame(stub, instance.splunkClient);
  }

  @Test
  public void testGetSplunkEventSourcetype() throws Exception {
    assertEquals("Unexpected default value", "jmx-attributes", instance.splunkEventSourcetype);

    instance.splunkEventSourcetype = "fred";

    assertEquals("fred", instance.getSplunkEventSourcetype());
  }

  @Test
  public void testSetSplunkEventSourcetype() throws Exception {
    assertEquals("Unexpected default value", "jmx-attributes", instance.splunkEventSourcetype);

    instance.setSplunkEventSourcetype("fred");

    assertEquals("fred", instance.splunkEventSourcetype);
  }

  @Test
  public void testStartStop() throws Exception {
    EventCollectorClientStub clientStub = new EventCollectorClientStub();

    instance.setSplunkClient(clientStub);
    instance.setGranularityPeriod(1);

    instance.start();
    Thread.sleep(1500);
    instance.stop();

    assertNull(clientStub.lastEvent);
  }

}