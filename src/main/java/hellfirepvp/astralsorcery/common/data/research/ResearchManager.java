package hellfirepvp.astralsorcery.common.data.research;

import hellfirepvp.astralsorcery.AstralSorcery;
import hellfirepvp.astralsorcery.common.constellation.Constellation;
import hellfirepvp.astralsorcery.common.network.PacketChannel;
import hellfirepvp.astralsorcery.common.network.packet.server.PktSyncKnowledge;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * This class is part of the Astral Sorcery Mod
 * The complete source code for this mod can be found on github.
 * Class: ResearchManager
 * Created by HellFirePvP
 * Date: 07.05.2016 / 13:33
 */
public class ResearchManager {

    public static PlayerProgress clientProgress = new PlayerProgress();

    private static Map<UUID, PlayerProgress> playerProgressServer = new HashMap<>();

    @Nullable
    public static PlayerProgress getProgress(EntityPlayer player) {
        return getProgress(player.getUniqueID());
    }

    @Nullable
    public static PlayerProgress getProgress(UUID uuid) {
        PlayerProgress progress = playerProgressServer.get(uuid);
        if (progress == null) {
            loadPlayerKnowledge(uuid);
        }
        progress = playerProgressServer.get(uuid);
        if (progress == null) {
            AstralSorcery.log.warn("Failed to load AstralSocery Progress data!");
            AstralSorcery.log.warn("Erroneous file: " + uuid.toString() + ".astral");
        }
        return progress;
    }

    public static void wipeKnowledge(EntityPlayer p) {
        wipeFile(p);
        playerProgressServer.remove(p.getUniqueID());
        PktSyncKnowledge pkt = new PktSyncKnowledge(PktSyncKnowledge.STATE_WIPE);
        PacketChannel.CHANNEL.sendTo(pkt, (net.minecraft.entity.player.EntityPlayerMP) p);
    }

    public static void sendInitClientKnowledge(EntityPlayer p) {
        UUID uuid = p.getUniqueID();
        if (playerProgressServer.get(uuid) == null) {
            loadPlayerKnowledge(uuid);
        }
        if (playerProgressServer.get(uuid) == null) {
            AstralSorcery.log.warn("Failed to load AstralSocery Progress data for " + p.getName());
            AstralSorcery.log.warn("Erroneous file: " + uuid.toString() + ".astral");
            return;
        }
        pushProgressToClientUnsafe(p);
    }

    public static boolean discoverConstellations(Collection<Constellation> csts, EntityPlayer player) {
        PlayerProgress progress = getProgress(player);
        if(progress == null) return false;

        for (Constellation c : csts) {
            progress.discoverConstellation(c.getName());
        }
        pushProgressToClientUnsafe(player);
        savePlayerKnowledge(player);
        return true;
    }

    public static boolean discoverConstellation(Constellation c, EntityPlayer player) {
        PlayerProgress progress = getProgress(player);
        if(progress == null) return false;

        progress.discoverConstellation(c.getName());
        pushProgressToClientUnsafe(player);
        savePlayerKnowledge(player);
        return true;
    }

    public static boolean maximizeTier(EntityPlayer player) {
        PlayerProgress progress = getProgress(player);
        if(progress == null) return false;
        progress.setTierReached(ProgressionTier.values()[ProgressionTier.values().length - 1]);

        pushProgressToClientUnsafe(player);
        savePlayerKnowledge(player);
        return true;
    }

    public static Optional<ProgressionTier> stepTier(EntityPlayer player) {
        PlayerProgress progress = getProgress(player);
        if(progress == null) return Optional.of(null);
        if(!progress.stepTier()) {
            return Optional.empty();
        }

        pushProgressToClientUnsafe(player);
        savePlayerKnowledge(player);
        return Optional.of(progress.getTierReached());
    }

    protected static boolean forceUnsafeResearchStep(EntityPlayer player, ResearchProgression progression) {
        PlayerProgress progress = getProgress(player);
        if(progress == null) return false;
        progress.forceGainResearch(progression);

        pushProgressToClientUnsafe(player);
        savePlayerKnowledge(player);
        return true;
    }

    public static boolean forceMaximizeResearch(EntityPlayer player) {
        PlayerProgress progress = getProgress(player);
        if(progress == null) return false;
        for (ResearchProgression progression : ResearchProgression.values()) {
            progress.forceGainResearch(progression);
        }

        pushProgressToClientUnsafe(player);
        savePlayerKnowledge(player);
        return true;
    }

    private static void pushProgressToClientUnsafe(EntityPlayer p) {
        PlayerProgress progress = playerProgressServer.get(p.getUniqueID());
        PktSyncKnowledge pkt = new PktSyncKnowledge(PktSyncKnowledge.STATE_ADD);
        pkt.load(progress);
        PacketChannel.CHANNEL.sendTo(pkt, (net.minecraft.entity.player.EntityPlayerMP) p);
    }

    private static void wipeFile(EntityPlayer player) {
        new File(getPlayerDirectory(), player.getUniqueID().toString() + ".astral").delete();
    }

    public static void savePlayerKnowledge(EntityPlayer p) {
        savePlayerKnowledge(p.getUniqueID());
    }

    public static void savePlayerKnowledge(UUID pUUID) {
        if (playerProgressServer.get(pUUID) == null) return;
        String uuidStr = pUUID.toString();
        File dir = getPlayerDirectory();
        File playerFile = new File(dir, uuidStr + ".astral");
        try {
            NBTTagCompound cmp = new NBTTagCompound();
            playerProgressServer.get(pUUID).store(cmp);
            CompressedStreamTools.write(cmp, playerFile);
        } catch (IOException e) {}
    }

    public static void loadPlayerKnowledge(EntityPlayer p) {
        loadPlayerKnowledge(p.getUniqueID());
    }

    public static void loadPlayerKnowledge(UUID pUUID) {
        String uuidStr = pUUID.toString();
        File dir = getPlayerDirectory();
        File playerFile = new File(dir, uuidStr + ".astral");
        try {
            NBTTagCompound compound = CompressedStreamTools.read(playerFile);
            PlayerProgress progress = new PlayerProgress();
            if (compound != null) {
                progress.load(compound);
            }
            playerProgressServer.put(pUUID, progress);
        } catch (IOException e) {}
    }

    private static File getPlayerDirectory() {
        File wDir = DimensionManager.getWorld(0).getSaveHandler().getWorldDirectory();
        File pDir = new File(wDir, "playerdata");
        if (!pDir.exists()) {
            pDir.mkdirs();
        }
        return pDir;
    }

    public static void logoutResetClient(EntityPlayer player) {
        PktSyncKnowledge pkt = new PktSyncKnowledge(PktSyncKnowledge.STATE_WIPE);
        PacketChannel.CHANNEL.sendTo(pkt, (net.minecraft.entity.player.EntityPlayerMP) player);
    }

    public static void recieveProgressFromServer(PktSyncKnowledge message) {
        clientProgress = new PlayerProgress();
        clientProgress.receive(message);
    }
}