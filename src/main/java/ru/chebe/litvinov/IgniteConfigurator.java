package ru.chebe.litvinov;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import java.util.List;

/**
 * Конфигуратор Apache Ignite.
 * Создаёт и запускает клиентский Ignite-узел с TCP-обнаружением кластера.
 */
public class IgniteConfigurator {
    private final String localAddress;
    private final List<String> discoveryAddresses;
    private final String workDir;

    /**
     * Создаёт конфигуратор Ignite.
     *
     * @param localAddress        локальный IP-адрес узла
     * @param discoveryAddresses  список адресов для TCP-обнаружения кластера
     * @param workDir             рабочий каталог для хранения данных Ignite
     */
    public IgniteConfigurator(String localAddress, List<String> discoveryAddresses, String workDir) {
        this.localAddress = localAddress;
        this.discoveryAddresses = discoveryAddresses;
        this.workDir = workDir;
    }

    /**
     * Создаёт и запускает клиентский Ignite-узел с настроенным TCP-Discovery.
     *
     * @return запущенный экземпляр Ignite
     */
    public Ignite getIgnite() {
        IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setIgniteInstanceName("bchgrp-client");
        cfg.setClientMode(true);
        cfg.setWorkDirectory(workDir);

        TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
        discoverySpi.setLocalAddress(localAddress);
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(discoveryAddresses);
        discoverySpi.setIpFinder(ipFinder);
        cfg.setDiscoverySpi(discoverySpi);

        return Ignition.start(cfg);
    }
}
