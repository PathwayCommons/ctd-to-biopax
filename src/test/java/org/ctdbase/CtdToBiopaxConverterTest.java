package org.ctdbase;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class CtdToBiopaxConverterTest extends TestCase {
    public CtdToBiopaxConverterTest(String testName)
    {
        super( testName );
    }

    public static Test suite()
    {
        return new TestSuite( CtdToBiopaxConverterTest.class );
    }

    public void testApp()
    {
        assertTrue( true );
    }
}
