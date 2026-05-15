package ru.chebe.litvinov.util;

/**
 * Утилита для валидации пользовательского ввода.
 * Используется в командах, принимающих числовые суммы и строковые имена.
 */
public final class InputValidator {

    private InputValidator() {}

    /**
     * Проверяет, что строка представляет число в диапазоне [min, max].
     *
     * @param s   строка от пользователя
     * @param min минимальное допустимое значение
     * @param max максимальное допустимое значение
     * @return распарсенное значение
     * @throws IllegalArgumentException если строка не является числом или вне диапазона
     */
    public static int validateAmount(String s, int min, int max) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Укажите сумму.");
        }
        int value;
        try {
            value = Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректное число: " + s);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException("Сумма должна быть от " + min + " до " + max + ".");
        }
        return value;
    }

    /**
     * Проверяет, что строка не пустая и не длиннее maxLen символов.
     *
     * @param s      строка от пользователя
     * @param maxLen максимальная длина
     * @return исходная строка (trimmed)
     * @throws IllegalArgumentException если строка пустая или слишком длинная
     */
    public static String validateName(String s, int maxLen) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Имя не может быть пустым.");
        }
        String trimmed = s.trim();
        if (trimmed.length() > maxLen) {
            throw new IllegalArgumentException("Слишком длинное имя (максимум " + maxLen + " символов).");
        }
        return trimmed;
    }
}
