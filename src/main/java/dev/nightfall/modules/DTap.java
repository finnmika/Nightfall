package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import dev.nightfall.util.InvUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_746;

public class DTap extends Module {
   private final NumberSetting targetRange = this.add(new NumberSetting("Ziel-Reichweite", 6.0, 3.0, 12.0, 0.5));
   private int phase;
   private int ticks;
   private int targetId = -1;
   private class_2338 firstBase;
   private class_2338 secondBase;

   public DTap() {
      super("DTap", "Eigenstaendige Double-Crystal-Kombo mit genau zwei Crystals.", Category.COMBAT);
   }

   @Override
   protected void onEnable() { this.reset(); }

   @Override
   protected void onDisable() { this.reset(); }

   @Override
   public void onWorldLeave() { this.reset(); }

   private void reset() {
      phase = 0;
      ticks = 0;
      targetId = -1;
      firstBase = null;
      secondBase = null;
   }

   private boolean validBase(class_2338 base) {
      class_2680 state = mc.field_1687.method_8320(base);
      class_2338 support = base.method_10074();
      if (!state.method_45474() || mc.field_1687.method_8320(support).method_26220(mc.field_1687, support).method_1110()) return false;
      if (!mc.field_1687.method_8320(base.method_10084()).method_26215()
         || !mc.field_1687.method_8320(base.method_10084().method_10084()).method_26215()) return false;
      class_238 space = new class_238(base.method_10263(), base.method_10264() + 1, base.method_10260(),
         base.method_10263() + 1, base.method_10264() + 3, base.method_10260() + 1);
      return mc.field_1687.method_8335(null, space).isEmpty();
   }

   private boolean usableBase(class_2338 base) {
      if (mc.field_1687.method_8320(base).method_27852(class_2246.field_9987)) {
         class_2338 support = base.method_10074();
         return !mc.field_1687.method_8320(support).method_26220(mc.field_1687, support).method_1110()
            && mc.field_1687.method_8320(base.method_10084()).method_26215()
            && mc.field_1687.method_8320(base.method_10084().method_10084()).method_26215();
      }
      return validBase(base);
   }

   private class_2338[] findBases(class_746 player, class_1657 target) {
      double dx = target.method_23317() - player.method_23317();
      double dz = target.method_23321() - player.method_23321();
      int sx = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? 1 : -1) : 0;
      int sz = sx == 0 ? (dz >= 0 ? 1 : -1) : 0;
      int[][] directions = {{sx, sz}, {-sz, sx}, {sz, -sx}, {-sx, -sz}};
      class_2338 feet = target.method_24515();
      for (int[] dir : directions) {
         class_2338 one = feet.method_10069(dir[0] * 2, -1, dir[1] * 2);
         class_2338 two = feet.method_10069(dir[0] * 4, -1, dir[1] * 2);
         if (usableBase(one) && usableBase(two)
            && player.method_5739(target) <= targetRange.get() + 3.0) return new class_2338[]{one, two};
      }
      return null;
   }

   private boolean placeObsidian(class_746 player, class_2338 base) {
      if (mc.field_1687.method_8320(base).method_27852(class_2246.field_9987)) return true;
      int slot = InvUtil.ensureHotbar(class_1802.field_8281);
      if (slot < 0) return false;
      class_2338 support = base.method_10074();
      if (mc.field_1687.method_8320(support).method_26220(mc.field_1687, support).method_1110()) return false;
      InvUtil.select(slot);
      class_243 hit = class_243.method_24953(support).method_1031(0.5, 1.0, 0.5);
      mc.field_1761.method_2896(player, class_1268.field_5808, new class_3965(hit, class_2350.field_11036, support, false));
      player.method_6104(class_1268.field_5808);
      return false;
   }

   private class_1511 findCrystal(class_2338 base) {
      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (entity instanceof class_1511 crystal && crystal.method_5805()
            && new class_243(crystal.method_23317(), crystal.method_23318(), crystal.method_23321())
               .method_1022(new class_243(base.method_10263() + 0.5, base.method_10264() + 1.0, base.method_10260() + 0.5)) < 1.25) return crystal;
      }
      return null;
   }

   private void placeAndBreak(class_746 player, class_2338 base, int nextPhase) {
      class_1511 crystal = findCrystal(base);
      if (crystal == null) {
         int slot = InvUtil.ensureHotbar(class_1802.field_8301);
         if (slot < 0) { this.setEnabled(false); return; }
         InvUtil.select(slot);
         class_243 hit = new class_243(base.method_10263() + 0.5, base.method_10264() + 1.0, base.method_10260() + 0.5);
         mc.field_1761.method_2896(player, class_1268.field_5808, new class_3965(hit, class_2350.field_11036, base, false));
         player.method_6104(class_1268.field_5808);
      } else {
         mc.field_1761.method_2918(player, crystal);
         player.method_6104(class_1268.field_5808);
         this.phase = nextPhase;
         this.ticks = 0;
      }
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1687 == null || mc.field_1761 == null || mc.field_1755 != null) return;
      if (phase == 0) {
         class_1657 target = CombatUtil.nearestPlayer(targetRange.get());
         if (target == null || target.method_24828()) return;
         if (InvUtil.findInventory(class_1802.field_8281) < 2 || InvUtil.findInventory(class_1802.field_8301) < 2) return;
         class_2338[] bases = findBases(player, target);
         if (bases == null) return;
         firstBase = bases[0];
         secondBase = bases[1];
         targetId = target.method_5628();
         phase = 1;
         ticks = 0;
      } else {
         class_1657 target = CombatUtil.nearestPlayer(targetRange.get());
         if (target == null || target.method_5628() != targetId || !target.method_5805() || ++ticks > 160) {
            this.setEnabled(false);
            return;
         }
         if (target.method_24828()) return;
         switch (phase) {
            case 1:
               if (placeObsidian(player, firstBase)) phase = 2;
               break;
            case 2:
               if (placeObsidian(player, secondBase)) phase = 3;
               break;
            case 3:
               placeAndBreak(player, firstBase, 4);
               break;
            case 4:
               if (findCrystal(firstBase) == null) phase = 5;
               break;
            case 5:
               placeAndBreak(player, secondBase, 6);
               break;
            case 6:
               if (findCrystal(secondBase) == null) this.setEnabled(false);
               break;
            default:
               this.setEnabled(false);
         }
      }
   }
}
