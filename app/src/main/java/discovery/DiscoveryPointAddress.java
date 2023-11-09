package com.udp.discovery;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;

/**
 *
 * @author amoy
 * @version 1.0
 *
 */
public class DiscoveryPointAddress {
    
    public String getSourceIp() { 
		return sourceIp;
	}

	public void setSourceIp(String sourceIp) {
		this.sourceIp = sourceIp;
	}

	public Collection<URL> getAddresses() {
		return addresses;
	}

	public void setAddresses(Collection<URL> addresses) {
		this.addresses = addresses;
	}

	private String sourceIp;
    private Collection<URL> addresses = new HashSet<>(0);
    
    public DiscoveryPointAddress(String sourceIp, Collection<URL> addrs) throws MalformedURLException {
        this.sourceIp = sourceIp;
        this.addresses.addAll(addrs);
    }
    
}
