package com.salvo.winsome.server;


import com.salvo.winsome.ConfigParser;

import java.util.HashMap;
import java.util.Scanner;


/**
 * @author Salvatore Guastella
 */
public class MainServer {


    public static void main(String[] args) {


        if(args.length < 1 ) {
            System.err.println("usage: java MainServer <config_path> [n_workers]");
            System.exit(-1);
        }

        HashMap<String,String> result = ConfigParser.parseConfigFile(args[0]);

        System.out.println(result); // stampo i campi letti

        int nWorkers = 10;

        // controllo se è stato passato il parametro n_workers
        if(args.length >= 2) {
            try {
                int n = Integer.parseInt(args[1]);
                if (n > 0) nWorkers = n;
            } catch (NumberFormatException e) {
                System.err.println("usage: java MainServer <config path> [n_workers]");
                System.exit(-1);
            }
        }

        System.out.println("n_workers: "+nWorkers);

        System.out.println("Avvio server...");

        int tcpPort = ConfigParser.getPortParameter(result,"tcpport");
        int multicastPort = ConfigParser.getPortParameter(result,"mcastport");
        String multicastAddress = ConfigParser.getStringParameter(result,"multicast");
        int regPort = ConfigParser.getPortParameter(result,"rmiregport");
        String regServiceName = ConfigParser.getStringParameter(result,"rmiservicename");
        double authorPercentage =  ConfigParser.getDoubleParameter(result,"authorpercentage");
        int rewardsPeriod = ConfigParser.getPositiveIntegerParameter(result,"rewardsperiod");
        int backupPeriod = ConfigParser.getPositiveIntegerParameter(result,"backupperiod");



        // creo e avvio il thread dispatcher

        WSServer server = new WSServer(nWorkers,tcpPort,multicastPort,multicastAddress,regPort,
                                       regServiceName,authorPercentage,rewardsPeriod,backupPeriod);

        Thread serverThread = new Thread(server);
        serverThread.start();


        // in attesa che l'utente fermi il server
        Scanner s = new Scanner(System.in);
        while(true) {
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




}





