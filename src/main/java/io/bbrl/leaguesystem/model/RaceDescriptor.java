package io.bbrl.leaguesystem.model;

import java.util.Objects;

public class RaceDescriptor {
    private String event;
    private String qualiHeatId;
    private String sprintHeatId;
    private String raceHeatId;

    public RaceDescriptor() {}
    public RaceDescriptor(String event, String qualiHeatId, String sprintHeatId, String raceHeatId) {
        this.event = event;
        this.qualiHeatId = qualiHeatId;
        this.sprintHeatId = sprintHeatId;
        this.raceHeatId = raceHeatId;
    }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getQualiHeatId() { return qualiHeatId; }
    public void setQualiHeatId(String qualiHeatId) { this.qualiHeatId = qualiHeatId; }
    public String getSprintHeatId() { return sprintHeatId; }
    public void setSprintHeatId(String sprintHeatId) { this.sprintHeatId = sprintHeatId; }
    public String getRaceHeatId() { return raceHeatId; }
    public void setRaceHeatId(String raceHeatId) { this.raceHeatId = raceHeatId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RaceDescriptor)) return false;
        RaceDescriptor that = (RaceDescriptor) o;
        return Objects.equals(event, that.event);
    }
    @Override
    public int hashCode() { return Objects.hash(event); }
}