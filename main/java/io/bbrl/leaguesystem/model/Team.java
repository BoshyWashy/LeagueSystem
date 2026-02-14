package io.bbrl.leaguesystem.model;

import java.util.*;

public class Team {
    private String id;
    private String name;
    private String hexColor = "#FFFFFF";
    private String ownerUuid;  // Main owner UUID
    private List<String> owners = new ArrayList<>();  // All owners including main
    private List<String> members = new ArrayList<>();
    private List<String> reserves = new ArrayList<>();

    public Team() {}

    public Team(String id, String name, String hexColor, String ownerUuid) {
        this.id = id;
        this.name = name;
        this.hexColor = hexColor;
        this.ownerUuid = ownerUuid;
        if (ownerUuid != null && !ownerUuid.isEmpty()) {
            this.owners.add(ownerUuid);  // Main owner is also in owners list - add only once
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHexColor() { return hexColor; }
    public void setHexColor(String hexColor) { this.hexColor = hexColor; }
    public String getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(String ownerUuid) {
        // If changing main owner, update owners list
        if (this.ownerUuid != null && !this.ownerUuid.equals(ownerUuid) && this.owners.contains(this.ownerUuid)) {
            // Don't remove old main owner from co-owners list, just update who is main
        }
        this.ownerUuid = ownerUuid;
    }
    public List<String> getOwners() { return owners; }
    public void setOwners(List<String> owners) {
        this.owners = owners;
        // Ensure main owner is in the list
        if (this.ownerUuid != null && !this.ownerUuid.isEmpty() && !this.owners.contains(this.ownerUuid)) {
            this.owners.add(0, this.ownerUuid);
        }
    }
    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }
    public List<String> getReserves() { return reserves; }
    public void setReserves(List<String> reserves) { this.reserves = reserves; }

    public String getChatColor() {
        return hexColor.startsWith("§") ? hexColor : "§" + hexColor;
    }

    public boolean isOwner(String uuid) {
        return owners.contains(uuid);
    }

    public boolean isMainOwner(String uuid) {
        return ownerUuid != null && ownerUuid.equals(uuid);
    }

    public void addOwner(String uuid) {
        if (uuid == null || uuid.isEmpty()) return;
        if (!owners.contains(uuid)) {
            owners.add(uuid);
        }
    }

    public void removeOwner(String uuid) {
        owners.remove(uuid);
        // If removing main owner, clear main owner uuid
        if (ownerUuid != null && ownerUuid.equals(uuid)) {
            ownerUuid = "";
        }
    }

    public void transferMainOwnership(String newMainOwnerUuid) {
        if (owners.contains(newMainOwnerUuid)) {
            // Move new main owner to front of list if not already
            owners.remove(newMainOwnerUuid);
            owners.add(0, newMainOwnerUuid);
            this.ownerUuid = newMainOwnerUuid;
        }
    }

    /**
     * Get unique owner UUIDs (no duplicates)
     */
    public List<String> getUniqueOwners() {
        List<String> unique = new ArrayList<>();
        for (String uuid : owners) {
            if (!unique.contains(uuid)) {
                unique.add(uuid);
            }
        }
        return unique;
    }
}