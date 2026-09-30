package ru.chebe.litvinov.service;

import ru.chebe.litvinov.data.Location;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

/**
 * Карта мира, нарисованная из данных игры. Старая map.png была нарисована руками: её нужно
 * было перерисовывать при каждом изменении переходов, и выглядела она как схема в Paint.
 * Теперь переходы, PvP, порталы и опасность берутся из локаций, а на карте отмечены
 * игрок («ты здесь») и мировой босс.
 *
 * Расположение клеток — как на прежней карте, чтобы игрокам не переучиваться; локации,
 * которых нет в раскладке, встают в дополнительный ряд снизу.
 */
public final class MapRenderer {

	private MapRenderer() {}

	/** Колонка и ряд каждой локации — сетка 4×7 прежней карты. */
	static final Map<String, int[]> LAYOUT = Map.ofEntries(
		Map.entry("загадка", new int[]{0, 0}), Map.entry("олимп", new int[]{1, 0}),
		Map.entry("магазин", new int[]{2, 0}), Map.entry("таверна", new int[]{3, 0}),
		Map.entry("кушетка", new int[]{0, 1}), Map.entry("модерская", new int[]{1, 1}),
		Map.entry("рекламный", new int[]{2, 1}),
		Map.entry("хуй-тек", new int[]{0, 2}), Map.entry("старборд", new int[]{1, 2}),
		Map.entry("кринжборд", new int[]{2, 2}),
		Map.entry("респаун", new int[]{0, 3}), Map.entry("дом", new int[]{1, 3}),
		Map.entry("мейн", new int[]{2, 3}), Map.entry("деградач", new int[]{3, 3}),
		Map.entry("качалочка", new int[]{0, 4}), Map.entry("дорогой-дневник", new int[]{1, 4}),
		Map.entry("для-ботов", new int[]{2, 4}), Map.entry("для-флуда", new int[]{3, 4}),
		Map.entry("девочковое", new int[]{0, 5}), Map.entry("чебеграм", new int[]{1, 5}),
		Map.entry("english", new int[]{2, 5}), Map.entry("политота", new int[]{3, 5}),
		Map.entry("nsfw2d", new int[]{0, 6}), Map.entry("nsfw", new int[]{1, 6}),
		Map.entry("nsfw-gay", new int[]{2, 6}), Map.entry("клоунская-братва", new int[]{3, 6})
	);

	private static final int COLS = 4;
	private static final int CELL_W = 330, CELL_H = 190;
	private static final int TILE_W = 262, TILE_H = 128;
	private static final int PAD_X = 70, TOP = 170, LEGEND_H = 190;

	// Палитра: тёмная «ночная» подложка, тайлы-карточки, опасность — полоса слева
	private static final Color BG_TOP = new Color(0x141726), BG_BOTTOM = new Color(0x1E1830);
	private static final Color TILE = new Color(0x262B3F), TILE_SAFE = new Color(0x22352F);
	private static final Color INK = new Color(0xF2EEE6), MUTED = new Color(0x9EA3B8);
	private static final Color EDGE = new Color(0x5B6280), GOLD = new Color(0xF4C95D);
	private static final Color PORTAL = new Color(0x55D6F0), PVP = new Color(0xE5534B), BOSS = new Color(0xB57BFF);

