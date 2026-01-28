package org.zappier.zappierGames;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.zappier.zappierGames.manhunt.Manhunt;

public class ShieldSoundEnforcement implements Listener {

    private static final double SHIELD_BLOCK_ANGLE_COS = 0.0; // cos(90°) = 0 -> 180° total arc

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShieldBlock(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        // Player must be actively blocking
        if (!victim.isBlocking()) return;

        // Damage must be fully blocked
        if (event.getFinalDamage() > 0) return;

        boolean axeStun = false;

        Entity damager = event.getDamager();

        if (!(event.getDamager() instanceof Player plyr)) {return;}

        if (damager instanceof LivingEntity attacker) {
            if (!isAttackInShieldArc(victim, attacker)) {
                return;
            }

            ItemStack weapon = attacker.getEquipment() != null
                    ? attacker.getEquipment().getItemInMainHand()
                    : null;

            if (weapon != null) {
                int durabilityToTake = (int) event.getDamage() + 1;
                ItemStack shield = victim.getActiveItem();

                if (shield != null) {
                    int unbreakingLvl = shield.getEnchantmentLevel(Enchantment.UNBREAKING);
                    if (unbreakingLvl > 0 && Math.random() < (unbreakingLvl / (unbreakingLvl + 1.0))) {
                        durabilityToTake = 0;
                    }
                }

                if (durabilityToTake > 2 && shield != null && shield.getType() == Material.SHIELD) {
                    // 3. Use the Damageable meta (import org.bukkit.inventory.meta.Damageable)
                    if (shield.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {

                        int newDamage = damageable.getDamage() + durabilityToTake;

                        if (newDamage >= shield.getType().getMaxDurability()) {
                            shield.setAmount(0);
                            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                            victim.clearActiveItem();
                        } else {
                            damageable.setDamage(newDamage);
                            shield.setItemMeta(damageable);
                        }
                    }
                }
            }

            if (weapon != null && weapon.getType().name().endsWith("_AXE")) {
                axeStun = true;

                int cooldownTicks = 5 * 20;
                victim.setCooldown(Material.SHIELD, cooldownTicks);
                victim.clearActiveItem();
            }
        }

        Sound sound = axeStun
                ? Sound.ITEM_SHIELD_BREAK
                : Sound.ITEM_SHIELD_BLOCK;

        // Play sound for everyone EXCEPT the victim
        victim.getWorld().getPlayers().forEach(player -> {
            player.playSound(victim.getLocation(), sound, 0.85f, 1.0f);
            if (/*Manhunt.bothSounds > 0 && */sound == Sound.ITEM_SHIELD_BREAK) {player.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.85f, 1.0f);}
        });
        event.setCancelled(true);
    }

    private boolean isAttackInShieldArc(Player victim, LivingEntity attacker) {
        Location victimLoc = victim.getEyeLocation();
        Location attackerLoc = attacker.getEyeLocation();

        Vector victimDirection = victimLoc.getDirection().setY(0).normalize(); // ignore pitch
        Vector toAttacker = attackerLoc.toVector().subtract(victimLoc.toVector()).setY(0).normalize();

        double dot = victimDirection.dot(toAttacker);

        // dot >= 0 means angle <= 90° from forward → within 180° arc
        return dot >= SHIELD_BLOCK_ANGLE_COS;
    }
}
