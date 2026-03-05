package com.example.examplemod;

import com.lupicus.cc.manager.ClaimManager;
import com.lupicus.cc.manager.ClaimManager.ClaimInfo;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

@Mod("claimchunkbluemap")
public class ExampleMod {

    private int tickCounter = 0;
    private int lastClaimCount = -1;
    private final Map<String, MarkerSet> dimensionLayers = new HashMap<>();

    public ExampleMod() {
        MinecraftForge.EVENT_BUS.register(this);

        BlueMapAPI.onEnable(api -> {
            api.getMaps().forEach(map -> {
                MarkerSet layer = MarkerSet.builder()
                        .label("Player Claims")
                        .defaultHidden(false)
                        .build();
                map.getMarkerSets().put("claim-chunk-layer", layer);
                dimensionLayers.put(map.getId(), layer);
            });
        });
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        // Check every 30 seconds (600 ticks)
        if (tickCounter >= 600) {
            tickCounter = 0;

            // Only run the heavy update if the number of claims actually changed
            int currentCount = ClaimManager.mapInfo.size();
            if (currentCount != lastClaimCount) {
                lastClaimCount = currentCount;
                updateClaimsOnMap();
            }
        }
    }

    private void updateClaimsOnMap() {
        BlueMapAPI.getInstance().ifPresent(api -> {
            // Clear all existing markers first
            dimensionLayers.values().forEach(layer -> layer.getMarkers().clear());

            for (ClaimInfo claim : ClaimManager.mapInfo.values()) {
                // 1. GRID SNAPPING
                int minX = (claim.pos.pos().getX() >> 4) << 4;
                int minZ = (claim.pos.pos().getZ() >> 4) << 4;
                int maxX = minX + 16;
                int maxZ = minZ + 16;

                // 2. PLAYER DATA
                String ownerName = ClaimManager.getName(claim);
                String ownerUUID = claim.owner.toString();

                // 3. COLOR GENERATION (Using 0-255 scale)
                int hash = claim.owner.hashCode();
                int r = (hash & 0xFF0000) >> 16;
                int g = (hash & 0x00FF00) >> 8;
                int b = (hash & 0x0000FF);

                // 100 is roughly 40% transparency (0 is invisible, 255 is solid)
                Color playerColor = new Color(r, g, b, .5f);
                Color invisibleBorder = new Color(0, 0, 0, 0);

                // 4. HTML LABEL
                String htmlLabel = "<div style='display: flex; align-items: center; gap: 8px; padding: 4px; font-weight: bold;'>" +
                        "<img src='https://crafthead.net/avatar/" + ownerUUID + "/32' " +
                        "style='width: 32px; height: 32px; image-rendering: pixelated; border-radius: 4px;'>" +
                        "<span>Claimed by: " + ownerName + "</span>" +
                        "</div>";

                // 5. BUILD MARKER
                Shape chunkShape = Shape.createRect(minX, minZ, maxX, maxZ);
                ExtrudeMarker marker = ExtrudeMarker.builder()
                        .label(htmlLabel)
                        .shape(chunkShape, 64.1f, 64.1f)
                        .lineColor(invisibleBorder)
                        .fillColor(playerColor)
                        .build();

                // 6. DEPTH TEST HACK
                try {
                    java.lang.reflect.Field field = marker.getClass().getDeclaredField("depthTest");
                    field.setAccessible(true);
                    field.set(marker, false);
                } catch (Exception ignored) {}

                // 7. INJECT INTO LAYERS
                String markerId = "claim_" + minX + "_" + minZ;
                String dimName = claim.pos.dimension().location().getPath();

                api.getMaps().forEach(map -> {
                    // Logic to match overworld/nether/end maps correctly
                    if (map.getId().contains(dimName) || (dimName.equals("overworld") && map.getId().equals("world"))) {
                        MarkerSet layer = dimensionLayers.get(map.getId());
                        if (layer != null) {
                            layer.getMarkers().put(markerId, marker);
                        }
                    }
                });
            }
        });
    }
}