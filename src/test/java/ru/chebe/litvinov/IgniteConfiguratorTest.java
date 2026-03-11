package ru.chebe.litvinov;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class IgniteConfiguratorTest {

    @Test
    public void class_hasGetIgniteMethod() throws Exception {
        IgniteConfigurator cfg = new IgniteConfigurator();
        assertNotNull(cfg);
        Method m = IgniteConfigurator.class.getMethod("getIgnite");
        assertNotNull(m);
    }
}
