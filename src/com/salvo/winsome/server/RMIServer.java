package com.salvo.winsome.server;

import com.salvo.winsome.RMIClientInterface;
import com.salvo.winsome.RMIServerInterface;

import java.lang.reflect.Array;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * @author Salvatore Guastella
 */
public class RMIServer extends RemoteServer implements RMIServerInterface {

    private ConcurrentHashMap<String,WSUser> registeredUser;

    private ConcurrentHashMap<String, ArrayList<String>> allTags;
    private Lock allTagsWriteLock;

    public RMIServer(ConcurrentHashMap<String,WSUser> registeredUser,
                     ConcurrentHashMap<String, ArrayList<String>> allTags,
                     Lock allTagsWriteLock) {

        this.registeredUser = registeredUser;
        this.allTags = allTags;
        this.allTagsWriteLock = allTagsWriteLock;
    }



    @Override
    public synchronized int registerUser(String username, String password, String[] tags) throws RemoteException {

        if(username == null || registeredUser.containsKey(username))
            return -1;

        if(password == null || password.equals(""))
            return -2;

        // creazione nuovo utente

        WSUser newUser = new WSUser(username,password,tags);

        // aggiunge il nuovo utente nella HashMap

        registeredUser.put(username,newUser);

        System.out.println("Registrato nuovo utente: \n username: "+username+"\n password: "+password+"\nlista tag: ");

        for (String s : tags){
            ArrayList<String> tagUser;
            if((tagUser = allTags.get(s)) != null) { // esiste almeno un utente registrato con il tag s
                allTagsWriteLock.lock();
                // aggiungo il nuovo utente alla lista degli utenti associati al tag
                tagUser.add(username);

                allTagsWriteLock.unlock();

            }else{
                // nuova entry
                tagUser = new ArrayList<>();
                tagUser.add(username);

                allTags.put(s,tagUser);

            }


            System.out.println(s);
        }

        return 0;
    }

    @Override
    public int registerForCallback(RMIClientInterface client) throws RemoteException {

        String username = client.getUsername();

        WSUser user = registeredUser.get(username);

        if(user == null)
            return -1;

        user.setRemoteClient(client);

        System.out.println("registrata callback "+client.getUsername());

        return 0;
    }



//    @Override
//    public int unregisterForCallback(String username) throws RemoteException {
//
//        WSUser user = registeredUser.get(username);
//
//        if(user == null)
//            return -1;
//
//        user.setRemoteClient(null);
//        user.setLogged(false);
//
//        return 0;
//    }

//    public HashMap<String,String[]> getFollowers()


}
