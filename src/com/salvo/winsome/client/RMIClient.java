package com.salvo.winsome.client;

import com.salvo.winsome.RMIClientInterface;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Salvatore Guastella
 */
public class RMIClient extends RemoteServer implements RMIClientInterface {


    private HashSet<String> followers;
    private String loginUsername;


    public RMIClient(HashSet<String> followers, String loginUsername){
        this.followers = followers;
        this.loginUsername = loginUsername;
    }

    @Override
    public void setFollowers(HashSet<String> users) throws RemoteException {
        followers = users;
    }
    @Override
    public void newFollow(String user) throws RemoteException {
        followers.add(user);
    }
    @Override
    public void newUnfollow(String user) throws RemoteException {
        followers.remove(user);
    }

    @Override
    public String getUsername() throws RemoteException {
        return this.loginUsername;
    }
}
