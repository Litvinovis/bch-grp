package ru.chebe.litvinov;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

public class Ignite3ConfiguratorTest {

    @Test
    public void class_hasGetClientMethod() throws Exception {
        Ignite3Configurator cfg = new Ignite3Configurator("127.0.0.1:10300");
        assertNotNull(cfg);
        Method m = Ignite3Configurator.class.getMethod("getClient");
        assertNotNull(m);
    }
}
