package com.salvo.winsome.server;

import com.salvo.winsome.RMIClientInterface;
import com.salvo.winsome.RMIServerInterface;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author Salvatore Guastella
 */
public class RMIServer extends RemoteServer implements RMIServerInterface {

    private HashMap<String,WSUser> registeredUser;

    private HashMap<String, ArrayList<WSUser>> allTags;

    public RMIServer(HashMap<String,WSUser> registeredUser, HashMap<String,ArrayList<WSUser>> allTags) {
        this.registeredUser = registeredUser;
        this.allTags = allTags;
    }



    @Override
    public int registerUser(String username, String password, String[] tags) throws RemoteException {

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
            ArrayList<WSUser> tagUser;
            if((tagUser = allTags.get(s)) != null) { // esiste almeno un utente registrato con il tag s

                // aggiungo il nuovo utente alla lista degli utenti associati al tag
                tagUser.add(newUser);

            }else{
                // nuova entry
                tagUser = new ArrayList<>();
                tagUser.add(newUser);
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

        System.out.println("registrato "+client.getUsername());

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
