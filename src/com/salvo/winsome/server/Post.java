package com.salvo.winsome.server;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author Salvatore Guastella
 */
@NoArgsConstructor
public class Post {
    @Getter @Setter private int id;
    @Getter @Setter private String author;
    @Getter @Setter private String title;
    @Getter @Setter private String content;
    @Getter @Setter private HashSet<String> upvote;
    @Getter @Setter private HashSet<String> downvote;
    @Getter @Setter private HashMap<String, ArrayList<String>> comments;

    @Getter @Setter private int n_iterations = 0;




    /**
     * insieme contenente gli username degli utenti che hanno fatto il rewin del post
     * utile per verificare se un post appartiene al feed di un utente
     */
    private HashSet<String> rewiners;


    public Post(int id, String author, String title, String content,int n_iterations) {
        this.id = id;
        this.author = author;
        this.title = title;
        this.content = content;
        this.upvote = new HashSet<>();
        this.downvote = new HashSet<>();
        this.comments = new HashMap<>();
        this.rewiners = new HashSet<>();
        this.n_iterations = n_iterations;
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






//    public int incAndGetN_iterations() {
//        return ++n_iterations;
//    }
}
