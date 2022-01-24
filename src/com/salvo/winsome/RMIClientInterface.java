package com.salvo.winsome;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author Salvatore Guastella
 */
public interface RMIClientInterface extends Remote {

    void newFollow(String user, String[] tags) throws RemoteException;

    void newUnfollow(String user) throws RemoteException;

}
