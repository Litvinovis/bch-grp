package ru.chebe.litvinov.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceCoverageSmokeTest {

    @Test
    public void serviceClasses_areLoadable_andHaveMethods() {
        assertClassHasMethods(ItemsManager.class);
        assertClassHasMethods(ClanManager.class);
        assertClassHasMethods(EventsManager.class);
        assertClassHasMethods(IdeasManager.class);
        assertClassHasMethods(LocationManager.class);
    }

    private void assertClassHasMethods(Class<?> type) {
        assertNotNull(type);
        Method[] methods = type.getDeclaredMethods();
        assertTrue(methods.length > 0, "No methods in " + type.getSimpleName());
    }
}
