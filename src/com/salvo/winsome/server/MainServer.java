package com.salvo.winsome.server;


import com.salvo.winsome.WSInterface;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * @author Salvatore Guastella
 */
public class MainServer {


    public static void main(String[] args) {
        WSServer server = new WSServer();

        // esporto stub per far registrare nuovi utenti

//        int port = Integer.parseInt(args[0]);
        int port = 6789;
        String SERVICE_NAME = "REGISTRATION-SERVICE";
        {
            try {
                WSInterface stub = (WSInterface) UnicastRemoteObject.exportObject(server,0);

                // creazione di un registry sulla porta args[0]

                Registry r = LocateRegistry.createRegistry(port);

                // pubblicazione dello stub sul registry

                r.rebind(SERVICE_NAME,stub);


            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }





}
