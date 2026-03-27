package ru.chebe.litvinov;

import org.apache.ignite.client.IgniteClient;

/**
 * Конфигуратор Apache Ignite 3 thin client.
 * Создаёт и возвращает подключённый IgniteClient по указанному адресу.
 */
public class Ignite3Configurator {

    private final String address;

    /**
     * Создаёт конфигуратор Ignite 3.
     *
     * @param address адрес Ignite 3 узла в формате host:port (например, "127.0.0.1:10300")
     */
    public Ignite3Configurator(String address) {
        this.address = address;
    }

    /**
     * Создаёт и возвращает подключённый Ignite 3 thin client.
     *
     * @return подключённый экземпляр IgniteClient
     */
    public IgniteClient getClient() {
        return IgniteClient.builder()
                .addresses(address)
                .build();
    }
}
