package com.uw.model;

import org.json.simple.JSONObject;

import java.io.Serializable;

public class ActivityDuration implements Serializable, Comparable<ActivityDuration>{
    public String userId;
    public String sessionId;
    public String activityType;
    public Long startTime;
    public Long endTime;

    @Override
    public String toString(){
        JSONObject obj = new JSONObject();
        obj.put("userId", userId);
        obj.put("sessionId", sessionId);
        obj.put("activityType", activityType);
        obj.put("startTime", startTime);
        obj.put("endTime", endTime);
        return obj.toJSONString();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    @Override
    public int compareTo(ActivityDuration o) {
        return this.startTime.compareTo(o.getStartTime());
    }
}
