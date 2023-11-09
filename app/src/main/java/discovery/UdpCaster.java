package com.udp.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UdpCaster {
	static  int multicastPort = 1900;
    
	public static void main(String[] args) {
		
		if(args.length > 0 ) {
			multicastPort = Integer.parseInt(args[0]);
		}
		
		boolean isSender = true;
		if(args.length > 1 ) {
			isSender = !isSender;
		}
		
		CountDownLatch cl = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(10);
		new Thread(() -> {
			UdpCaster.receiveBroadcast(finished, cl);
		}).start();
		
		if(isSender) {
			while(true) {
				for (int i = 1; i <= 10; i++) {
					final int order = i;
					new Thread(() -> {
						try {
							cl.await();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						UdpCaster.broadcast(order);
						finished.countDown();
					}).start();
				}
				
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		
	}

	
    private static void receiveBroadcast(CountDownLatch finished, CountDownLatch cl) {
    	Logger log = LoggerFactory.getLogger("UdpCaster-receiveBroadcast");
    	String multicastAddress = "239.255.255.250";
        int bufferSize = 1024;

        try(MulticastSocket socket = new MulticastSocket(multicastPort)) {
            InetAddress group = InetAddress.getByName(multicastAddress);
            log.info("Join group - {}", group);
            // 加入组播组
            socket.joinGroup(group);

            byte[] buffer = new byte[bufferSize];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            
            // 接收广播消息
            cl.countDown(); 
            AtomicLong c = new AtomicLong(0);
            while(true) {
            	log.info("Receive pack....");
            	socket.receive(packet);
	            String message = new String(packet.getData(), 0, packet.getLength());
	            log.info(c.incrementAndGet() + " - Received broadcast message: " + message);
            }
//            socket.leaveGroup(group);
//            log.info("Leave group {}", group);
        } catch (IOException e) {
        	log.error("Io error!", e);
        }
	}
    
	private static void broadcast(int i) {
		Logger log = LoggerFactory.getLogger("UdpCaster-broadcast");
		String multicastAddress = "239.255.255.250";
        
        String registrationMessage = i + " - 我是地址 http://10.191.10.2/test";

        try {
            try (MulticastSocket socket = new MulticastSocket()) {
            	InetAddress group = InetAddress.getByName(multicastAddress);
				// 设置TTL（生存时间）为1，以限制组播消息的传播范围
				socket.setTimeToLive(1);

				byte[] messageBytes = registrationMessage.getBytes();
				DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length, group, multicastPort);

				// 发送注册消息
//				log.info("Send msg: {}", registrationMessage);
				socket.send(packet);
				log.info("Registration message sent.");
				  try {
		            	Thread.sleep(1000);
		            } catch (InterruptedException e) {
		            	e.printStackTrace();
		            }
			}
        } catch (IOException e) {
        	log.error("Io error!", e);
        }
	}
}
