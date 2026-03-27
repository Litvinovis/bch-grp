#!/usr/bin/env python3
"""
Миграция данных BCH-GRP из Apache Ignite 2.x в Apache Ignite 3.x
Читает данные через pyignite (Ignite 2 thin client), записывает через Ignite 3 SQL
"""
import json
import sys
import subprocess
import os

IGNITE2_HOST = '192.168.1.120'
IGNITE2_PORT = 10800
IGNITE3_HOST = '127.0.0.1'
IGNITE3_PORT = 10300

def extract_fields(obj):
    """Извлекает поля из BinaryObject или обычного значения"""
    if obj is None:
        return None

    # pyignite BinaryObject
    if hasattr(obj, 'fields'):
        result = {}
        for field in obj.fields():
            try:
                value = obj.get(field)
                result[field] = extract_fields(value)
            except Exception:
                result[field] = None
        return result

    # Java Map/List через pyignite
    if isinstance(obj, dict):
        return {str(k): extract_fields(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple)):
        return [extract_fields(v) for v in obj]
    if isinstance(obj, (int, float, bool, str)):
        return obj

    # Неизвестный тип — конвертируем в строку
    return str(obj)

def read_from_ignite2():
    from pyignite import Client
    from pyignite.datatypes import BinaryObject

    client = Client(use_ssl=False)
    client.connect(IGNITE2_HOST, IGNITE2_PORT)
    print(f"✅ Подключён к Ignite 2 ({IGNITE2_HOST}:{IGNITE2_PORT})")

    data = {}
    caches = ['players', 'locations', 'items', 'bosses', 'ideas', 'clans']

    for cache_name in caches:
        entries = {}
        try:
            cache = client.get_cache(cache_name)
            # Используем scan без десериализации Java-объектов
            with cache.scan() as cursor:
                for key, value in cursor:
                    try:
                        entries[str(key)] = extract_fields(value)
                    except Exception as e:
                        entries[str(key)] = f"ERROR: {e}"
            data[cache_name] = entries
            print(f"  📦 {cache_name}: {len(entries)} записей")
        except Exception as e:
            print(f"  ⚠️  Ошибка кэша {cache_name}: {e}")
            data[cache_name] = {}

    client.close()
    return data

def main():
    print("=== BCH-GRP Migration: Ignite 2 → Ignite 3 ===\n")

    print("📖 Читаем данные из Ignite 2...")
    data = read_from_ignite2()

    total = sum(len(v) for v in data.values())
    print(f"\n📊 Итого: {total} записей")

    export_file = '/tmp/bchgrp_export.json'
    with open(export_file, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2, default=str)
    print(f"💾 Данные сохранены в {export_file}")
    print(f"Структура: { {k: len(v) for k, v in data.items()} }")

if __name__ == '__main__':
    main()
