package com.salvo.winsome.server;

import com.salvo.winsome.RMIClientInterface;
import com.salvo.winsome.RMIServerInterface;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * @author Salvatore Guastella
 */
public class RMIServer extends RemoteServer implements RMIServerInterface {

    private final ConcurrentHashMap<String,WSUser> registeredUsers;
    private final ConcurrentHashMap<String, ArrayList<String>> allTags;
    private final Lock allTagsWriteLock;

    public RMIServer(ConcurrentHashMap<String,WSUser> registeredUsers,
                     ConcurrentHashMap<String, ArrayList<String>> allTags,
                     Lock allTagsWriteLock) {

        this.registeredUsers = registeredUsers;
        this.allTags = allTags;
        this.allTagsWriteLock = allTagsWriteLock; // listUsers potrebbe accedere in contemporanea
    }



    @Override
    public int registerUser(String username, String password, String[] tags) throws RemoteException {

        if(username == null || password == null || password.equals(""))
            return -1;


        // creazione nuovo utente

        WSUser newUser = new WSUser(username,password,tags);

        // aggiunge il nuovo utente nella HashMap

        if(registeredUsers.putIfAbsent(username,newUser) != null) return -2; // username già utilizzato

        System.out.println("Registrato nuovo utente: \n username: "+username+"\n password: "+password+"\nlista tag: ");

        for (String s : tags){
            ArrayList<String> tagUser;
            allTagsWriteLock.lock();
            if((tagUser = allTags.get(s)) != null) { // esiste almeno un utente registrato con il tag s
                // aggiungo il nuovo utente alla lista degli utenti associati al tag
                tagUser.add(username);


            }else{
                // nuova entry
                tagUser = new ArrayList<>();
                tagUser.add(username);

                allTags.put(s,tagUser);

            }
            allTagsWriteLock.unlock();


            System.out.println(s);
        }

        return 0;
    }

    @Override
    public int registerForCallback(RMIClientInterface client,String username) throws RemoteException {

        WSUser user = registeredUsers.get(username);
        if(user == null)
            return -1;
        user.setRemoteClient(client);

        System.out.println("Registrato stub "+username);

        return 0;
    }

}
