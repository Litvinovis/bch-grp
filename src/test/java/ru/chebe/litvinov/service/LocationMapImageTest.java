package ru.chebe.litvinov.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocationMapImageTest {

	@Test
	void mapImage_isReadFromClasspath_notFromSourceTree() {
		// Было: new File("src/main/resources/map.png") — в проде (jar в /opt/BCHGRP) такого файла нет
		byte[] png = LocationManager.mapImage();
		assertNotNull(png);
		assertTrue(png.length > 1000);
		assertEquals((byte) 0x89, png[0], "PNG-сигнатура");
		assertEquals('P', png[1]);
	}
}
