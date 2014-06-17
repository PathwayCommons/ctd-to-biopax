package com.google.gsoc14.ctd2biopax;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class CTD2BioPAXConverterTest extends TestCase {
    public CTD2BioPAXConverterTest(String testName)
    {
        super( testName );
    }

    public static Test suite()
    {
        return new TestSuite( CTD2BioPAXConverterTest.class );
    }

    public void testApp()
    {
        assertTrue( true );
    }
}
