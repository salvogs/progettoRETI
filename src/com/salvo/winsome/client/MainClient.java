package com.salvo.winsome.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

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
                input = br.readLine();
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
                // TODO
                break;

            case "list users" :
                // TODO
                break;

            case "list followers" :
                // TODO
                break;

            case "list following" :
                // TODO
                break;

            case "blog" :
                // TODO
                break;

            case "show feed" :
                // TODO
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
                        case "register" :

                            String username = st.nextToken();
                            String password = st.nextToken();

                            ArrayList<String> tags = new ArrayList<>();

                            int tag_counter = 0;

                            while(st.hasMoreTokens()){
                                if(tag_counter == 4){
                                    System.out.println("Sono stati inseriti solamente i primi 5 tag");
                                    break;
                                }

                                String tag = st.nextToken();

                                tag_counter += putIfAbsent(tag,tags); // invariato se ritorna 0


                            }

                            client.register(username,password, (String[]) tags.toArray(new String[0]));
                            break;

                        case "login" :
                            client.login("aa","dsddf");
                            // TODO
                            break;

                        case "follow" :
                            // TODO
                            break;

                        case "unfollow" :
                            // TODO
                            break;

                        case "post" :
                            // TODO
                            break;

                        case "show" :
                            // TODO
                            break;

                        case "delete" :
                            // TODO
                            break;

                        case "rewin" :
                            // TODO
                            break;

                        case "rate" :
                            // TODO
                            break;

                        case "comment" :
                            // TODO
                            break;



                        default:
                            throw new NoSuchElementException();
                    }
                } catch (NoSuchElementException e){
                    System.err.println("Input non valido, riprova");
                    return;
                }








            }


        }



    }



/**
 * @return 1 se il tag e' nuovo, 0 altrimenti
 */
 private static int putIfAbsent(String tag, ArrayList<String> tags){
        // controllo che non sia stato gia' inserito lo stesso tag
        for ( String s : tags) {
            if(s.equals(tag))
                return 0; // non memorizzo il tag
        }

        // nuovo tag

        tags.add(tag);
        return 1;
    }


}
