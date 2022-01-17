package com.salvo.winsome.server;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.salvo.winsome.RMIClientInterface;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import lombok.*;

/**
 * @author Salvatore Guastella
 */
//@NoArgsConstructor
public class WSUser implements Serializable {

    @Getter @Setter private String username;
    @Getter @Setter private String password;
    @Getter @Setter private String[] tags;
    @JsonIgnore private boolean logged;
    @JsonIgnore private RMIClientInterface remoteClient;

    @JsonIgnore private int sessionId;


    @Getter @Setter private HashSet<String> follower;
    @Getter @Setter private HashSet<String> followed;


    @Getter @Setter private HashSet<Integer> blog; //salvo gli id dei post creati dall'utente

    @Getter @Setter private double wallet;


    @JsonIgnore @Getter private ArrayList<Transaction> transactions;


    public WSUser(){
        logged = false;
        remoteClient = null;
        sessionId = -1;
    }

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

        this.sessionId = -1;

        this.remoteClient = null;

        this.follower = new HashSet<>();
        this.followed = new HashSet<>();

        this.blog = new HashSet<>();
        this.wallet = 0;

        this.transactions = new ArrayList<>();
    }




    public void incrementWallet(double reward) {
        wallet += reward;
    }


    public void setRemoteClient(RMIClientInterface remoteClient) {
        this.remoteClient = remoteClient;
    }


    // notifica quando l'utente ha un nuovo follower
    public void notifyNewFollow(String user, String[] tags) throws RemoteException {
        remoteClient.newFollow(user,tags);
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

    public void newPost(Post p) {
        blog.add(p.getId());
    }


//    /**
//     * controlla se il post e' stato creato dall'utente
//     * (quindi se appartiene al suo blog) e lo cancella
//     *
//     * @return true se il post e' stato cancellato con successo, false altrimenti
//     */
//    public boolean deletePost(int id) {
//        if(blog.remove(id) == true)
//            return true;
//
//        return false;
//
//    }


    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public int getSessionId() {
        return sessionId;
    }

    public HashSet<Integer> getBlog() {
        return blog;
    }

    public void addTranstaction(Transaction t) {
        transactions.add(t);
    }


}
