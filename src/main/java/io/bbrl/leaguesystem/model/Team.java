package io.bbrl.leaguesystem.model;

import java.util.*;

public class Team {
    private String id; // unique within league
    private String name;
    private String hexColor = "#FFFFFF";
    private String ownerUuid;
    private List<String> members = new ArrayList<>(); // uuid strings
    private List<String> reserves = new ArrayList<>();

    public Team() {}

    public Team(String id, String name, String hexColor, String ownerUuid) {
        this.id = id;
        this.name = name;
        this.hexColor = hexColor;
        this.ownerUuid = ownerUuid;
        this.members.add(ownerUuid);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHexColor() { return hexColor; }
    public void setHexColor(String hexColor) { this.hexColor = hexColor; }
    public String getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(String ownerUuid) { this.ownerUuid = ownerUuid; }
    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }
    public List<String> getReserves() { return reserves; }
    public void setReserves(List<String> reserves) { this.reserves = reserves; }
}
