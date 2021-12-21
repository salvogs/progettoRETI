package com.salvo.winsome;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * @author Salvatore Guastella
 */
public interface RMIServerInterface extends Remote {


    /**
     * metodo per inserire un nuovo utente
     * @param username
     * @param password
     * @param tags lista di tag
     * @return 0 se registrazione andata a buon fine, -1 se username gia' utilizzato, -2 se password vuota
     * @throws RemoteException
     */
    int registerUser(String username, String password, String[] tags) throws RemoteException;

    int registerForCallback(RMIClientInterface client) throws RemoteException;

    void unregisterForCallback(RMIClientInterface client) throws RemoteException;

}
