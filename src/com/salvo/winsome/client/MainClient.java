package com.salvo.winsome.client;

import com.salvo.winsome.ConfigParser;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;


/**
 * @author Salvatore Guastella
 */
public class MainClient {

    private static WSClient client;
    private static int eCounter = 0;


    public static void main(String[] args) {


        if(args.length != 1) {
            System.err.println("usage: java MainClient <config path>");
            System.exit(-1);
        }

        HashMap<String,String> result = ConfigParser.parseConfigFile(args[0]);

        System.out.println(result);


        String serverAddress = ConfigParser.getStringParameter(result,"server");
        int tcpPort = ConfigParser.getPortParameter(result,"tcpport");
        String registryAddr = ConfigParser.getStringParameter(result,"rmireghost");
        int registryPort = ConfigParser.getPortParameter(result,"rmiregport");
        String regServiceName = ConfigParser.getStringParameter(result,"rmiservicename");


        client = new WSClient(serverAddress,tcpPort,registryAddr,registryPort,regServiceName);


        try( BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {


            System.out.println("Benvenuto in winsome:");
            System.out.println("Digita 'help' per visualizzare la lista dei comandi, 'stop' per terminare l'esecuzione");
            while (true) {
                String input = br.readLine().trim();
                if (input.equals("")) continue;

                if(decodeAndRunCommand(input.trim()) == -2) System.exit(0);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

//try{
//            System.out.println("Benvenuto in winsome:");
//            System.out.println("Digita 'help' per visualizzare la lista dei comandi, 'stop' per terminare l'esecuzione");
//
//            String input = String.join(" ",args);
//            StringTokenizer st = new StringTokenizer(input," ");
//            String token = st.nextToken();
//            token = st.nextToken("+");
//            while (token != null) {
//                System.out.println(token);
//
//                if(decodeAndRunCommand(token.trim()) == -2) System.exit(0);
//                token = st.nextToken("+");
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.exit(-1);
//        }

    }



    private static int decodeAndRunCommand(String input) throws IOException{
        // controllo prima se e' stata richiesta un' operazione senza parametri
        switch (input) {
            case "help":
                help();
                break;
            case "stop" : {
                client.stop();
                return -2;
            }
            case "dnd on" :
                client.enableDoNotDisturb();
                break;
            case "dnd off" :
                client.disableDoNotDisturb();
                break;
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
                client.getWallet();
                break;

            case "wallet btc" :
                client.getWalletInBitcoin();
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
                            if(!st.nextToken().equals("post")) {
                                inputError();
                                return -1;
                            }


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
                            inputError();
                            return -1;
                    }
                } catch (NoSuchElementException | IllegalArgumentException e){
                    inputError();
                    return -1;
                }

            }


        }

        return 0;

    }

    private static void inputError() {
        System.err.println("Input non valido, riprova");
        eCounter++;
        if(eCounter == 3) {
            eCounter = 0;
            System.out.println("Digita il comando 'help' per visualizzare la lista dei comandi");
        }
    }

    private static void help() {
        System.out.println("----------------- Sintassi comandi WINSOME client -----------------");
        System.out.println("ATTENZIONE: tutti i comandi sono case sensitive");
        System.out.println("dnd on -> per disabilitare la stampa delle notifiche");
        System.out.println("dnd off -> per abilitare la stampa delle notifiche");
        System.out.println("register <username> <password> -> per registrare un nuovo utente su winsome");
        System.out.println("login <username> <password> -> per effettuare il login di uno specifico utente");
        System.out.println("logout <username> -> per effettuare il logout di uno specifico utente");
        System.out.println("list users -> per visualizzare gli utenti di winsome con almeno un tag in comune");
        System.out.println("list following -> per visualizzare la lista degli utenti seguiti");
        System.out.println("list followers -> per visualizzare la lista dei propri followers");
        System.out.println("follow <username> -> per seguire un utente");
        System.out.println("unfollow <username> -> per smettere di seguire un utente");
        System.out.println("blog -> per visualizzare il proprio blog (post di cui l'utente e' autore)");
        System.out.println("show feed -> per visualizzare il proprio feed (blog degli utenti seguiti)");
        System.out.println("post <\"title\"> <\"content\"> -> per creare un nuovo post su winsome (\"\" obbligatorie)");
        System.out.println("delete <idpost> -> per cancellare un post da winsome");
        System.out.println("rewin <idpost> -> per effettuare il rewin di un post");
        System.out.println("show post <idpost> -> per visualizzare i dettagli di un post");
        System.out.println("rate <idpost> <vote> -> per esprimere un voto su un post (+1 positivo -1 negativo)");
        System.out.println("comment <idpost> <\"commment\"> -> per aggiungere un commento ad un post (\"\" obbligatorie)");
        System.out.println("wallet -> per recuperare il valore del proprio portafoglio e la lista delle transazioni");
        System.out.println("wallet btc -> per recuperare il valore del proprio portafoglio convertito in bitcoin");
        System.out.println("------------------------------------------------------------------\n");
    }


}
