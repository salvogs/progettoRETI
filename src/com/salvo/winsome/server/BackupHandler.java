package com.salvo.winsome.server;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.swing.text.Position;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Salvatore Guastella
 */
public class BackupHandler implements Runnable{

    File usersBackup;
    File postsBackup;

    private ConcurrentHashMap<String, WSUser> registeredUser;

    private ConcurrentHashMap<Integer, Post> posts;


    public BackupHandler(ConcurrentHashMap<String, WSUser> registeredUser, File usersBackup,
                         ConcurrentHashMap<Integer, Post> posts, File postsBackup) {

        this.registeredUser = registeredUser;
        this.usersBackup = usersBackup;
        this.posts = posts;
        this.postsBackup = postsBackup;
    }


    @Override
    public void run() {

        try {
            JsonFactory jsonFactory = new JsonFactory();
//            jsonFactory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false); // per evitare che con la scrittura venga chiusto lo stream
            ObjectMapper mapper = new ObjectMapper(jsonFactory);
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            // scrivo a intervalli di 10 secondi
//            BufferedWriter usersWriter = new BufferedWriter(new FileWriter(usersBackup,false));
//            BufferedWriter postsWriter = new BufferedWriter(new FileWriter(postsBackup,false));

            while (true) {
                Thread.sleep(10000);
//                System.out.println(mapper.writeValueAsString(registeredUser));
                if(registeredUser.size() > 0) {
                    mapper.writeValue(usersBackup,registeredUser);
                    System.out.println("backup utenti effettuato");
                }
                if(posts.size() > 0) {
                    mapper.writeValue(postsBackup, posts);
                    System.out.println("backup post effettuato");
                }
//                usersWriter.flush();
//                postsWriter.close();
            }

//            usersWriter.close();
//            postsWriter.close();



        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }
}
