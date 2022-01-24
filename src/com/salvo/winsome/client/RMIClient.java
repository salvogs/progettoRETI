package com.salvo.winsome.client;

import com.salvo.winsome.RMIClientInterface;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Salvatore Guastella
 */
public class RMIClient extends RemoteServer implements RMIClientInterface {


    private HashMap<String,String[]> followers;
    private volatile AtomicBoolean doNotDisturb;

    public RMIClient(HashMap<String,String[]> followers, AtomicBoolean doNotDisturb){
        this.followers = followers;
        this.doNotDisturb = doNotDisturb;
    }


    @Override
    public void newFollow(String user,String[] tags) throws RemoteException {
        followers.put(user,tags);
        if(doNotDisturb.get() == false) System.out.println(user+" ti segue");
    }
    @Override
    public void newUnfollow(String user) throws RemoteException {
        followers.remove(user);
        if(doNotDisturb.get() == false) System.out.println(user+" ha smesso di seguirti");
    }


}
