package com.udp.discovery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
 
/**
 *
 * Support IPv4 WSSD webservice addression discovery.
 * 
 * @author amoy
 * @version 1.0
 *
 */
public class DeviceProbeDiscoverer {
    
    private static final Logger LOG = LoggerFactory.getLogger("WS-Discover");
    private static final int WS_DISCOVERY_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(20);
    private static final int WS_DISCOVERY_PORT = 3702;
    private static final String WS_DISCOVERY_ADDRESS_IPv4 = "239.255.255.250";
    private static final String WS_DISCOVERY_ADDRESS_IPv6 = "[FF02::C]";
    private static final String WS_DISCOVERY_PROBE_MESSAGE = "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" xmlns:tns=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\"><soap:Header><wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</wsa:Action><wsa:MessageID>urn:uuid:%s</wsa:MessageID><wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To></soap:Header><soap:Body><tns:Probe/></soap:Body></soap:Envelope>";
    
    public static Collection<URL> discoverWsDevicesAsUrls() {
      return discoverWsDevicesAsUrls("", "");
    } 
    /**
     *  Discovery the WS service list and find the matched ones by regular expression.
     *  
     * @param regexpProtocol the checking regular expression of protocol
     * @param regexpURLPath the checking regular expression of URL path
     * @return
     */
    public static Collection<URL> discoverWsDevicesAsUrls(String regexpProtocol, String regexpURLPath) {
      Set<URL> urls = new HashSet<>();
      boolean ok = true;
      Optional<DiscoveryPointAddress> addrs = discoverWsDevices();
      if(addrs.isPresent()) {
          DiscoveryPointAddress foundItem =  addrs.get();
          for(URL url : foundItem.getAddresses() ) {
              ok = isMatchRegExpr(url.getProtocol(), regexpProtocol);
              ok = ok && isMatchRegExpr(url.getPath(), regexpURLPath);
              if (ok)
                urls.add(url); 
          }  
          
      }
      return  urls; 
    }
    
    /**
     * Discovery the WS service point {@link DiscoveryPointAddress} list and find the matched ones by regular expression.
     *  
     * @param ip the host ip of webservice url
     * @return the matched {@link DiscoveryPointAddress} instant.
     * @throws MalformedURLException 
     */
    public static Optional<DiscoveryPointAddress> discoverWsDevicesPoints(String ip)  {
        Optional<DiscoveryPointAddress> addrs = discoverWsDevices();
        if(addrs.isPresent()) {
            DiscoveryPointAddress foundItem =  addrs.get();
            Collection<URL> foundUrl = new ArrayList<>();
            for(URL url : foundItem.getAddresses() ) {
                if (url.getHost().equals(ip)) {
                    foundUrl.add(url);
                }
            }  
            if(!foundUrl.isEmpty()) {
                try {
                    return  Optional.of(new DiscoveryPointAddress(foundItem.getSourceIp(), foundUrl));
                }catch(MalformedURLException e) {
                    LOG.warn("Discovery WS address list is failed, error: {}", e.getMessage());
                }
            }
        }
        return Optional.empty(); 
    }
    
    /**
     * Discovery the WS service point {@link DiscoveryPointAddress} list and find the matched ones by regular expression.
     *  
     * @param regexpProtocol the checking regular expression of protocol
     * @param regexpURLPath the checking regular expression of URL path
     * @return the matched {@link DiscoveryPointAddress} instant.
     * @throws MalformedURLException 
     */
    public static Optional<DiscoveryPointAddress> discoverWsDevicesPoints(String regexpProtocol, String regexpURLPath)  {
        Optional<DiscoveryPointAddress> addrs = discoverWsDevices();
        if(addrs.isPresent()) {
            DiscoveryPointAddress foundItem =  addrs.get();
            Collection<URL> foundUrl = new ArrayList<>();
            boolean ok = true;
            for(URL url : foundItem.getAddresses() ) {
                ok = isMatchRegExpr(url.getProtocol(), regexpProtocol);
                ok = ok && isMatchRegExpr(url.getPath(), regexpURLPath);
                if (ok) {
                    foundUrl.add(url);
                }
            }  
            if(!foundUrl.isEmpty()) {
                try {
                    return  Optional.of(new DiscoveryPointAddress(foundItem.getSourceIp(), foundUrl));
                }catch(MalformedURLException e) {
                    LOG.warn("Discovery WS address list is failed, error: {}", e.getMessage());
                }
            }
        }
        return Optional.empty(); 
    }
 
    private static boolean isMatchRegExpr(String checkingData, String regExpression) {
        if(checkingData == null || checkingData.length() == 0) {
            return false;
        }
        if(regExpression == null || regExpression.length() == 0) {
            return true;
        }
        return checkingData.matches(regExpression);
    }
    
