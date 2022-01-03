package com.salvo.winsome.server;

import java.util.*;

/**
 * @author Salvatore Guastella
 */
public class RewardsHandler implements Runnable{

    private static final int TIMEOUT = 30;//tempo in secondi

    private WSServer server;
    private HashMap<Integer,Post> posts;
    private double authorPercentage = 0.7; // todo config


    public RewardsHandler(WSServer server) {
        this.server = server;
        this.posts= server.getPosts();
    }

    @Override
    public void run() {

        while (true) {
//            try {
//                Thread.sleep(TIMEOUT*1000);
            Scanner s = new Scanner(System.in);

            System.out.println(s.nextLine());
                System.out.println("SCATTATO TIMERRRRR");
                server.go();
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

        HashMap<Integer, HashSet<String>> upvotes = server.getNewUpvotes();
        HashMap<Integer, HashSet<String>> downvotes = server.getNewDownvotes();
        HashMap<Integer, ArrayList<String>> comments = server.getNewComments();


        // insieme dei post modificati
        HashSet<Integer> postsToCompute = new HashSet<>();


        postsToCompute.addAll(upvotes.keySet());
        postsToCompute.addAll(downvotes.keySet());
        postsToCompute.addAll(comments.keySet());


        for(Integer idPost : postsToCompute) {

            //if(postexists) // todo o lo cancello dalle 3 map alla delete?


            int n_upvote = upvotes.containsKey(idPost) ? upvotes.get(idPost).size() : 0;
            int n_downvote = downvotes.containsKey(idPost) ? downvotes.get(idPost).size() : 0;
            int n_comments = comments.containsKey(idPost) ? comments.get(idPost).size() : 0;

            Post p = posts.get(idPost);
            int n_iterations = p.incAndGetN_iterations();

            int newUpvotes = n_upvote == 0 ? 0 : upvotes.get(idPost).size();
            int newDownvotes = n_downvote == 0 ? 0 : downvotes.get(idPost).size();

            int newPeopleLikesSum = newUpvotes - newDownvotes;

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
            reward = reward / n_iterations;


            System.out.println("rewarddd: "+reward);

            double authorReward = reward * authorPercentage;



            HashSet<String> curators = new HashSet<>();

            if(n_upvote > 0) curators.addAll(upvotes.get(idPost));
            if(n_downvote > 0) curators.addAll(downvotes.get(idPost));
            if(n_comments > 0) curators.addAll(comments.get(idPost));

            double curatorReward = (reward - authorReward) / curators.size();

            for(String curator : curators)
                server.incrementWallet(curator,curatorReward);


            postsToCompute.remove(idPost);

            upvotes.remove(idPost);
            downvotes.remove(idPost);
            comments.remove(idPost);


        }




    }
}
