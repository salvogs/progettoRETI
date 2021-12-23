package com.salvo.winsome.server;

import com.salvo.winsome.RMIClientInterface;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author Salvatore Guastella
 */
public class WSUser {

    private String username;
    private String password;
    private String[] tags;

    private boolean logged;
    private RMIClientInterface remoteClient;

    private HashSet<String> follower;
    private HashSet<String> followed;


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
        this.remoteClient = null;

        this.follower = new HashSet<>();
        this.followed = new HashSet<>();
    }


    public void setRemoteClient(RMIClientInterface remoteClient) {
        this.remoteClient = remoteClient;
    }

    // il server invia TUTTI i follower
    public void setFollowers(HashSet<String> users) throws RemoteException {
        remoteClient.setFollowers(users);
    }

    // notifica quando l'utente ha un nuovo follower
    public void notifyNewFollow(String user) throws RemoteException {
        remoteClient.newFollow(user);
    }

    public void notifyNewUnfollow(String user) throws RemoteException {
        remoteClient.newUnfollow(user);
    }


    public void setLogged(boolean b) {
        this.logged = b;
    }

    public boolean alreadyLogged(){
        return this.logged;
    };

    public String getUsername() {
        return username;
    }

    public String[] getTags() {
        return tags;
    }


    public HashSet<String> getFollowers() {
        return follower;
    }

    public HashSet<String> getFollowed() {
        return followed;
    }

    public boolean checkPassword(String password){
        return this.password.equals(password) ? true : false;
    }

    public void addFollowed(String username) {
        followed.add(username);
    }

    public void addFollower(String username) {
        follower.add(username);
    }

    public void removeFollowed(String username) {
        followed.remove(username);
    }

    public void removeFollower(String username) {
        follower.remove(username);
    }




}
