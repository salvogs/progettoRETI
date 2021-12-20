package com.salvo.winsome;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author Salvatore Guastella
 */
public interface RMIClientInterface extends Remote {

    void newFollower(String[] users) throws RemoteException;

    String getUsername() throws RemoteException;
}
