package com.alphora.cql.service.serialization;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.opencds.cqf.cql.runtime.Interval;
import org.testng.annotations.Test;

public class InterimBadCqlTypeSerializerTests {

    @Test
    public void testParseInterval() {
        InterimBadCqlTypeSerializer ibcts = new InterimBadCqlTypeSerializer();
        Object result = ibcts.deserialize("Interval[@2019-08-01, @2021-09-01)");
        assertNotNull(result);
        assertTrue(result instanceof Interval);  
    }
}