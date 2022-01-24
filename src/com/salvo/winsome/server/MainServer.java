package com.salvo.winsome.server;


import com.salvo.winsome.Utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Scanner;


/**
 * @author Salvatore Guastella
 */
public class MainServer {


    public static void main(String[] args) {


        if(args.length != 1) {
            System.err.println("usage: java MainServer <config path>");
            System.exit(-1);
        }

        HashMap<String,String> result = Utils.parsingConfigFile(args[0]);

        System.out.println(result);

        System.out.println("Avvio server...");

        int tcpPort = getPortParameter(result,"tcpport");
        int udpPort = getPortParameter(result,"udpport");
        int multicastPort = getPortParameter(result,"mcastport");
        InetAddress multicastAddress = null;
        try {
            multicastAddress = InetAddress.getByName(getStringParameter(result,"multicast"));
            if(!multicastAddress.isMulticastAddress())
                throw new UnknownHostException();
        } catch (UnknownHostException e) {
            System.out.println ("indirizzo di multicast non valido");
            System.exit(-1);
        }
        int regPort = getPortParameter(result,"rmiregport");
        String regServiceName = getStringParameter(result,"rmiservicename");






        WSServer server = new WSServer(tcpPort,udpPort,multicastPort,multicastAddress,regPort,regServiceName);
        Thread serverThread = new Thread(server);
        serverThread.start();

        Scanner s = new Scanner(System.in);
        while(true) {
            System.out.println("Digita 'stop' per terminare l'esecuzione\n");
            String in = s.nextLine();
            if(in.equalsIgnoreCase("stop")) {
                System.out.println("Terminazione server...");
                server.stop();
                try {
                    serverThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("Server terminato");
                System.exit(0);
            }
        }



    }


    private static int getPortParameter(HashMap<String,String> result, String portName) {

        String port = result.get(portName);
        int portnum = 0;

        try {
            if (port != null) {

                 portnum = Integer.parseInt(port);

                if (portnum < 1024 || portnum > 65535) {
                    System.err.println("porta \"" + portName + "\"non valida");
                    System.exit(-1);
                }
            } else throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.err.println("parsing \""+portName+"\" fallito");
            System.exit(-1);
        }

        return portnum;
    }

    private static String getStringParameter(HashMap<String,String> result, String name) {

        String parameter = result.get(name);

        if(parameter == null) {
            System.err.println("parsing \""+name+"\" fallito");
            System.exit(-1);
        }

        return parameter;
    }



}





