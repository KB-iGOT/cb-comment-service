package com.tarento.commenthub.transactional.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author Mahesh RV
 * @author Ruksana
 * <p>
 * A utility class to cache and retrieve properties.
 * It loads properties from specified files and provides methods to access them.
 * Also handles environment variable overrides for properties.
 */
@Component
public class PropertiesCache {
    // Logger for logging messages
    private final Logger logger = LogManager.getLogger(getClass());

    // Array of file names from which properties are loaded
    private final String[] fileName = {
            "cassandra.config.properties",
            "cassandratablecolumn.properties",
            "application.properties",
            "customerror.properties"
    };
    // Properties object to store loaded properties
    private final Properties configProp = new Properties();

    public PropertiesCache() {
        // Load properties from each file
        for (String file : fileName) {
            InputStream in = this.getClass().getClassLoader().getResourceAsStream(file);
            try {
                configProp.load(in);
            } catch (IOException e) {
                logger.error("Error loading properties from file '{}'", file, e);
            }
        }
    }

    /**
     * Method to get a property value
     *
     * @param key -key to be fetched.
     * @return - returns the values.
     */
    public String getProperty(String key) {
        String value = System.getenv(key);
        if (StringUtils.isNotBlank(value)) return value;
        return configProp.getProperty(key) != null ? configProp.getProperty(key) : key;
    }

    /**
     * Method to read a property value
     *
     * @param key - key to be read
     * @return - returns the value.
     */
    public String readProperty(String key) {
        String value = System.getenv(key);
        if (StringUtils.isNotBlank(value)) return value;
        return configProp.getProperty(key);
    }
}
