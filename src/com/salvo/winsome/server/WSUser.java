package com.salvo.winsome.server;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.salvo.winsome.RMIClientInterface;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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


    private ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private Lock readLock = readWriteLock.readLock();
    private Lock writeLock = readWriteLock.writeLock();

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


    /**
     * @return true se e' un nuovo seguito
     */
    public boolean addFollowed(String username) {
        return followed.add(username);
    }

    public boolean addFollower(String username) {
        return follower.add(username);
    }


    /**
     * @return true se era seguito
     */
    public boolean removeFollowed(String username) {
        return followed.remove(username);
    }

    public void removeFollower(String username) {
        follower.remove(username);
    }

    public void newPost(int idPost) {
        blog.add(idPost);
    }

    public void deletePost(int idPost) {
        blog.remove(idPost);
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


    public void lockRead() {
        readLock.lock();
    }

    public void unlockRead() {
        readLock.unlock();
    }

    public void lockWrite() {
        writeLock.lock();
    }

    public void unlockWrite() {
        writeLock.unlock();
    }

}
