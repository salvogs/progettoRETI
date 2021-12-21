package com.salvo.winsome.server;

import com.salvo.winsome.RMIClientInterface;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Salvatore Guastella
 */
public class WSUser {

    private String username;
    private String password;
    private String[] tags;

    private boolean logged;

    private HashMap<String,String> follower;
    private HashMap<String,String> followed;

    private RMIClientInterface remoteClient;

    /**
     * nuovo utente
     * @param username Il nickname
     * @param password La password
     * @param tags lista dei tag
     */
    public WSUser(String username, String password, String[] tags){
        this.username = username;
        this.password = password;
        this.tags = tags;
        this.logged = false;

        this.follower = new HashMap<>();
        this.followed = new HashMap<>();
    }


    public void setRemoteClient(RMIClientInterface remoteClient) {
        this.remoteClient = remoteClient;
    }



    public int addFollower(String username) {
        if(follower.putIfAbsent(username,username) != null)
            return -1;


        return 0;

    }

    public void addFollowed(String username) {

    }


}
