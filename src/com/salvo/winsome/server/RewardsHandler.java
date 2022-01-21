package com.salvo.winsome.server;

import java.io.IOException;
import java.net.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Salvatore Guastella
 */
public class RewardsHandler implements Runnable{

    private static final int TIMEOUT = 30;//tempo in secondi

    WSServer server;
    private final InetAddress multicastAddress;
    private final int multicastPort;
    private DatagramSocket dsocket;


    private ConcurrentHashMap<Integer,Post> posts;
    HashMap<Integer, HashSet<String>> upvotes;
    HashMap<Integer, HashSet<String>> downvotes;
    HashMap<Integer, ArrayList<String>> comments;

    private double authorPercentage = 0.7; // todo config


    private int globalIterations;

    public RewardsHandler(WSServer server) {
        this.server = server;
        this.posts = server.getPosts();

        this.globalIterations = server.getRewardsIteration(); // da backup

        this.multicastAddress = server.getMulticastAddress();
        this.multicastPort = server.getMulticastPort();

        try {
            dsocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(-1);
        }

    }

    @Override
    public void run() {


        while (true) {

            server.setRewardsIteration(globalIterations);
//            try {
//                Thread.sleep(TIMEOUT*1000);
            Scanner s = new Scanner(System.in);

            System.out.println(s.nextLine());
                System.out.println("SCATTATO TIMERRRRR");
                globalIterations++;
//                server.go();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            computeRewards();
        }



    }

    private void computeRewards() {

        /* recupero dal server le hashmap relative alle (nuove) interazioni
           sulle quali calolare le ricompense
         */

        this.upvotes = server.replaceAndGetNewUpvotes();
        this.downvotes = server.replaceAndGetNewDownvotes();
        this.comments = server.replaceAndGetNewComments();


        // insieme dei post modificati
        HashSet<Integer> postsToCompute = new HashSet<>();


        postsToCompute.addAll(upvotes.keySet());
        postsToCompute.addAll(downvotes.keySet());
        postsToCompute.addAll(comments.keySet());


        int postsComputed = 0;

        for(Integer idPost : postsToCompute) {


            int n_upvote = upvotes.containsKey(idPost) ? upvotes.get(idPost).size() : 0;
            int n_downvote = downvotes.containsKey(idPost) ? downvotes.get(idPost).size() : 0;
            int n_comments = comments.containsKey(idPost) ? comments.get(idPost).size() : 0;

            Post p = posts.get(idPost);

            if(p == null) continue; // controllo se il post è stato cancellato


            int n_iterations = p.getN_iterations();



            int newPeopleLikesSum = n_upvote - n_downvote;

            double reward = Math.log(Math.max(newPeopleLikesSum,0) + 1); // prima parte numeratore

            double newPeopleCommentingSum = 0;



            if(n_comments > 0) {

                //scorro la lista delle persone che hanno commentato quel post (se ci sono commenti)

                ArrayList<String> peopleCommented = comments.get(idPost);
                // corrispondenza username - n-commenti in idpost
                HashMap<String,Integer> nCommentUsers = new HashMap<>();

                for(String commented_user : peopleCommented) {

                    Integer cp = nCommentUsers.get(commented_user);

                    if(cp == null)
                        nCommentUsers.put(commented_user,1);
                    else
                        nCommentUsers.put(commented_user,cp+1);

                }
                // sommatoria

                for (String username : nCommentUsers.keySet()) {
                    int cp = nCommentUsers.get(username);
                    newPeopleCommentingSum += 2 / (1 + Math.exp(-(cp-1)));
                }
            }


            reward += Math.log(newPeopleCommentingSum+1); // seconda parte numeratore
            reward = reward / (globalIterations - n_iterations);


            System.out.println("rewarddd: "+reward);

            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

            double authorReward = reward * authorPercentage;
            server.incrementWallet(p.getAuthor(),sdf.format(new Date()),authorReward);


            HashSet<String> curators = new HashSet<>();

            if(n_upvote > 0) curators.addAll(upvotes.get(idPost));
            if(n_downvote > 0) curators.addAll(downvotes.get(idPost));
            if(n_comments > 0) curators.addAll(comments.get(idPost));

            double curatorReward = (reward - authorReward) / curators.size();

            for(String curator : curators)
                server.incrementWallet(curator,sdf.format(new Date()),curatorReward);


            postsToCompute.remove(idPost);
            upvotes.remove(idPost);
            downvotes.remove(idPost);
            comments.remove(idPost);


            postsComputed++;

        }

        if(postsComputed > 0) {
            // invio notifica multicast ai client
            // ricevuta solo dai client 'online' e con le notifiche attive

            try {


                final byte[] msg = "Calcolo ricompense effettuato".getBytes();
                DatagramPacket dpacket = new DatagramPacket(msg,msg.length,multicastAddress,multicastPort);

                dsocket.send(dpacket);

            } catch (IOException e) {
                e.printStackTrace();
            }

        }



    }
}
