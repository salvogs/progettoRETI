package com.salvo.winsome.server;

import java.io.IOException;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Salvatore Guastella
 */
public class RewardsHandler implements Runnable{

    private WSServer server;
    private int rewardsPeriod;
    private double authorPercentage;
    private final InetAddress multicastAddress;
    private final int multicastPort;
    private DatagramSocket dsocket;


    private ConcurrentHashMap<Integer, WSPost> posts;
    HashMap<Integer, HashSet<String>> upvotes;
    HashMap<Integer, HashSet<String>> downvotes;
    HashMap<Integer, ArrayList<String>> comments;


    private int globalIterations;

    public RewardsHandler(WSServer server, double authorPercentage, int rewardsPeriod) {
        this.server = server;
        this.authorPercentage = authorPercentage;
        this.rewardsPeriod = rewardsPeriod; // in ms

        this.posts = server.getPosts();

        this.globalIterations = server.getRewardsIteration();

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

        System.out.println("Thread ricompense avviato");

        while (!Thread.currentThread().isInterrupted()) {

            try {
                Thread.sleep(rewardsPeriod);
                computeRewards();

            } catch (InterruptedException e) {
                break;
            }


//            Scanner s = new Scanner(System.in);
//
//            System.out.println(s.nextLine());
//            System.out.println("SCATTATO TIMERRRRR");
        }

        // calcolo le ricompense per l'ultima volta
        computeRewards();
        dsocket.close();
        return;




    }

    private void computeRewards() {

        globalIterations++;
        server.setRewardsIteration(globalIterations);

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

            WSPost p = posts.get(idPost);

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


//            System.out.println("rewarddd: "+reward);

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

            System.out.println("Calcolo ricompense effettuato");

            // invio notifica multicast ai client
            // ricevuta solo dai client 'online' e con le notifiche attive

            try {

                final byte[] msg = "Calcolo ricompense effettuato".getBytes();
                DatagramPacket dpacket = new DatagramPacket(msg, msg.length, multicastAddress, multicastPort);

                dsocket.send(dpacket);

                System.out.println("Avviso multicast inviato");
            } catch (IOException e) {
                System.err.println("Errore: impossibile inviare messaggio multicast");
//                e.printStackTrace();
            }

        }



    }



}
