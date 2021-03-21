package com.example;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor
    private final int N;//number of processes
    private final int id;//id of current process
    private Members processes;//other processes' references
    private Integer proposal;
    private int ballot;
    private int readballot;
    private int imposeballot;
    private Integer estimate;
    private int ack_counter =0;
    private boolean received_decide = false;
    private ArrayList<Pair<Integer,Integer>> states;
    private long startTime = 0;

    private boolean fault_prone = false;
    private boolean silent = false;
    private double crash_proba = 0.1;

    private boolean hold = false;
    private boolean decided = false;

    private int old_ballot = 0;


    public Process(int ID, int nb) {

        N = nb;
        id = ID;

        ballot = id - N;
        readballot = 0;
        imposeballot= id-N;

        states = new ArrayList<Pair<Integer,Integer>>();

        
        
    }
    
    public String toString() {
        return "Process{" + "id=" + id ;
    }

    /**
     * Static function creating actor
     */
    public static Props createActor(int ID, int nb) {
        return Props.create(Process.class, () -> {

            return new Process(ID, nb);
        });
    }
    
    
    private void propose(Integer v) {
       proposal = v;
       //log.info("p" + self().path().name() + " proposes : " + proposal);
       ballot += N;
       states = new ArrayList<Pair<Integer,Integer>>(); 
       for (ActorRef actor : processes.references) {
           actor.tell(new ReadMsg(ballot), this.getSelf());
           //log.info("Read ballot " + ballot + " msg: p" + self().path().name() + " -> p" + actor.path().name());
       }
    }
    

    private void readReceived(int newBallot, ActorRef pj) {
            //log.info("read received " + self().path().name() );

            if(readballot > newBallot || imposeballot > newBallot){
                pj.tell(new AbortMsg(newBallot), this.getSelf());
            }
            else{
                readballot = newBallot;
                pj.tell(new GatherMsg(newBallot, imposeballot, estimate), this.getSelf());

            }
    }

    private void gatherReceived(Integer est, int estballot , ActorRef sender ){
        states.add(new Pair<Integer,Integer>(est, estballot));
        int max_ballot = 0;
        if(states.size() >= N/2 +1){
            for(int i = 0; i<states.size(); ++i){
                if(states.get(i).second() > max_ballot){
                    max_ballot = states.get(i).second() ;    
                    proposal = states.get(i).first();
                }
            }
            states = new ArrayList<Pair<Integer,Integer>>();
            for (ActorRef actor : processes.references) {
                actor.tell(new ImposeMsg(ballot, proposal), this.getSelf());
                //log.info("Impose ballot " + ballot +  ", proposal : " + proposal + ", msg: p" + self().path().name() + " -> p" + actor.path().name());
            }
        }
        
        
    }

    private void imposeReceived(int newBallot, Integer v, ActorRef pj){
        if(readballot > newBallot || imposeballot > newBallot){
            pj.tell(new AbortMsg(newBallot), this.getSelf());
        }
        else{
            estimate = v;
            imposeballot = newBallot;
            pj.tell(new AckMsg(newBallot), this.getSelf());
        }
    }
    
    
    public void onReceive(Object message) throws Throwable {
        if(fault_prone && !silent){
            double rd = Math.random();
            if(rd<crash_proba){
                silent=true;
                //log.info("p" + self().path().name() + ": I have crashed ! :(");
            }
        }
        if(silent){
            return;
        }

        if (message instanceof Members) {//save the system's info
            Members m = (Members) message;
            processes = m;
            //log.info("p" + self().path().name() + " received processes info");
          }
        else if (message instanceof LaunchMsg) {
            Integer v = 0;
            if(Math.random()>0.5){
                v = 1;
            }
            this.propose(v);
      
          }
        else if (message instanceof ReadMsg) {
            ReadMsg m = (ReadMsg) message;
            this.readReceived(m.ballot, getSender());
          }
        else if (message instanceof AbortMsg){
            AbortMsg m = (AbortMsg) message;
            
            if(m.ballot != old_ballot && !decided){
                old_ballot = m.ballot;
                //log.info("p" + self().path().name() + " aborted");
                if(!hold){
                    propose(proposal);
                }
                
            }
            

          }
        else if (message instanceof GatherMsg){
            GatherMsg m = (GatherMsg) message;
            this.gatherReceived(m.estimate, m.imposeballot, getSender());
        }
        else if(message instanceof ImposeMsg){
            ImposeMsg m = (ImposeMsg) message;
            this.imposeReceived(m.ballot, m.proposal, getSender());
        }
        else if(message instanceof AckMsg){
            ack_counter++;
            if(ack_counter>=(N/2)+1){
                ack_counter =0;
                //log.info( "Ack correct" );
                for (ActorRef actor : processes.references) {
                    actor.tell(new DecideMsg(proposal), this.getSelf());
                }
            }
            
        }
        else if(message instanceof DecideMsg){
            if(received_decide == false){
                received_decide = true;
                DecideMsg m = (DecideMsg) message;
                long elapsedTime =  System.currentTimeMillis() - startTime;
                log.info( "p" + self().path().name()+" decided : " + m.proposal + " | time :" + elapsedTime);
                //Thread.sleep(100);
                //System.exit(1);
                
                decided = true;
                for (ActorRef actor : processes.references) {
                    actor.tell(new DecideMsg(m.proposal), this.getSelf());
               }

            }
        }
        else if(message instanceof CrashMsg){
            fault_prone = true;
            //log.info( "p" + self().path().name()+" is fault-prone" );

        }
        else if(message instanceof HoldMsg){
            hold = true;
            //log.info( "p" + self().path().name()+" holds" );

        }
        else if(message instanceof StartTime){
            startTime = ((StartTime)message).time;
        }
        
    }



}   
