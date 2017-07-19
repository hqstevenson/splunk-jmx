package com.pronoia.splunk.jmx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Collection of utility methods for testing.
 */
public class TestSupportUtils {
    Logger log = LoggerFactory.getLogger(this.getClass());
    /**
     * This is a public method added for testing and loading the exclused-observed-attributes.properties outside of the
     * osgi container.
     */
    public void loadExcludedProperties(SplunkJmxAttributeChangeMonitor instance){
        InputStream inputStream = null;
        Set<String> xObservedAttributes=new HashSet<>();
        try {
            Properties excludedAttributeProperties = new Properties();
            log.info("Loading excluded-observed-attributes.properties from class path");
            inputStream = ClassLoader.class.getResourceAsStream("/excluded-observed-attributes.properties");
            excludedAttributeProperties.load(inputStream);
            for (Map.Entry<?, ?> entry: excludedAttributeProperties.entrySet()) {
                boolean addToExcluded=Boolean.getBoolean((String)entry.getKey());
                if(addToExcluded){
                    log.info("Adding {} to excluded observed attributes.",(String)entry.getKey());
                    xObservedAttributes.add((String)entry.getKey());
                }else{
                    log.info("Not adding {} to excluded observed attributes.",(String)entry.getKey());
                }
            }
        } catch (FileNotFoundException e) {
            log.error("ERROR WARNING!! Could potentially flood splunk:{}",e);
        } catch (IOException e) {
            log.error("ERROR WARNING!! Could potentially flood splunk:{}",e);
        } catch(Exception e){
            log.error("ERROR WARNING!! Could potentially flood splunk:{}",e);
        }
        instance.setExcludedObservedAttributes(xObservedAttributes);
    }
}
