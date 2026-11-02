package com.epam.resource.component;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

/**
 * JUnit Platform Suite runner for Cucumber component tests.
 *
 * Run via: mvn test -Dtest=ResourceComponentTest
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.epam.resource.component")
public class ResourceComponentTest {
    // No body — this class is the test entry point for the Cucumber suite
}
