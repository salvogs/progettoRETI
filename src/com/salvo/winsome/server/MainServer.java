package com.salvo.winsome.server;


import com.salvo.winsome.RMIServerInterface;

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

        server.start();


        }
    }





