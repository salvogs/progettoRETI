package com.salvo.winsome.server;

import com.salvo.winsome.WSInterface;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.HashMap;

/**
 * @author Salvatore Guastella
 */
public class WSServer extends RemoteServer implements WSInterface {

    /**
     * hash table degli utenti registrati
     */
    private HashMap<String,WSUser> registeredUser;



    public WSServer(){
        this.registeredUser = new HashMap<>();
    }

    @Override
    public int registerUser(String username, String password, String[] tags) throws RemoteException {

        // controllo se l'username != null e se esiste un utente con lo stesso username
        if(username == null || registeredUser.containsKey(username))
            return -1;

        // controllo che sia stata passata una password valida
        if(password == null || password.equals(""))
            return -2;

        // creazione nuovo utente

        WSUser newUser = new WSUser(username,password,tags);

        // aggiunge il nuovo utente nella HashMap

        registeredUser.put(username,newUser);

        System.out.println("Registrato nuovo utente: \n username: "+username+"\n password: "+password+"\nlista tag: ");

        for (String s : tags)
            System.out.println(s);

        return 0;
    }
}
