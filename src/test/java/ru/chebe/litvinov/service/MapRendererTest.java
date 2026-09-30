package ru.chebe.litvinov.service;

import org.junit.jupiter.api.Test;
import ru.chebe.litvinov.data.Location;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MapRendererTest {

	static { System.setProperty("java.awt.headless", "true"); }

	private static Location loc(String name, boolean pvp, boolean teleport, int danger, String... paths) {
		return Location.builder().name(name).pvp(pvp).teleport(teleport).dangerous(danger).boss(pvp ? "Босс" : null)
			.paths(new ArrayList<>(List.of(paths))).populationById(new ArrayList<>()).populationByName(new ArrayList<>()).build();
	}

	@Test
	void rendersPngFromLocations_includingOnesMissingFromLayout() throws Exception {
		List<Location> locs = List.of(
			loc("дом", false, false, 0, "мейн"),
			loc("мейн", true, true, 10, "дом", "новая-локация"),
			loc("респаун", false, false, 0, "дом"),      // односторонний переход — стрелка
			loc("новая-локация", true, false, 70, "мейн"));

		byte[] png = MapRenderer.render(locs, "мейн", "дом");

		BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
		assertNotNull(img);
		assertTrue(img.getWidth() > 1000);
		assertTrue(img.getHeight() > 1500, "локация вне раскладки добавляет ряд снизу");
	}

	@Test
	void fontWithCyrillicIsBundled() {
		assertTrue(MapRenderer.font(800, 20f).canDisplayUpTo("Карта БЧ-РПГ ёЁ") < 0);
	}
}
