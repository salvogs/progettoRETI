package com.salvo.winsome.server;

/**
 * @author Salvatore Guastella
 */
public class WSUser {

    private String username;
    private String password;
    private String[] tags;

    public WSUser(String username, String password, String[] tags){
        this.username = username;
        this.password = password;
        this.tags = tags;
    }
}
