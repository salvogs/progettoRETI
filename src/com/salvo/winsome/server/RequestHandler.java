package com.salvo.winsome.server;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProxySelector;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Salvatore Guastella
 */
public class RequestHandler implements Runnable{

    /*
        riferimento al server per accedere ai suoi metodi
     */
    private WSServer server;

    private String request;
    private Selector selector;
    private SelectionKey key;

    ObjectMapper mapper;

    /**
     * @param request la richiesta del client da gestire
     * @param selector il selector dove e' registrato il channel del client
     * @param key selectable channel del client da registrare
     *        per OP_WRITE quando e' pronta una risposta
     * @throws IOException
     */
    public RequestHandler(WSServer server, String request, Selector selector, SelectionKey key) {
        this.server = server;
        this.request = request;
        this.selector = selector;
        this.key = key;

        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);


    }


    public void run() {
        System.out.println(request);
        boolean badReq = false;
        ObjectNode response = null;
        try {

            JsonNode req = mapper.readTree(request);

            JsonNode op = req.get("request-type");


            switch (op.asText()) {
                case "login": {

                    String username = parseNextTextField(req, "username");
                    if (username != null) {

                        String password = parseNextTextField(req, "password");
                        if (password != null) {

                            SocketChannel clientChannel = (SocketChannel) key.channel();
                            response = server.login(username, password, clientChannel.getRemoteAddress().hashCode());

                        }
                    }

                }

                break;

                case "logout": {

                    String username = parseNextTextField(req, "username");
                    if (username != null) {

                        SocketChannel clientChannel = (SocketChannel) key.channel();

                        response = server.logout(username, clientChannel.getRemoteAddress().hashCode());


                    }
                }
                break;

                case "follow": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {

                        String toFollow = parseNextTextField(req, "to-follow");
                        if (toFollow != null) {

                            response = server.followUser(username, toFollow);


                        }
                    }
                }
                break;

                case "unfollow": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {

                        String toUnfollow = parseNextTextField(req, "to-unfollow");
                        if (toUnfollow != null) {

                            response = server.unfollowUser(username, toUnfollow);


                        }
                    }
                }

                break;

                case "list-users": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {
                        response = server.listUsers(username);


                    }
                }
                break;

                case "list-following": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {
                        response = server.listFollowing(username);


                    }
                }
                break;
                case "create-post": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {
                        String title = parseNextTextField(req, "title");
                        if (title != null) {
                            String content = parseNextTextField(req, "content");
                            if (content != null) {
                                response = server.createPost(username, title, content);

                            }
                        }
                    }


                }

                break;

                case "delete-post": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {

                        int idPost = parseNextNumberField(req, "id-post");

                        if (idPost != -2)
                            response = server.deletePost(username, idPost);
                    }
                }

                break;

                case "rewin-post": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {
                        int idPost = parseNextNumberField(req, "id-post");
                        if (idPost != -2)
                            response = server.rewinPost(username, idPost);
                    }
                }

                break;

                case "show-post": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {

                        int idPost = parseNextNumberField(req, "id-post");
                        if (idPost != -2)
                            response = server.showPost(username, idPost);
                    }
                }

                break;

                case "show-feed": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {
                        response = server.showFeed(username);
                    }

                }
                break;

                case "view-blog": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {
                        response = server.viewBlog(username);
                    }
                }
                break;

                case "rate-post": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {
                        int idPost = parseNextNumberField(req, "id-post");
                        if (idPost != -2) {

                            int vote = parseNextNumberField(req, "vote");

                            if (vote != 0) {
                                response = server.ratePost(username, idPost, vote);

                            }
                        }

                    }
                }

                break;


                case "comment-post": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {
                        int idPost = parseNextNumberField(req, "id-post");
                        if (idPost != -2) {
                            String comment = parseNextTextField(req, "comment");
                            if (comment != null) {

                                response = server.commentPost(username, idPost, comment);

                            }
                        }

                    }
                }
                break;

                case "wallet": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {
                        response = server.getWallet(username);
                    }
                }
                break;

                case "wallet-btc": {
                    String username = parseNextTextField(req, "username");
                    if (username != null) {
                        response = server.getWalletInBitcoin(username);
                    }
                }

                break;

                default:
                    break;

            }



        } catch (IOException | IllegalArgumentException e) {
            System.err.println("parsing richiesta fallito");
            badReq = true;
            e.printStackTrace();
        }

        if(badReq || response == null)
            registerForResponse("{\n  \"status-code\" : 400,\n  \"message\" : \"bad request\"\n}");
        else {
            try {
                registerForResponse(mapper.writeValueAsString(response));
            } catch (IOException e) {}
        }


    }


    /**
     * cambia l'interest set della key in OP_WRITE in modo tale che
     * il dispatcher mandi la risposta quando il channel e' pronto in scrittura
     * @param response
     */
    public void registerForResponse(String response) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES+response.length());
        buffer.putInt(response.length());
        buffer.put(ByteBuffer.wrap(response.getBytes()));
        buffer.flip();
        key.attach(buffer);

        if(selector.isOpen()) {
            key.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }

        // altrimenti il worker potrebbe mandare la risposta
//        else {
//            while (buffer.hasRemaining())
//                ((SocketChannel) key.channel()).write(buffer);
//        }


    }

//    private void sendResponse(String response) throws IOException {
//
//        SocketChannel clientChannel = (SocketChannel) key.channel();
//
//
//        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES+response.length());
//        buffer.putInt(response.length());
//        buffer.put(ByteBuffer.wrap(response.getBytes()));
//
//        buffer.flip();
//
//        while(buffer.hasRemaining())
//            clientChannel.write(buffer);
//
//        // creo il buffer che conterra' la lunghezza del messaggio
//        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
//        // creo il buffer che conterra' il messaggio
//        ByteBuffer message = ByteBuffer.allocate(1024);
//
//        ByteBuffer[] bba = {length, message};
//        // imposto l'interestOps della key OP_READ
//        // e aggiungo l'array di bytebuffer (bba) come attachment
//        key.attach(bba);
//        key.interestOps(SelectionKey.OP_READ);
//        selector.wakeup();
//
//
//    }

    private String parseNextTextField(JsonNode req, String fieldName) {
        JsonNode field = req.get(fieldName);

        return field != null ? field.asText() : null;
    }

    private int parseNextNumberField(JsonNode req, String fieldName) {
        JsonNode field = req.get(fieldName);

        return field != null ? field.intValue() : -2;
    }


}



