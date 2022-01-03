package com.salvo.winsome.client;

import com.salvo.winsome.RMIClientInterface;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Salvatore Guastella
 */
public class RMIClient extends RemoteServer implements RMIClientInterface {


    private HashMap<String,String[]> followers;
    private String loginUsername;


    public RMIClient(HashMap<String,String[]> followers, String loginUsername){
        this.followers = followers;
        this.loginUsername = loginUsername;
    }


    @Override
    public void newFollow(String user,String[] tags) throws RemoteException {
        followers.put(user,tags);
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
