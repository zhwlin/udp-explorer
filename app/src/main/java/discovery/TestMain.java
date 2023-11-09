package com.udp.discovery;

public class TestMain {

	public static void main(String[] args) { 
		DeviceProbeDiscoverer.discoverWsDevicesAsUrls()
		  .forEach(r -> System.out.println(r));
	}

}
