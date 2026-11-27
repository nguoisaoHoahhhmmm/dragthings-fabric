package dragthings.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * mob_drag.json: what may be carried and how. The shrink animation and egg
 * icon are visual and stay in the client config.
 *
 * mobPickupRange: 2-10 blocks.
 * extraAllowlist / extraBlacklist: entity ids, e.g. ["minecraft:enderman", "minecraft:witch"].
 * The allowlist beats the category switches; the blacklist beats everything.
 */
public final class MobDragConfig {

    public boolean enableMobDrag = true;
    public boolean allowPassive = true;
    public boolean allowNeutral = true;
    public boolean allowHostile = false;
    public boolean allowBoss = false;

    public List<String> extraAllowlist = new ArrayList<>();
    public List<String> extraBlacklist = new ArrayList<>();

    /** The mob can neither take nor deal damage while carried. */
    public boolean invulnerableWhileDragged = true;
    public int mobPickupRange = 5;

    private transient Set<String> allowSet;
    private transient Set<String> blockSet;

    void sanitize() {
        mobPickupRange = ConfigFiles.clamp(mobPickupRange, 2, 10);
        if (extraAllowlist == null) extraAllowlist = new ArrayList<>();
        if (extraBlacklist == null) extraBlacklist = new ArrayList<>();
        allowSet = new HashSet<>(extraAllowlist);
        blockSet = new HashSet<>(extraBlacklist);
    }

    public double getPickupRange() { return mobPickupRange; }
    public boolean isAllowlisted(String entityId) { return allowSet != null && allowSet.contains(entityId); }
    public boolean isBlacklisted(String entityId) { return blockSet != null && blockSet.contains(entityId); }
}