	/**
	 * @param playerLocation где игрок (null — не отмечать)
	 * @param worldBossLocation где мировой босс (null — его нет)
	 */
	public static byte[] render(Collection<Location> locations, String playerLocation, String worldBossLocation) throws IOException {
		Map<String, Location> byName = new LinkedHashMap<>();
		for (Location l : locations) byName.put(l.getName(), l);

		// Позиции: из раскладки, остальные — в дополнительные ряды
		Map<String, int[]> pos = new LinkedHashMap<>();
		int maxRow = 6;
		List<String> extra = new ArrayList<>();
		for (String name : byName.keySet()) {
			if (LAYOUT.containsKey(name)) pos.put(name, LAYOUT.get(name));
			else extra.add(name);
		}
		for (int i = 0; i < extra.size(); i++) pos.put(extra.get(i), new int[]{i % COLS, 7 + i / COLS});
		if (!extra.isEmpty()) maxRow = 7 + (extra.size() - 1) / COLS;

		int width = PAD_X * 2 + CELL_W * (COLS - 1) + TILE_W;
		int height = TOP + CELL_H * maxRow + TILE_H + 60 + LEGEND_H;
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, height, BG_BOTTOM));
		g.fillRect(0, 0, width, height);
		drawStars(g, width, height);

		g.setColor(INK);
		g.setFont(font(800, 54f));
		g.drawString("Карта БЧ-РПГ", PAD_X, 92);
		g.setColor(MUTED);
		g.setFont(font(500, 22f));
		g.drawString("+идти <локация> — бот сам проведёт по кратчайшему пути", PAD_X, 130);

		// Переходы — под карточками
		Set<String> drawn = new HashSet<>();
		for (Location l : byName.values()) {
			int[] a = pos.get(l.getName());
			for (String to : l.getPaths() == null ? List.<String>of() : l.getPaths()) {
				int[] b = pos.get(to);
				if (b == null) continue;
				String key = l.getName().compareTo(to) < 0 ? l.getName() + "|" + to : to + "|" + l.getName();
				if (!drawn.add(key)) continue;
				Location back = byName.get(to);
				boolean twoWay = back != null && back.getPaths() != null && back.getPaths().contains(l.getName());
				drawEdge(g, center(a), center(b), !twoWay);
			}
		}

		for (Location l : byName.values()) {
			drawTile(g, l, pos.get(l.getName()), l.getName().equals(playerLocation), l.getName().equals(worldBossLocation));
		}

		drawLegend(g, width, height - LEGEND_H + 10);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "png", out);
		return out.toByteArray();
	}

	private static Point center(int[] cell) {
		return new Point(PAD_X + cell[0] * CELL_W + TILE_W / 2, TOP + cell[1] * CELL_H + TILE_H / 2);
	}

	private static void drawEdge(Graphics2D g, Point a, Point b, boolean oneWay) {
		g.setColor(new Color(EDGE.getRed(), EDGE.getGreen(), EDGE.getBlue(), 90));
		g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(a, b));
		g.setColor(EDGE);
		g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Float(a, b));
		if (oneWay) {
			// Стрелка у края целевой карточки: переход только в одну сторону
			double ang = Math.atan2(b.y - a.y, b.x - a.x);
			double inset = Math.abs(Math.cos(ang)) > 0.5 ? TILE_W / 2.0 + 10 : TILE_H / 2.0 + 10;
			double tx = b.x - Math.cos(ang) * inset, ty = b.y - Math.sin(ang) * inset;
			Path2D arrow = new Path2D.Double();
			arrow.moveTo(tx, ty);
			arrow.lineTo(tx - Math.cos(ang - 0.5) * 20, ty - Math.sin(ang - 0.5) * 20);
			arrow.lineTo(tx - Math.cos(ang + 0.5) * 20, ty - Math.sin(ang + 0.5) * 20);
			arrow.closePath();
			g.fill(arrow);
		}
	}

	/** Цвет опасности: зелёный (мирно) → жёлтый → оранжевый → красный. */
	static Color dangerColor(int dangerous) {
		if (dangerous <= 0) return new Color(0x4CC38A);
		if (dangerous <= 10) return new Color(0x9BD35A);
		if (dangerous <= 25) return new Color(0xF2C94C);
		if (dangerous <= 35) return new Color(0xF2994A);
		if (dangerous <= 50) return new Color(0xEB6A3C);
		return new Color(0xE0424B);
	}

	private static void drawTile(Graphics2D g, Location l, int[] cell, boolean here, boolean boss) {
		int x = PAD_X + cell[0] * CELL_W, y = TOP + cell[1] * CELL_H;
		RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, TILE_W, TILE_H, 22, 22);

		// Тень
		g.setColor(new Color(0, 0, 0, 90));
		g.fill(new RoundRectangle2D.Float(x + 4, y + 7, TILE_W, TILE_H, 22, 22));

		if (l.isTeleport()) {
			// Портал — светящийся контур
			for (int i = 4; i >= 1; i--) {
				g.setColor(new Color(PORTAL.getRed(), PORTAL.getGreen(), PORTAL.getBlue(), 28 * (5 - i)));
				g.setStroke(new BasicStroke(i * 4f));
				g.draw(shape);
			}
		}
		g.setColor(l.getDangerous() <= 0 && !l.isPvp() ? TILE_SAFE : TILE);
		g.fill(shape);

		// Полоса опасности слева
		Shape clip = g.getClip();
		g.setClip(shape);
		g.setColor(dangerColor(l.getDangerous()));
		g.fillRect(x, y, 12, TILE_H);
		g.setClip(clip);

		if (here) {
			g.setColor(GOLD);
			g.setStroke(new BasicStroke(5f));
			g.draw(shape);
		} else if (l.isTeleport()) {
			g.setColor(PORTAL);
			g.setStroke(new BasicStroke(2.5f));
			g.draw(shape);
		}

		// Название
		g.setColor(INK);
		Font title = font(800, 30f);
		g.setFont(title);
		String name = capitalize(l.getName());
		FontMetrics fm = g.getFontMetrics();
		while (fm.stringWidth(name) > TILE_W - 44 && title.getSize2D() > 18f) {
			title = title.deriveFont(title.getSize2D() - 1f);
			g.setFont(title);
			fm = g.getFontMetrics();
		}
		g.drawString(name, x + 28, y + 46);

		// Подпись: босс или «мирная зона»
		g.setFont(font(500, 17f));
		g.setColor(MUTED);
		String sub = l.getBoss() != null && !l.getBoss().isBlank() ? "босс: " + l.getBoss()
			: (l.isPvp() ? "опасность " + l.getDangerous() + "%" : "мирная зона");
		g.drawString(ellipsize(g, sub, TILE_W - 44), x + 28, y + 74);

		// Бейджи — своей строкой под подписью: справа они наезжали на имя босса
		int bx = x + 28;
		if (l.isPvp()) bx = badgeLeft(g, "PVP", PVP, bx, y + TILE_H - 20);
		if (l.isTeleport()) badgeLeft(g, "ПОРТАЛ", new Color(0x1E8FA8), bx, y + TILE_H - 20);

		// Метки над карточкой
		int tx = x + 16;
		if (here) tx = tag(g, "ТЫ ЗДЕСЬ", GOLD, new Color(0x2A2106), tx, y - 16) + 8;
		if (boss) tag(g, "МИРОВОЙ БОСС", BOSS, Color.WHITE, tx, y - 16);
	}

	/** Бейдж слева направо; возвращает x для следующего. */
	private static int badgeLeft(Graphics2D g, String text, Color bg, int left, int baseline) {
		g.setFont(font(800, 14f));
		int w = g.getFontMetrics().stringWidth(text) + 18;
		badge(g, text, bg, left + w, baseline);
		return left + w + 8;
	}

	/** Бейдж справа налево; возвращает x для следующего. */
	private static int badge(Graphics2D g, String text, Color bg, int right, int baseline) {
		g.setFont(font(800, 14f));
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(text) + 18, h = 24;
		int x = right - w;
		g.setColor(bg);
		g.fill(new RoundRectangle2D.Float(x, baseline - 17, w, h, h, h));
		g.setColor(Color.WHITE);
		g.drawString(text, x + 9, baseline);
		return x - 6;
	}

	/** Ярлык над карточкой; возвращает x правого края. */
	private static int tag(Graphics2D g, String text, Color bg, Color fg, int x, int baseline) {
		g.setFont(font(800, 15f));
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(text) + 22, h = 28;
		g.setColor(bg);
		g.fill(new RoundRectangle2D.Float(x, baseline - 20, w, h, h, h));
		g.setColor(fg);
		g.drawString(text, x + 11, baseline);
		return x + w;
	}

	private static void drawLegend(Graphics2D g, int width, int y) {
		g.setColor(new Color(255, 255, 255, 18));
		g.fill(new RoundRectangle2D.Float(PAD_X - 20, y, width - 2 * PAD_X + 40, LEGEND_H - 50, 26, 26));
		g.setFont(font(700, 20f));
		g.setColor(MUTED);
		g.drawString("Опасность", PAD_X + 6, y + 44);
		int[] levels = {0, 10, 25, 35, 50, 70};
		String[] labels = {"0", "10", "25", "35", "50", "70+"};
		for (int i = 0; i < levels.length; i++) {
			int x = PAD_X + 150 + i * 70;
			g.setColor(dangerColor(levels[i]));
			g.fill(new RoundRectangle2D.Float(x, y + 26, 56, 20, 10, 10));
			g.setColor(MUTED);
			g.setFont(font(500, 16f));
			g.drawString(labels[i], x + 14, y + 68);
		}
		int x = PAD_X + 6, row = y + 110;
		g.setFont(font(500, 18f));
		x = legendBadge(g, "PVP", PVP, x, row, "можно напасть на игрока");
		x = legendBadge(g, "ПОРТАЛ", new Color(0x1E8FA8), x + 36, row, "телепорт с токеном");
		x = legendBadge(g, "ТЫ ЗДЕСЬ", new Color(0xC99A2E), x + 36, row, "");
		legendBadge(g, "МИРОВОЙ БОСС", BOSS, x + 20, row, "");
	}

	private static int legendBadge(Graphics2D g, String badge, Color bg, int x, int baseline, String caption) {
		int right = badge(g, badge, bg, x + textWidth(g, badge, font(800, 14f)) + 18, baseline) + 6;
		int bw = textWidth(g, badge, font(800, 14f)) + 18;
		g.setFont(font(500, 18f));
		g.setColor(MUTED);
		if (!caption.isEmpty()) g.drawString("— " + caption, x + bw + 10, baseline);
		return x + bw + 10 + (caption.isEmpty() ? 0 : g.getFontMetrics().stringWidth("— " + caption));
	}

	private static int textWidth(Graphics2D g, String s, Font f) {
		return g.getFontMetrics(f).stringWidth(s);
	}

	/** Редкие «звёзды» на фоне — детерминированно, чтобы карта не мерцала между вызовами. */
	private static void drawStars(Graphics2D g, int w, int h) {
		Random r = new Random(42);
		for (int i = 0; i < 140; i++) {
			int a = 25 + r.nextInt(70);
			g.setColor(new Color(255, 255, 255, a));
			int s = r.nextInt(3) + 1;
			g.fillOval(r.nextInt(w), r.nextInt(h), s, s);
		}
	}

	private static String ellipsize(Graphics2D g, String s, int max) {
		FontMetrics fm = g.getFontMetrics();
		if (fm.stringWidth(s) <= max) return s;
		while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
		return s + "…";
	}

	private static String capitalize(String s) {
		if (s == null || s.isEmpty()) return "";
		if (s.startsWith("nsfw") || s.equals("english")) return s.toUpperCase(Locale.ROOT).replace("ENGLISH", "English");
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	// ── шрифты: Manrope из ресурсов (кириллица; на сервере нет шрифтов кроме DejaVu) ──

	private static final Map<Integer, Font> FONTS = new HashMap<>();

	static synchronized Font font(int weight, float size) {
		Font base = FONTS.computeIfAbsent(weight, w -> {
			try (InputStream in = MapRenderer.class.getResourceAsStream("/fonts/Manrope-" + w + ".ttf")) {
				if (in != null) return Font.createFont(Font.TRUETYPE_FONT, in);
			} catch (Exception ignored) {
				// нет шрифта — ниже запасной
			}
			return new Font("DejaVu Sans", w >= 700 ? Font.BOLD : Font.PLAIN, 12);
		});
		return base.deriveFont(size);
	}
}
