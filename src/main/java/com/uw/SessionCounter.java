package com.uw;

import com.google.common.base.Optional;
import com.uw.model.ActivityDuration;
import com.uw.model.Session;
import com.uw.model.SessionEvent;
import com.uw.model.SessionWithActivity;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.hive.HiveContext;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.*;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import scala.Tuple2;

import java.util.*;

public class SessionCounter {
    public static void main(String[] args){
        String[] topics = {"test"};
        int numThreads = 1;
        String zkQuorum = "localhost:2181";
        String groupId = "testgr";
        int sessionTime = 1; //in mins
        String checkpointDir = "/tmp/spark_sessionization";

        Map<String, Integer> topicMap = new HashMap<>();
        for (String topic: topics) {
            topicMap.put(topic, numThreads);
        }

        //only for localrunning
        SparkConf sparkConf = new SparkConf().setMaster("local[2]").setAppName("SessionCounter");

        //uncomment for server running
        //SparkConf sparkConf = new SparkConf().setAppName("SessionCounter");

        JavaStreamingContextFactory contextFactory = new JavaStreamingContextFactory() {
            @Override
            public JavaStreamingContext create() {
                JavaStreamingContext jssc = new JavaStreamingContext(sparkConf, new Duration(5000));

                HiveContext hiveContext = new HiveContext(jssc.sc());

                hiveContext.sql("CREATE EXTERNAL TABLE IF NOT EXISTS sessions (startTime BIGINT, sessionId STRING, endTime BIGINT, status STRING, userId STRING ) LOCATION '/tmp/hivew/sessions/'");
                hiveContext.sql("CREATE EXTERNAL TABLE IF NOT EXISTS sessionevents (activityType STRING, endTime BIGINT, sessionId STRING, startTime BIGINT, userId STRING ) LOCATION '/tmp/hivew/sessionevents/'");

                jssc.checkpoint(checkpointDir);

                //TODO only for localrunning
                jssc.sc().setLogLevel("ERROR");

                JavaPairReceiverInputDStream<String, String> messages =
                        KafkaUtils.createStream(jssc, zkQuorum, groupId, topicMap);

                JavaDStream<SessionEvent> sessionEvents = messages.map(tuple2 -> {
                    JSONParser jsonParser = new JSONParser();
                    SessionEvent sessionEvent = null;
                    try {
                        JSONObject jsonObject = (JSONObject) jsonParser.parse(tuple2._2());
                        sessionEvent = new SessionEvent();
                        sessionEvent.sessionId = (String) jsonObject.get("sessionId");
                        sessionEvent.userId = (String) jsonObject.get("userId");
                        sessionEvent.activityType = (String) jsonObject.get("activityType");
                        sessionEvent.timestamp = (Long) jsonObject.get("timestamp");
                    } catch (Exception e) {
                        //e.printStackTrace();
                    }
                    return sessionEvent;
                });

                Function2<List<Session>, Optional<Session>, Optional<Session>> updateSession =
                        (values, state) -> {
                            Optional<Session> result = null;

                            if(values.size()==0){
                                if(System.currentTimeMillis() - state.get().endTime > sessionTime * 60 * 1000 ){
                                    state.get().status = "end";
                                }
                                result = state;
                            }

                            for(Session s : values){
                                if(!state.isPresent()){
                                    if(System.currentTimeMillis() - s.endTime > sessionTime * 60 * 1000 ){
                                        s.status = "end";
                                    }
                                    result = Optional.of(s);
                                } else {
                                    Session session = new Session();
                                    session.userId = s.userId;
                                    session.sessionId = s.sessionId;
                                    session.startTime = Math.min(s.startTime, state.get().startTime);
                                    session.endTime = Math.max(s.endTime, state.get().endTime);
                                    session.status = s.status;

                                    if(System.currentTimeMillis() - session.endTime > sessionTime * 60 * 1000 ){
                                        session.status = "end";
                                    }

                                    result = Optional.of(session);
                                }

                            }
                            return result;
                        };

                JavaPairDStream<String, Session> sessionInfo = sessionEvents.filter(sessionEvent -> sessionEvent!=null)
                .mapToPair(sessionEvent -> {
                    Session session = new Session();
                    session.sessionId = sessionEvent.sessionId;
                    session.userId = sessionEvent.userId;
                    session.startTime = sessionEvent.timestamp;
                    session.endTime = sessionEvent.timestamp;
                    session.status = "active";
                    if(sessionEvent.activityType.equals("end")) {
                        session.status = "end";
                    }
                    return new Tuple2<>(session.sessionId, session);
                }).reduceByKey((a, b) -> {
                    Session session = new Session();
                    session.userId = a.userId;
                    session.sessionId = a.sessionId;
                    session.startTime = Math.min(a.startTime, b.startTime);
                    session.endTime = Math.max(a.endTime, b.endTime);
                    if(a.getStatus().equals("end") || b.getStatus().equals("end")) {
                        session.status = "end";
                    } else {
                        session.status = "active";
                    }
                    return session;
                }).updateStateByKey(updateSession);

                sessionInfo.foreachRDD((id, s) ->{
                    DataFrame df = hiveContext.createDataFrame(id.values(), Session.class);
                    df.insertInto("sessions", true);

                    System.out.println("-----sessions-----");
                    hiveContext.sql("SELECT * FROM sessions").show();
                });

                sessionInfo.print();

                Function2<List<SessionWithActivity>, Optional<SessionWithActivity>, Optional<SessionWithActivity>> updateActivityDuration =
                        (values, state) -> {
                            Optional<SessionWithActivity> result = null;

                            if(values.size()==0){
                                result = state;
                            }

                            for(SessionWithActivity s : values){
                                if(!state.isPresent()){
                                    if(System.currentTimeMillis() - s.endTime > sessionTime * 60 * 1000 ){
                                        s.status = "end";
                                    }
                                    result = Optional.of(s);
                                } else {
                                    SessionWithActivity session = new SessionWithActivity();
                                    session.userId = s.userId;
                                    session.sessionId = s.sessionId;
                                    session.startTime = Math.min(s.startTime, s.startTime);
                                    session.endTime = Math.max(s.endTime, state.get().endTime);
                                    session.status = s.status;

                                    if(System.currentTimeMillis() - session.endTime > sessionTime * 60 * 1000 ){
                                        session.status = "end";
                                    }

                                    List<ActivityDuration> list = new LinkedList<ActivityDuration>();
                                    list.addAll(s.getActivityDurationList());
                                    list.addAll(state.get().getActivityDurationList());
                                    Collections.sort(list);

                                    List<ActivityDuration> newlist = new LinkedList<ActivityDuration>();

                                    ActivityDuration ac = list.get(0);
                                    for(int i=1; i<list.size(); i++){
                                        if(ac.getActivityType().equals(list.get(i).getActivityType())){
                                            ac.setEndTime(list.get(i).getEndTime());
                                        } else {
                                            ac.setEndTime(list.get(i).getStartTime());
                                            newlist.add(ac);
                                            ac = list.get(i);
                                        }
                                    }
                                    newlist.add(ac);
                                    session.setActivityDurationList(newlist);

                                    result = Optional.of(session);
                                }

                            }
                            return result;
                        };


                JavaPairDStream<String, SessionWithActivity> activityInfo = sessionEvents.filter(sessionEvent -> sessionEvent!=null)
                        .mapToPair(sessionEvent -> {
                            SessionWithActivity session = new SessionWithActivity();
                            session.sessionId = sessionEvent.sessionId;
                            session.userId = sessionEvent.userId;
                            session.startTime = sessionEvent.timestamp;
                            session.endTime = sessionEvent.timestamp;
                            session.status = "active";

                            if(sessionEvent.activityType.equals("end")) {
                                session.status = "end";
                            }

                            ActivityDuration activity = new ActivityDuration();
                            activity.activityType = sessionEvent.activityType;
                            activity.endTime = sessionEvent.timestamp;
                            activity.startTime = sessionEvent.timestamp;
                            activity.userId = sessionEvent.userId;
                            activity.sessionId = sessionEvent.sessionId;

                            List<ActivityDuration> list = new ArrayList<ActivityDuration>();
                            list.add(activity);

                            session.setActivityDurationList(list);

                            return new Tuple2<>(session.sessionId, session);
                        }).reduceByKey((a, b) -> {
                            SessionWithActivity session = new SessionWithActivity();
                            session.userId = a.userId;
                            session.sessionId = a.sessionId;
                            session.startTime = Math.min(a.startTime, b.startTime);
                            session.endTime = Math.max(a.endTime, b.endTime);
                            if(a.getStatus().equals("end") || b.getStatus().equals("end")) {
                                session.status = "end";
                            } else {
                                session.status = "active";
                            }
                            List<ActivityDuration> list = new LinkedList<ActivityDuration>();
                            list.addAll(a.getActivityDurationList());
                            list.addAll(b.getActivityDurationList());
                            Collections.sort(list);


                            List<ActivityDuration> newlist = new LinkedList<ActivityDuration>();

                            ActivityDuration ac = list.get(0);
                            for(int i=1; i<list.size(); i++){
                                if(ac.getActivityType().equals(list.get(i).getActivityType())){
                                    ac.setEndTime(list.get(i).getEndTime());
                                } else {
                                    ac.setEndTime(list.get(i).getStartTime());
                                    newlist.add(ac);
                                    ac = list.get(i);
                                }
                            }
                            newlist.add(ac);
                            session.setActivityDurationList(newlist);

                            return session;
                        }).updateStateByKey(updateActivityDuration);

                activityInfo.foreachRDD((id, s) ->{
                    JavaRDD<ActivityDuration> durationJavaRDD = id.values().flatMap(sessionWithActivity -> sessionWithActivity.getActivityDurationList());
                    DataFrame df = hiveContext.createDataFrame(durationJavaRDD, ActivityDuration.class);

                    df.insertInto("sessionevents", true);

                    System.out.println("-----sessionevents-----");
                    hiveContext.sql("SELECT * FROM sessionevents").show();
                });

                activityInfo.print();

                return jssc;
            }
        };

        JavaStreamingContext jssc = JavaStreamingContext.getOrCreate(checkpointDir, contextFactory);
        
        //TODO for local running
        jssc.sc().setLogLevel("ERROR");

        jssc.start();
        jssc.awaitTermination();
    }
}
