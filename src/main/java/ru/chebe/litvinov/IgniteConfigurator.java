package ru.chebe.litvinov;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

import java.util.List;

public class IgniteConfigurator {
    private final String localAddress;
    private final List<String> discoveryAddresses;
    private final String workDir;

    public IgniteConfigurator(String localAddress, List<String> discoveryAddresses, String workDir) {
        this.localAddress = localAddress;
        this.discoveryAddresses = discoveryAddresses;
        this.workDir = workDir;
    }

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
