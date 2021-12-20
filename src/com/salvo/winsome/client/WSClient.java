package com.salvo.winsome.client;

import com.salvo.winsome.RMIClientInterface;
import com.salvo.winsome.RMIServerInterface;
import com.salvo.winsome.WSUser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.SocketHandler;


/**
 * @author Salvatore Guastella
 */
public class WSClient {

    private String loginName;
    private ConcurrentHashMap<String,String> followers;
    private RMIClient clientCallback;

    private RMIServerInterface remoteServer;

    private SocketChannel socket;



    public WSClient(String registryAddr, int registryPort, String serviceName) {

        this.loginName = null;
        this.followers = new ConcurrentHashMap<String, String>();


        try {
            // individuo il registry sulla porta args[0]
            Registry r = LocateRegistry.getRegistry(registryAddr, registryPort);

            // copia serializzata dello stub esposto dal server remoto
            Remote remoteObject = r.lookup(serviceName);

            this.remoteServer = (RMIServerInterface) remoteObject;

            // tcp connection

            try {
                this.socket = SocketChannel.open(new InetSocketAddress("localhost", 9000));
            } catch (IOException e) {
                System.err.println("ERRORE: Impossibile connettersi con il server");
                System.exit(-1);
            }

            System.out.println("Connessione stabilita con il server - localhost:9000");


        } catch (NotBoundException e) {
            System.err.println("ERRORE: Nessuna corrispondenza sul registry per "+serviceName);
            System.exit(-1);
        } catch (RemoteException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

        public int register(String username, String password, String[] tags) {


        try {
            remoteServer.registerUser(username, password, tags);
        } catch (RemoteException e) {
            e.printStackTrace();
        }


        return 0;
    }

    public int login(String username, String password) {

        // implement login TODO


        this.clientCallback = new RMIClient(followers,"SUGO USER");

        // esporto stub clientCallback per permettere al server di notificare nuovi follow/unfollow

        try {
            RMIClientInterface stub = (RMIClientInterface) UnicastRemoteObject.exportObject(clientCallback,0);

            // mi registro per ricevere notifiche

            remoteServer.registerForCallback(stub);

        } catch (RemoteException e) {
            e.printStackTrace();
        }



        return 0;
    }

    public int logout() {
        return 0;
    }

    public int listFollowers() {
        return 0;
    }

    public int listFollowing() {
        return 0;
    }

    public int followUser() {
        return 0;
    }

    public int unfollowUser() {
        return 0;
    }

    public int viewBlog() {
        return 0;
    }

    public int createPost() {
        return 0;
    }















}
