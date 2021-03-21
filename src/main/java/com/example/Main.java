package com.example;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.util.*;
import java.util.stream.Stream;


public class Main {

    public static int N = 10;
    private static int f = 4;


    public static void main(String[] args) throws InterruptedException {

        long start = System.currentTimeMillis();
        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("System started with N=" + N );

        ArrayList<ActorRef> references = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            // Instantiate processes
            final ActorRef a = system.actorOf(Process.createActor(i + 1, N), "" + i);
            references.add(a);
        }

        //give each process a view of all the other processes
        Members m = new Members(references);
        for (ActorRef actor : references) {
            actor.tell(m, ActorRef.noSender());
        }

        for (ActorRef actor : references) {
            actor.tell(new StartTime(start), ActorRef.noSender());
        }

        
        

        Collections.shuffle(references);

        for(int i = 0; i<f ; ++i){
            references.get(i).tell(new CrashMsg(), ActorRef.noSender());
        }

        // OfconsProposerMsg opm = new OfconsProposerMsg(100);
        // references.get(f).tell(opm, ActorRef.noSender());
    
        for(ActorRef actor : references){
            actor.tell(new LaunchMsg(), ActorRef.noSender());
        }

        Thread.sleep(1000);
        

        for(int i =0 ; i<N ; ++i){
            if(i!=f){
                references.get(i).tell(new HoldMsg(), ActorRef.noSender());

            }
        }

    }
}
