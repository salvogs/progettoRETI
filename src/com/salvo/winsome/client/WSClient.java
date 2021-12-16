package com.salvo.winsome.client;

import com.salvo.winsome.WSInterface;

import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;


/**
 * @author Salvatore Guastella
 */
public class WSClient {

    public int register(String username, String password, String[] tags) {
        int port = 6789;

        String SERVICE_NAME = "REGISTRATION-SERVICE";


        try {
            // individuo il registry sulla porta args[0]
            Registry r = LocateRegistry.getRegistry("localhost",port);

            // copia serializzata dello stub esposto dal server remoto
            Remote remoteObject = r.lookup(SERVICE_NAME);

            WSInterface remoteServer = (WSInterface) remoteObject;


            remoteServer.registerUser(username, password, tags);

            return 0;
        } catch (RemoteException e) {
                e.printStackTrace();
        } catch (NotBoundException e) {
                e.printStackTrace();
        }

        return 0;
    }

    public int login(String username, String password) {
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
