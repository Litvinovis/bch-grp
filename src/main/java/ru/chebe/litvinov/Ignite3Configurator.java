package ru.chebe.litvinov;

import org.apache.ignite.client.IgniteClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Менеджер подключения Apache Ignite 3 thin client.
 *
 * <p>Хранит единственный экземпляр {@link IgniteClient} и предоставляет метод
 * {@link #reconnect()} для создания нового соединения при потере старого.
 * Репозитории, которые получают клиент через {@link #getClient()}, автоматически
 * начнут использовать новый клиент после переподключения.
 */
public class Ignite3Configurator {

    private static final Logger log = LoggerFactory.getLogger(Ignite3Configurator.class);

    private final String address;
    private volatile IgniteClient client;

    /**
     * Создаёт конфигуратор и сразу устанавливает соединение с Ignite 3.
     *
     * @param address адрес Ignite 3 узла в формате host:port (например, "127.0.0.1:10300")
     */
    public Ignite3Configurator(String address) {
        this.address = address;
        this.client = buildClient();
    }

    /**
     * Возвращает текущий подключённый Ignite 3 thin client.
     *
     * @return актуальный экземпляр {@link IgniteClient}
     */
    public IgniteClient getClient() {
        return client;
    }

    /**
     * Переподключается к Ignite 3: закрывает старый клиент и создаёт новый.
     * Метод потокобезопасен — защищён монитором объекта.
     *
     * @return {@code true} если переподключение прошло успешно
     */
    public synchronized boolean reconnect() {
        log.info("Ignite3Configurator: переподключение к {}...", address);
        try {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ex) {
                    log.warn("Ignite3Configurator: ошибка закрытия старого клиента: {}", ex.getMessage());
                }
            }
            client = buildClient();
            log.info("Ignite3Configurator: переподключение успешно к {}", address);
            return true;
        } catch (Exception e) {
            log.error("Ignite3Configurator: переподключение не удалось: {}", e.getMessage(), e);
            return false;
        }
    }

    private IgniteClient buildClient() {
        return IgniteClient.builder()
                .addresses(address)
                .build();
    }
}
