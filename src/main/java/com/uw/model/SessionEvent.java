package com.uw.model;

import org.json.simple.JSONObject;

import java.io.Serializable;

public class SessionEvent implements Serializable{
    public String userId;
    public String sessionId;
    public String activityType;
    public Long timestamp;

    @Override
    public String toString(){
        JSONObject obj = new JSONObject();
        obj.put("userId", userId);
        obj.put("sessionId", sessionId);
        obj.put("activityType", activityType);
        obj.put("timestamp", timestamp);
        return obj.toJSONString();
    }
}