    private static Optional<DiscoveryPointAddress> discoverWsDevices() {
        Collection<InetAddress> ciadr = getLocalHostRealIpv4Address();
        return  ciadr.parallelStream()
                         .map(ip -> {
                            try {
                               LOG.debug("Discovery  by {}", ip);
                               Collection<String> found = DeviceProbeDiscoverer.discoverWsDevicesBySourceIP(ip);
                               if(!found.isEmpty()) { 
                                   Collection<URL> addrs = new HashSet<>(found.size());
                                   for (String item : found) {
                                     addrs.add(new URL(item));
                                   }
                                   return new DiscoveryPointAddress(ip.getHostAddress(), addrs);
                               }
                            } catch (Exception e) {
                                LOG.debug("Faild to process discovery WS, error: {}", e.getMessage());
                            }
                            return null;
                         }) 
                         .filter(Objects::nonNull)
                         .findFirst();
    }
    
    /**
     *  Discovery Soap Webservice address list by specified ip.
     *  
     * @param ip the source ip
     * @return the found address list
     * @throws Exception
     */
    private static Collection<String> discoverWsDevicesBySourceIP(InetAddress ip) throws Exception {
        final Collection<String> allUrls = new HashSet<>();
        int port = new SecureRandom().nextInt(20000) + 40000;
        final CountDownLatch ready = new CountDownLatch(1);
        try(final DatagramSocket server = new DatagramSocket(port, ip);) {
            // Receive data
            ExecutorService es = Executors.newSingleThreadExecutor();
            es.submit(() -> {
                try {
                    ready.await();
                } catch (InterruptedException e1) { 
                    Thread.currentThread().interrupt();
                }
                byte[] buf = new byte[4096];
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    while(!es.isTerminated()) {
                        server.receive(packet);
                        Collection<String> found = parseSoapResponseForUrls(Arrays.copyOf(packet.getData(), packet.getLength()));
                        allUrls.addAll(found);
                    }
                } catch (Exception e) {
                    //Ignore!
                }
            });  
            
            // Send UDP message
            server.setSoTimeout(WS_DISCOVERY_TIMEOUT);
            String msg = getSendMessage();
            InetAddress targetAddr = InetAddress.getByName(WS_DISCOVERY_ADDRESS_IPv4);
            server.send(new DatagramPacket(msg.getBytes(), msg.length(), targetAddr, WS_DISCOVERY_PORT));
            ready.countDown();
            
            // Stop the receiving thread.
            es.shutdown();
            es.awaitTermination(WS_DISCOVERY_TIMEOUT, TimeUnit.MILLISECONDS);
        }

       LOG.debug("Source ip {} -> found {} items.", ip, allUrls.size());
       return allUrls;
    }
    
    private static String getSendMessage() {
        String uuid = UUID.randomUUID().toString();
        return String.format(WS_DISCOVERY_PROBE_MESSAGE, uuid);
    }
    
    
    private static Collection<InetAddress> getLocalHostRealIpv4Address()  {
        Collection<InetAddress> candiates = new HashSet<>();
        try {
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if(interfaces != null) {
               while (interfaces.hasMoreElements()) {
                  NetworkInterface anInterface = interfaces.nextElement();
                    if( ! anInterface.isLoopback() ) {
                         final List<InterfaceAddress> interfaceAddresses = anInterface.getInterfaceAddresses();
                         for (InterfaceAddress address : interfaceAddresses) {
                            InetAddress iadd = address.getAddress();
                             if(!iadd.isLoopbackAddress() && iadd instanceof Inet4Address && iadd.isSiteLocalAddress()) {
                                 candiates.add(iadd);
                            }
                         }
                      }
               }  
           } 
            
           if(candiates.isEmpty()) {
               candiates.add(InetAddress.getLocalHost());
           }
        } catch (Exception e) {
            //Ignore!
        }  
        return candiates;
    }
    
    
    private static Collection<Node> getNodeMatching(Node body, String regexp) {
      Collection<Node> nodes = new ArrayList<>();
      if (body.getNodeName().matches(regexp))
        nodes.add(body); 
      if (body.getChildNodes().getLength() == 0)
        return nodes; 
      NodeList returnList = body.getChildNodes();
      for (int k = 0; k < returnList.getLength(); k++) {
        Node node = returnList.item(k);
        nodes.addAll(getNodeMatching(node, regexp));
      } 
      return nodes;
    }
    
    private static Collection<String> parseSoapResponseForUrls(byte[] data) throws SOAPException, IOException {
      Collection<String> urls = new ArrayList<>();
      MessageFactory factory = MessageFactory.newInstance("SOAP 1.2 Protocol");
      MimeHeaders headers = new MimeHeaders();
      headers.addHeader("Content-type", "application/soap+xml");
      SOAPMessage message = factory.createMessage(headers, new ByteArrayInputStream(data));
      SOAPBody body = message.getSOAPBody();
      for (Node node : getNodeMatching(body, ".*:XAddrs")) {
        if (node.getTextContent().length() > 0)
          urls.addAll(Arrays.asList(node.getTextContent().split(" "))); 
      } 
      return urls;
    }
}



