package io.bbrl.leaguesystem.service;

import io.bbrl.leaguesystem.model.League;
import java.util.Map;

public final class PointCalculator {
    /* always return primitive int */
    public static int getPointsFor(League league, int position) {
        if (position <= 0) return 0;
        String key = league.getConfig().getPointSystem();
        if (key.startsWith("custom:")) {
            String scale = key.substring(7);
            Map<Integer,Integer> table = league.getConfig().getCustomScales().get(scale);
            return (table == null) ? 0 : table.getOrDefault(position, 0);
        }
        /* fallback to built-in standard table */
        Map<Integer,Integer> std = league.getConfig().getCustomScales().get("standard");
        return (std == null) ? 0 : std.getOrDefault(position, 0);
    }
}