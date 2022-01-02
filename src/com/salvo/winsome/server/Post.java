package com.salvo.winsome.server;

import javafx.geometry.Pos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author Salvatore Guastella
 */
public class Post {
    private int id;
    private String author;
    private String title;
    private String content;
    private HashSet<String> upvote;
    private HashSet<String> downvote;
    private HashMap<String, ArrayList<String>> comments;

    /**
     * insieme contenente gli username degli utenti che hanno fatto il rewin del post
     * utile per verificare se un post appartiene al feed di un utente
     */
    private HashSet<String> rewiners;


    public Post(int id, String author, String title, String content) {
        this.id = id;
        this.author = author;
        this.title = title;
        this.content = content;
        this.upvote = new HashSet<>();
        this.downvote = new HashSet<>();
        this.comments = new HashMap<>();
        this.rewiners = new HashSet<>();
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

        if(!comments.containsKey(username))
            comments.put(username,new ArrayList<>());

        comments.get(username).add(comment);
    }

    public int getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public HashSet<String> getUpvote() {
        return upvote;
    }

    public HashSet<String> getDownvote() {
        return downvote;
    }

    public HashMap<String, ArrayList<String>> getComments() {
        return comments;
    }

    public void addRewiner(String username) {
        rewiners.add(username);
    }

    public HashSet<String> getRewiners() {
        return rewiners;
    }
}
