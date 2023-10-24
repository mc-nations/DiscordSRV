/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2022 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package github.scarsz.discordsrv.modules.voice;

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.apache.commons.math3.ml.clustering.*;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Network {

    private final Set<UUID> players;
    private String channel;
    private boolean initialized = false;

    public Network(String channel) {
        this.players = Collections.emptySet();
        this.channel = channel;
    }

    public Network(Set<UUID> players) {
        this.players = players;

        DiscordSRV.debug(Debug.VOICE, "Network being made for " + players);

        List<Permission> allowedPermissions = new ArrayList<>(Arrays.asList(Permission.VOICE_SPEAK));
        List<Permission> deniedPermissions = new ArrayList<>(Arrays.asList(Permission.VOICE_CONNECT));

        if (VoiceModule.isVoiceChannelsVisible()) {
            allowedPermissions.add(Permission.VIEW_CHANNEL);
        } else {
            deniedPermissions.add(Permission.VIEW_CHANNEL);
        }

        VoiceModule.getCategory().createVoiceChannel(UUID.randomUUID().toString())
                .addPermissionOverride(
                        VoiceModule.getGuild().getPublicRole(),
                        allowedPermissions,
                        deniedPermissions
                )
                .addPermissionOverride(
                        VoiceModule.getGuild().getSelfMember(),
                        Arrays.asList(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS),
                        Collections.emptyList()
                )
                .queue(channel -> {
                    this.channel = channel.getId();
                    initialized = true;
                }, e -> {
                    DiscordSRV.error("Failed to create network for " + players + ": " + e.getMessage());
                    VoiceModule.get().getNetworks().remove(this);
                });
    }

    public Network engulf(Network network) {
        DiscordSRV.debug(Debug.VOICE, "Network " + this + " is engulfing " + network);
        players.addAll(network.players);
        network.players.clear();
        return this;
    }

    /**
     * @return true if the player is within the network strength or falloff ranges
     */
    public boolean isPlayerInRangeToBeAdded(Player player) {
        return players.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(p -> !p.equals(player))
                .filter(p -> p.getWorld().getName().equals(player.getWorld().getName()))
                .anyMatch(p -> VoiceModule.verticalDistance(p.getLocation(), player.getLocation()) <= VoiceModule.getVerticalStrength()
                        && VoiceModule.horizontalDistance(p.getLocation(), player.getLocation()) <= VoiceModule.getHorizontalStrength());
    }

    /**
     * @return true if the player is within the network strength and should be connected
     */
    public boolean isPlayerInRangeToStayConnected(Player player) {
        double falloff = VoiceModule.getFalloff();
        return players.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(p -> !p.equals(player))
                .filter(p -> p.getWorld().getName().equals(player.getWorld().getName()))
                .anyMatch(p -> VoiceModule.verticalDistance(p.getLocation(), player.getLocation()) <= VoiceModule.getVerticalStrength() + falloff
                        && VoiceModule.horizontalDistance(p.getLocation(), player.getLocation()) <= VoiceModule.getHorizontalStrength() + falloff);
    }

    public void clusterPlayersDBSCAN() {
        List<Point3D> points = new ArrayList<>();
        
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            double x = player.getLocation().getX();
            double y = player.getLocation().getY();
            double z = player.getLocation().getZ();
            Point3D point = new Point3D(x, y, z);
            points.add(point);
        }

        // Set DBSCAN parameters (you may adjust these based on your needs)
        double boundary = VoiceModule.getHorizontalStrength() + VoiceModule.getFalloff(); // Maximum distance for players to be in the same cluster



        KMeansPlusPlusClusterer<Point3D> clusterer = new KMeansPlusPlusClusterer<>(2, 2);
        List<CentroidCluster<Point3D>> clusters = clusterer.cluster(points);


        // We only have one cluster because all players outside the epsilon are treated
        // as noise and are removed

        if(clusters.size() < 2) return;
        EuclideanDistance distance = new EuclideanDistance();
        if(distance.compute(clusters.get(0).getCenter().getPoint(), clusters.get(1).getCenter().getPoint()) < boundary) return;
        CentroidCluster<Point3D> biggestCluster;
        if(clusters.get(0).getPoints().size() < clusters.get(1).getPoints().size()) {
            biggestCluster = clusters.get(1);
        } else {
            biggestCluster = clusters.get(0);
        }
        Cluster<Point3D> cluster = clusters.get(0);

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (!isPlayerInCluster(player, biggestCluster)) {
                // Player is not in the cluster, remove them from the network
                players.remove(uuid);
            }
        }
    }

    private boolean isPlayerInCluster(Player player, Cluster<Point3D> cluster) {
        double playerX = player.getLocation().getX();
        double playerY = player.getLocation().getY();
        double playerZ = player.getLocation().getZ();

        for (Point3D point : cluster.getPoints()) {
            double[] coordinates = point.getPoint();
            double x = coordinates[0];
            double y = coordinates[1];
            double z = coordinates[2];

            if (playerX == x && playerY == y && playerZ == z) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return true if the player is within the falloff range <strong>but not the strength range</strong>
     */
    public boolean isPlayerInsideFalloffZone(Player player) {
        double falloff = VoiceModule.getFalloff();
        double horizontalStrength = VoiceModule.getHorizontalStrength();
        double verticalStrength = VoiceModule.getHorizontalStrength();
        return players.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(p -> !p.equals(player))
                .filter(p -> p.getWorld().getName().equals(player.getWorld().getName()))
                .anyMatch(p -> {
                    double vertical = VoiceModule.verticalDistance(p.getLocation(), player.getLocation());
                    double horizontal = VoiceModule.horizontalDistance(p.getLocation(), player.getLocation());
                    return vertical > verticalStrength && vertical <= verticalStrength + falloff
                            && horizontal > horizontal && horizontal <= horizontalStrength + falloff;
                });
    }

    public void clear() {
        players.clear();
    }

    public void add(Player player) {
        players.add(player.getUniqueId());
    }

    public void add(UUID uuid) {
        players.add(uuid);
    }

    public void remove(Player player) {
        players.remove(player.getUniqueId());
    }

    public void remove(UUID uuid) {
        players.remove(uuid);
    }

    public boolean contains(Player player) {
        return players.contains(player.getUniqueId());
    }

    public boolean contains(UUID uuid) {
        return players.contains(uuid);
    }

    public int size() {
        return players.size();
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public VoiceChannel getChannel() {
        if (channel == null || channel.isEmpty()) return null;
        return VoiceModule.getGuild().getVoiceChannelById(channel);
    }

    public boolean isInitialized() {
        return initialized;
    }
}
