package com.uw.model;

import org.json.simple.JSONObject;

import java.io.Serializable;

public class Session implements Serializable{
    public String userId;
    public String sessionId;
    public Long startTime;
    public Long endTime;
    public String status;

    @Override
    public String toString(){
        JSONObject obj = new JSONObject();
        obj.put("userId", userId);
        obj.put("sessionId", sessionId);
        obj.put("startTime", startTime);
        obj.put("endTime", endTime);
        obj.put("status", status);
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
