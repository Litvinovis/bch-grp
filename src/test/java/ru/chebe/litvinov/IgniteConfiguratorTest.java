package ru.chebe.litvinov;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

public class IgniteConfiguratorTest {

    @Test
    public void class_hasGetIgniteClientMethod() throws Exception {
        IgniteConfigurator cfg = new IgniteConfigurator(List.of("127.0.0.1:10300"));
        assertNotNull(cfg);
        Method m = IgniteConfigurator.class.getMethod("getIgniteClient");
        assertNotNull(m);
    }
}
