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

public @Getter @Setter class WSUser implements Serializable {

    private String username;
    private String password;
    private String[] tags;

    private HashSet<String> followers;
    private HashSet<String> followed;


    private HashSet<Integer> blog; //salvo gli id dei post creati dall'utente

    private double wallet;
    private ArrayList<Transaction> transactions;

    @JsonIgnore private boolean logged = false;
    @JsonIgnore private RMIClientInterface remoteClient;

    @JsonIgnore private int sessionId;


    @JsonIgnore private ReentrantReadWriteLock readWriteLock;

    @JsonIgnore private Lock readLock;
    @JsonIgnore private Lock writeLock;

    public WSUser(){
        sessionId = -1;
        readWriteLock = new ReentrantReadWriteLock();
        readLock = readWriteLock.readLock();
        writeLock = readWriteLock.writeLock();
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

        this.followers = new HashSet<>();
        this.followed = new HashSet<>();

        this.blog = new HashSet<>();
        this.wallet = 0;

        this.transactions = new ArrayList<>();

        this.readWriteLock = new ReentrantReadWriteLock();
        this.readLock = readWriteLock.readLock();
        this.writeLock = readWriteLock.writeLock();
    }




    public void incrementWallet(double reward) {
        wallet += reward;
    }

    // notifica quando l'utente ha un nuovo follower
    public void notifyNewFollow(String user, String[] tags) throws RemoteException {
        if(remoteClient != null) remoteClient.newFollow(user,tags);
    }

    public void notifyNewUnfollow(String user) throws RemoteException {
        if(remoteClient != null) remoteClient.newUnfollow(user);
    }

    public boolean alreadyLogged(){
        return this.logged;
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
        return followers.add(username);
    }


    /**
     * @return true se era seguito
     */
    public boolean removeFollowed(String username) {
        return followed.remove(username);
    }

    public void removeFollower(String username) {
        followers.remove(username);
    }

    public void newPost(int idPost) {
        blog.add(idPost);
    }

    public void deletePost(int idPost) {
        blog.remove(idPost);
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
