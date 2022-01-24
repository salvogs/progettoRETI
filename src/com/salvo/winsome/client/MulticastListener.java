package com.salvo.winsome.client;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Salvatore Guastella
 */
public class MulticastListener implements Runnable{

    private InetAddress multicastAddress;
    private int multicastPort;
    private MulticastSocket msocket;
    private volatile AtomicBoolean doNotDisturb;

    public MulticastListener(String multicastAddress, int multicastPort, AtomicBoolean doNotDisturb) throws UnknownHostException {
        this.multicastAddress = InetAddress.getByName(multicastAddress);
        this.multicastPort = multicastPort;
        this.doNotDisturb = doNotDisturb;
    }

    @Override
    public void run() {

        int bufLen = 64;

        try {
            msocket = new MulticastSocket(multicastPort);
            msocket.joinGroup(multicastAddress);

            byte[] buffer = new byte[bufLen];

            DatagramPacket msg = new DatagramPacket(buffer,bufLen);

            msocket.receive(msg); // aspetto che vengano calcolate le ricompense



            if(doNotDisturb.get() == false) System.out.println(new String(msg.getData()).trim());



        } catch (SocketException e) {
            System.out.println("Thread multicastListener terminato");
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }


    }


    public void stop() {
        if(msocket != null) {
            try {
                msocket.leaveGroup(multicastAddress);
                msocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
