package com.salvo.winsome.server;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Salvatore Guastella
 */
@NoArgsConstructor
public @Getter @Setter class WSPost {
    private int id;
    private String author;
    private String title;
    private String content;
    private HashSet<String> upvote;
    private HashSet<String> downvote;
    private HashMap<String, ArrayList<String>> comments;

    private int n_iterations;



    /**
     * insieme contenente gli username degli utenti che hanno fatto il rewin del post
     * utile per verificare se un post appartiene al feed di un utente
     */
    private HashSet<String> rewiners;

    @JsonIgnore private ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    @JsonIgnore private Lock readLock = readWriteLock.readLock();
    @JsonIgnore private Lock writeLock = readWriteLock.writeLock();

    @JsonIgnore private boolean deleted;

    public WSPost(int id, String author, String title, String content, int n_iterations) {
        this.id = id;
        this.author = author;
        this.title = title;
        this.content = content;
        this.upvote = new HashSet<>();
        this.downvote = new HashSet<>();
        this.comments = new HashMap<>();
        this.rewiners = new HashSet<>();
        this.n_iterations = n_iterations;
        this.deleted = false;
    }

    public boolean voted(String username) {
        return upvote.contains(username) || downvote.contains(username);
    }

    public void newUpvote(String username) {
        upvote.add(username);
    }

    public void newDownvote(String username) {
        downvote.add(username);
    }

    public void newComment(String username, String comment) {

        comments.putIfAbsent(username,new ArrayList<>());
        comments.get(username).add(comment);
    }


    public void addRewiner(String username) {
        rewiners.add(username);
    }

    public HashSet<String> getRewiners() {
        return rewiners;
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

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted() {
        deleted = true;
    }


//    public int incAndGetN_iterations() {
//        return ++n_iterations;
//    }
}
