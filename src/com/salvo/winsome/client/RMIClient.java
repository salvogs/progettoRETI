package com.salvo.winsome.client;

import com.salvo.winsome.RMIClientInterface;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Salvatore Guastella
 */
public class RMIClient extends RemoteServer implements RMIClientInterface {


    private ConcurrentHashMap<String,String> followers;
    private String loginUsername;


    public RMIClient(ConcurrentHashMap<String,String> followers, String loginUsername){
        this.followers = followers;
        this.loginUsername = loginUsername;
    }

    @Override
    public void newFollower(String[] users) throws RemoteException {
        for (String user : users)
            this.followers.put(user,user);
    }

    @Override
    public String getUsername() throws RemoteException {
        return this.loginUsername;
    }
}
