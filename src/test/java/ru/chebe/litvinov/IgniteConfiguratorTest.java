package ru.chebe.litvinov;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

public class IgniteConfiguratorTest {

    @Test
    public void class_hasGetIgniteMethod() throws Exception {
        IgniteConfigurator cfg = new IgniteConfigurator("127.0.0.1", List.of("127.0.0.1:47500"), "/tmp/ignite-test");
        assertNotNull(cfg);
        Method m = IgniteConfigurator.class.getMethod("getIgnite");
        assertNotNull(m);
    }
}
