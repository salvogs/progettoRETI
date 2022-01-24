package com.salvo.winsome.client;

import com.salvo.winsome.Utils;


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

        HashMap<String,String> result = Utils.parsingConfigFile(args[0]);

        System.out.println(result);


        String serverAddress = getStringParameter(result,"server");
        int tcpPort = getPortParameter(result,"tcpport");
        int udpPort = getPortParameter(result,"udpport");
        String multicastAddress = getStringParameter(result,"multicast");
        int multicastPort = getPortParameter(result,"mcastport");
        String registryAddr = getStringParameter(result,"rmireghost");
        int registryPort = getPortParameter(result,"rmiregport");
        String regServiceName = getStringParameter(result,"rmiservicename");


        client = new WSClient(serverAddress,tcpPort,registryAddr,registryPort,regServiceName);


        System.out.println("\n" +
                " _    _ _____ _   _  _____  ________  ___ _____ \n" +
                "| |  | |_   _| \\ | |/  ___||  _  |  \\/  ||  ___|\n" +
                "| |  | | | | |  \\| |\\ `--. | | | | .  . || |__  \n" +
                "| |/\\| | | | | . ` | `--. \\| | | | |\\/| ||  __| \n" +
                "\\  /\\  /_| |_| |\\  |/\\__/ /\\ \\_/ / |  | || |___ \n" +
                " \\/  \\/ \\___/\\_| \\_/\\____/  \\___/\\_|  |_/\\____/ \n" +
                "                                                \n" +
                "                                                \n");


        try( BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {


            System.out.println("Benvenuto in winsome:");
            System.out.println("Digita 'help' per visualizzare la lista dei comandi, quit per uscire");
            while (true) {
                String input = br.readLine().trim();
                if (input.equals("")) continue;

                if(decodeAndRunCommand(input.trim()) == -1) System.exit(0);
            }

        } catch (Exception e) {
            System.exit(-1);
        }

    }

    private static String getStringParameter(HashMap<String,String> result, String name) {

        String parameter = result.get(name);

        if(parameter == null) {
            System.err.println("parsing \""+name+"\" fallito");
            System.exit(-1);
        }

        return parameter;
    }

    private static int getPortParameter(HashMap<String,String> result, String portName) {

        String port = result.get(portName);
        int portnum = 0;

        try {
            if (port != null) {

                portnum = Integer.parseInt(port);

                if (portnum < 1024 || portnum > 65535) {
                    System.err.println("porta \"" + portName + "\"non valida");
                    System.exit(-1);
                }
            } else throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.err.println("parsing \""+portName+"\" fallito");
            System.exit(-1);
        }

        return portnum;
    }



    private static int decodeAndRunCommand(String input) {

        // controllo prima se e' stata richiesta un' operazione senza parametri

        switch (input) {
            case "help":
                help();
                break;
            case "quit" : {
                client.stop();
                return -1;
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
                    eCounter++;
                    if(eCounter == 3) {
                        eCounter = 0;
                        System.out.println("Digita il comando 'help' per visualizzare la lista dei comandi");
                    }
                    return 0;
                }

            }


        }

        return 0;

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
