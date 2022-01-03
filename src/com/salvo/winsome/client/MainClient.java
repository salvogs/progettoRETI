package com.salvo.winsome.client;

import com.sun.deploy.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Salvatore Guastella
 */
public class MainClient {

    private static WSClient client;

    public static void main(String[] args) {

//        int port = Integer.parseInt(args[0]);

        client = new WSClient("localhost",6789,"REGISTRATION-SERVICE");

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in)); // TODO: cambiare con scanner?
        String input = null;

        while (true) {

            try {
                input = br.readLine().trim();
            } catch (IOException e) {
                System.err.println(e.getMessage());
                System.exit(-1);
            }



            decodeAndRunCommand(input.trim());

        }

    }

    private static void decodeAndRunCommand(String input) {

        // controllo prima se e' stata richiesta un' operazione senza parametri

        switch (input) {
            case "logout" :
                client.logout();
                break;
            case "list users" :
                client.listUsers();
                break;

            case "list followers" :
                client.listFollowers();
                break;

            case "list following" :
                client.listFollowing();
                break;

            case "blog" :
                client.blog();
                break;

            case "show feed" :
                client.showFeed();
                break;

            case "wallet" :
                // TODO
                break;

            case "wallet btc" :
                // TODO
                break;


            default: {

                StringTokenizer st = new StringTokenizer(input," ");

                String token = st.nextToken();
                try {

                    switch (token) {
                        case "register" : {

                            String username = st.nextToken();
                            String password = st.nextToken();

                            HashSet<String> tags = new HashSet<>();


                            int tag_counter = 0;

                            while (st.hasMoreTokens()) {
                                if (tag_counter == 5) {
                                    System.out.println("Sono stati inseriti solamente i primi 5 tag");
                                    break;
                                }

                                String tag = st.nextToken().toLowerCase();

                                tag_counter += tags.add(tag) ? 1 : 0;


                            }

                            client.register(username, password, (String[]) tags.toArray(new String[0]));
                        }
                        break;

                        case "login" : {
                            String username = st.nextToken();
                            String password = st.nextToken();

                            client.login(username,password);
                        }
                        break;

                        case "follow" : {
                            String toFollow = st.nextToken();
                            client.followUser(toFollow);
                        }
                        break;

                        case "unfollow" : {
                            String toUnfollow = st.nextToken();
                            client.unfollowUser(toUnfollow);
                        }
                        break;

                        case "post" : {
                            st.nextToken("\"");
                            String title = st.nextToken("\"");
                            st.nextToken("\"");
                            String content = st.nextToken("\"");

                            client.createPost(title, content);
                        }
                        break;

                        case "show" : {
                            if(st.nextToken().equals("post"))
                                new NoSuchElementException(); // todo cambiare?

                            int idPost = Integer.parseInt(st.nextToken());
                            client.showPost(idPost);
                        }
                        break;

                        case "delete" : {
                            int idPost = Integer.parseInt(st.nextToken());

                            client.deletePost(idPost);
                        }


                        break;

                        case "rewin" :{
                            int idPost = Integer.parseInt(st.nextToken());
                            client.rewinPost(idPost);
                        }

                        break;

                        case "rate" : {
                            int idPost = Integer.parseInt(st.nextToken());
                            String vote = st.nextToken();
                            client.ratePost(idPost,vote);
                        }
                        break;

                        case "comment" : {
                            int idPost = Integer.parseInt(st.nextToken());
                            st.nextToken("\"");
                            String comment = st.nextToken("\"");

                            client.addComment(idPost,comment);


                        }
                        break;



                        default:
                            throw new NoSuchElementException(); // todo cambiare?
                    }
                } catch (NoSuchElementException | IllegalArgumentException e){
                    System.err.println("Input non valido, riprova");
                    return;
                }








            }


        }



    }



}
