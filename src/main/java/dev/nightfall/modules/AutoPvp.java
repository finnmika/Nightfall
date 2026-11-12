package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import dev.nightfall.util.InvUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_304;
import net.minecraft.class_3532;
import net.minecraft.class_746;

public class AutoPvp extends Module {
   private final NumberSetting follow = this.add(new NumberSetting("Verfolgen bis (Bloecke)", 14.0, 4.0, 32.0, 1.0));
   private final NumberSetting stop = this.add(new NumberSetting("Abstand halten", 2.6, 1.5, 5.0, 0.1));
   private final NumberSetting reach = this.add(new NumberSetting("Angriffsreichweite", 3.2, 1.0, 6.0, 0.1));
   private final NumberSetting turnSpeed = this.add(new NumberSetting("Drehgeschwindigkeit", 16.0, 3.0, 35.0, 1.0));
   private final BoolSetting smoothLook = this.add(new BoolSetting("Smooth Rotation", true));
   private final BoolSetting attack = this.add(new BoolSetting("Angreifen", true));
   private final BoolSetting doubleTap = this.add(new BoolSetting("Double Tap (DTap)", false));
   private final NumberSetting doubleTapWindow = this.add(new NumberSetting("DTap-Zeitfenster (Ticks)", 18.0, 6.0, 40.0, 1.0));
   private final BoolSetting strafe = this.add(new BoolSetting("Strafen", true));
   private final BoolSetting jump = this.add(new BoolSetting("Springen bei Hindernis", true));
   private final BoolSetting useStunSlam = this.add(new BoolSetting("Stun-Slam (Windcharge -> Mace)", true));
   private final BoolSetting useShieldBreak = this.add(new BoolSetting("Schild mit Axt kontern", true));
   private final BoolSetting useAutoGap = this.add(new BoolSetting("AutoGap integrieren", true));
   private final BoolSetting useAutoTotem = this.add(new BoolSetting("AutoTotem integrieren", true));
   private final BoolSetting useCrystalAura = this.add(new BoolSetting("CrystalAura integrieren", false));
   private final BoolSetting useAutoAnchor = this.add(new BoolSetting("AutoAnchor integrieren", false));
   private final BoolSetting useMaceAssist = this.add(new BoolSetting("MaceAssist integrieren", true));
   private boolean driving;
   private boolean wasForward;
   private boolean wasSprint;
   private boolean wasLeft;
   private boolean wasRight;
   private boolean wasJump;
   private boolean ownsAutoGap;
   private boolean ownsAutoTotem;
   private boolean ownsCrystalAura;
   private boolean ownsAutoAnchor;
   private boolean ownsMaceAssist;
   private int strafeDir = 1;
   private int strafeTimer;
   private boolean dtapPending;
   private int dtapTargetId = -1;
   private int dtapTicks;
   private int shieldPreviousSlot = -1;
   private int shieldAxeSlot = -1;
   private int shieldTargetId = -1;
   private AutoPvp.SlamPhase slamPhase = AutoPvp.SlamPhase.NONE;
   private class_1657 slamTarget;
   private int slamTicks;
   private int originalSlot = -1;
   private int windSlot = -1;
   private int maceSlot = -1;

   public AutoPvp() {
      super("AutoPvP", "Kaempft situationsabhaengig mit sanfter Rotation und Umgebungschecks.", Category.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.syncAssistModules();
   }

   @Override
   protected void onDisable() {
      this.clearDoubleTap();
      this.cancelSlam(true);
      this.releaseShieldAxe();
      this.release();
      this.disableOwnedAssistModules();
   }

   private void syncAssistModules() {
      this.ownsAutoGap = this.syncModule(AutoGap.class, this.useAutoGap.get(), this.ownsAutoGap);
      this.ownsAutoTotem = this.syncModule(AutoTotem.class, this.useAutoTotem.get(), this.ownsAutoTotem);
      this.ownsCrystalAura = this.syncModule(CrystalAura.class, this.useCrystalAura.get(), this.ownsCrystalAura);
      this.ownsAutoAnchor = this.syncModule(AutoAnchor.class, this.useAutoAnchor.get(), this.ownsAutoAnchor);
      this.ownsMaceAssist = this.syncModule(MaceAssist.class, this.useMaceAssist.get(), this.ownsMaceAssist);
   }

   private <T extends Module> boolean syncModule(Class<T> type, boolean requested, boolean owned) {
      T module = ModuleManager.get(type);
      if (requested) {
         if (!module.isEnabled()) {
            module.setEnabled(true);
            return true;
         } else {
            return owned;
         }
      } else {
         if (owned && module.isEnabled()) {
            module.setEnabled(false);
         }

         return false;
      }
   }

   private void disableOwnedAssistModules() {
      this.disableIfOwned(AutoGap.class, this.ownsAutoGap);
      this.disableIfOwned(AutoTotem.class, this.ownsAutoTotem);
      this.disableIfOwned(CrystalAura.class, this.ownsCrystalAura);
      this.disableIfOwned(AutoAnchor.class, this.ownsAutoAnchor);
      this.disableIfOwned(MaceAssist.class, this.ownsMaceAssist);
      this.ownsAutoGap = this.ownsAutoTotem = this.ownsCrystalAura = this.ownsAutoAnchor = this.ownsMaceAssist = false;
   }

   private <T extends Module> void disableIfOwned(Class<T> type, boolean owned) {
      if (owned) {
         T module = ModuleManager.get(type);
         if (module.isEnabled()) {
            module.setEnabled(false);
         }
      }
   }

   private void clearDoubleTap() {
      this.dtapPending = false;
      this.dtapTargetId = -1;
      this.dtapTicks = 0;
   }

   private void releaseShieldAxe() {
      if (this.shieldAxeSlot >= 0 && mc.field_1724 != null && mc.field_1724.method_31548().method_67532() == this.shieldAxeSlot && this.shieldPreviousSlot >= 0
         )
       {
         InvUtil.select(this.shieldPreviousSlot);
      }

      this.shieldPreviousSlot = this.shieldAxeSlot = this.shieldTargetId = -1;
   }

   private void updateShieldAxe(class_1657 target) {
      if (this.useShieldBreak.get() && target != null && target.method_6039()) {
         if (this.shieldTargetId != -1 && this.shieldTargetId != target.method_5628()) {
            this.releaseShieldAxe();
         }

         if (this.shieldAxeSlot >= 0) {
            if (InvUtil.selected() != this.shieldAxeSlot) {
               this.shieldPreviousSlot = this.shieldAxeSlot = this.shieldTargetId = -1;
            }
         } else {
            int axe = InvUtil.ensureHotbar(class_1802.field_22025);
            if (axe < 0) {
               axe = InvUtil.ensureHotbar(class_1802.field_8556);
            }

            if (axe < 0) {
               axe = InvUtil.ensureHotbar(class_1802.field_8475);
            }

            if (axe >= 0) {
               this.shieldPreviousSlot = InvUtil.selected();
               this.shieldAxeSlot = axe;
               this.shieldTargetId = target.method_5628();
               InvUtil.select(axe);
            }
         }
      } else {
         this.releaseShieldAxe();
      }
   }

   private void release() {
      if (this.driving && mc.field_1690 != null) {
         mc.field_1690.field_1894.method_23481(this.wasForward);
         mc.field_1690.field_1867.method_23481(this.wasSprint);
         mc.field_1690.field_1913.method_23481(this.wasLeft);
         mc.field_1690.field_1849.method_23481(this.wasRight);
         mc.field_1690.field_1903.method_23481(this.wasJump);
         class_304.method_1424();
         this.driving = false;
      }
   }

   private void aimAt(double x, double y, double z) {
      class_746 p = mc.field_1724;
      if (p != null) {
         class_243 eye = p.method_33571();
         double dx = x - eye.field_1352;
         double dy = y - eye.field_1351;
         double dz = z - eye.field_1350;
         float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
         float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
         if (!this.smoothLook.get()) {
            p.method_36456(yaw);
            p.method_36457(pitch);
         } else {
            float max = this.turnSpeed.get().floatValue();
            p.method_36456(p.method_36454() + class_3532.method_15363(class_3532.method_15393(yaw - p.method_36454()), -max, max));
            p.method_36457(p.method_36455() + class_3532.method_15363(class_3532.method_15393(pitch - p.method_36455()), -max, max));
         }
      }
   }

   private boolean clearAbove(class_746 p, int height) {
      class_2338 feet = p.method_24515();

      for (int y = 1; y <= height; y++) {
         class_2338 pos = feet.method_10086(y);
         class_2680 state = mc.field_1687.method_8320(pos);
         if (!state.method_26220(mc.field_1687, pos).method_1110()) {
            return false;
         }
      }

      return !p.method_5799() && !p.method_5771() && !p.method_6128() && !p.method_5765();
   }

   private boolean safeForward(class_746 p, class_1657 target) {
      class_243 toward = new class_243(target.method_23317() - p.method_23317(), 0.0, target.method_23321() - p.method_23321());
      if (toward.method_1027() < 1.0E-5) {
         return true;
      } else {
         toward = toward.method_1029();
         class_2338 feet = class_2338.method_49637(p.method_23317() + toward.field_1352 * 0.85, p.method_23318(), p.method_23321() + toward.field_1350 * 0.85);
         class_2338 head = feet.method_10084();
         class_2338 floor = feet.method_10074();
         boolean blocked = !mc.field_1687.method_8320(feet).method_26220(mc.field_1687, feet).method_1110()
            || !mc.field_1687.method_8320(head).method_26220(mc.field_1687, head).method_1110();
         boolean supported = !mc.field_1687.method_8320(floor).method_26220(mc.field_1687, floor).method_1110();
         return !blocked && supported;
      }
   }

   private void cancelSlam(boolean restoreSlot) {
      if (mc.field_1690 != null) {
         mc.field_1690.field_1903.method_23481(this.wasJump);
      }

      if (restoreSlot && this.originalSlot >= 0 && mc.field_1724 != null) {
         InvUtil.select(this.originalSlot);
      }

      this.slamPhase = AutoPvp.SlamPhase.NONE;
      this.slamTarget = null;
      this.slamTicks = 0;
      this.originalSlot = this.windSlot = this.maceSlot = -1;
   }

   private boolean tickStunSlam(class_746 p, class_1657 target, double distance) {
      if (!this.useStunSlam.get()) {
         if (this.slamPhase != AutoPvp.SlamPhase.NONE) {
            this.cancelSlam(true);
         }

         return false;
      } else {
         if (this.slamPhase == AutoPvp.SlamPhase.NONE) {
            if (!p.method_24828() || distance < 3.5 || distance > 7.5 || !p.method_6057(target) || !this.clearAbove(p, 4)) {
               return false;
            }

            this.windSlot = InvUtil.ensureHotbar(class_1802.field_49098);
            this.maceSlot = InvUtil.ensureHotbar(class_1802.field_49814);
            if (this.windSlot < 0 || this.maceSlot < 0) {
               return false;
            }

            this.originalSlot = InvUtil.selected();
            this.slamTarget = target;
            this.slamPhase = AutoPvp.SlamPhase.JUMP;
            this.slamTicks = 0;
         }

         if (this.slamTarget == target && target.method_5805() && this.clearAbove(p, 3) && this.slamTicks++ <= 45) {
            switch (this.slamPhase) {
               case JUMP:
                  mc.field_1690.field_1903.method_23481(true);
                  if (!p.method_24828()) {
                     mc.field_1690.field_1903.method_23481(this.wasJump);
                     this.slamPhase = AutoPvp.SlamPhase.AIM_AND_CHARGE;
                     this.slamTicks = 0;
                  }

                  return true;
               case AIM_AND_CHARGE:
                  this.aimAt(target.method_23317(), p.method_23320() + 3.0, target.method_23321());
                  if (p.method_36455() <= -48.0F) {
                     InvUtil.select(this.windSlot);
                     mc.field_1761.method_2919(p, class_1268.field_5808);
                     p.method_6104(class_1268.field_5808);
                     this.slamPhase = AutoPvp.SlamPhase.RISING;
                     this.slamTicks = 0;
                  }

                  return true;
               case RISING:
                  if (p.method_18798().field_1351 > 0.12) {
                     this.slamPhase = AutoPvp.SlamPhase.FALLING;
                     InvUtil.select(this.maceSlot);
                  } else if (this.slamTicks > 8) {
                     this.cancelSlam(true);
                     return false;
                  }

                  return true;
               case FALLING:
                  InvUtil.select(this.maceSlot);
                  this.aimAt(target.method_23317(), target.method_23323(0.55), target.method_23321());
                  if (p.method_18798().field_1351 <= 0.0
                     && p.field_6017 >= 1.5
                     && p.method_5739(target) <= 3.5
                     && p.method_6057(target)
                     && p.method_7261(0.5F) >= 0.95F) {
                     mc.field_1761.method_2918(p, target);
                     p.method_6104(class_1268.field_5808);
                     this.cancelSlam(true);
                     return true;
                  }

                  if (p.method_24828()) {
                     this.cancelSlam(true);
                  }

                  return true;
               default:
                  return false;
            }
         } else {
            this.cancelSlam(true);
            return false;
         }
      }
   }

   @Override
   public void onWorldLeave() {
      this.clearDoubleTap();
      this.cancelSlam(true);
      this.releaseShieldAxe();
      this.release();
      this.disableOwnedAssistModules();
      this.driving = false;
      this.strafeTimer = 0;
   }

   @Override
   public void onTick() {
      this.syncAssistModules();
      class_746 p = mc.field_1724;
      if (p != null && mc.field_1755 == null && mc.field_1761 != null && mc.field_1687 != null) {
         class_1657 target = CombatUtil.nearestPlayer(this.follow.get());
         if (target == null) {
            this.clearDoubleTap();
            this.cancelSlam(true);
            this.releaseShieldAxe();
            this.release();
         } else {
            if (!this.doubleTap.get()) {
               this.clearDoubleTap();
            } else if (this.dtapPending && (this.dtapTargetId != target.method_5628() || this.dtapTicks-- <= 0)) {
               this.clearDoubleTap();
            }

            if (!this.driving) {
               this.wasForward = mc.field_1690.field_1894.method_1434();
               this.wasSprint = mc.field_1690.field_1867.method_1434();
               this.wasLeft = mc.field_1690.field_1913.method_1434();
               this.wasRight = mc.field_1690.field_1849.method_1434();
               this.wasJump = mc.field_1690.field_1903.method_1434();
            }

            this.aimAt(target.method_23317(), target.method_23323(0.5), target.method_23321());
            double distance = p.method_5739(target);
            if (this.tickStunSlam(p, target, distance)) {
               this.driving = true;
            } else {
               boolean approach = distance > this.stop.get() && this.safeForward(p, target);
               mc.field_1690.field_1894.method_23481(approach);
               mc.field_1690.field_1867.method_23481(approach && distance > this.stop.get() + 1.5);
               if (this.strafe.get() && distance <= this.reach.get() + 1.5) {
                  if (this.strafeTimer-- <= 0) {
                     this.strafeDir = -this.strafeDir;
                     this.strafeTimer = 16 + (int)(distance * 3.0);
                  }

                  mc.field_1690.field_1913.method_23481(this.strafeDir > 0);
                  mc.field_1690.field_1849.method_23481(this.strafeDir < 0);
               } else {
                  mc.field_1690.field_1913.method_23481(false);
                  mc.field_1690.field_1849.method_23481(false);
               }

               boolean obstacle = p.field_5976;
               mc.field_1690.field_1903.method_23481(this.jump.get() && obstacle && p.method_24828() && this.clearAbove(p, 2));
               this.driving = true;
               this.updateShieldAxe(target);
               if (this.attack.get() && !(distance > this.reach.get()) && p.method_6057(target) && !(p.method_7261(0.5F) < 0.95F)) {
                  if (this.shieldAxeSlot < 0) {
                     int swordSlot = InvUtil.findBestSword();
                     if (swordSlot < 0) {
                        return;
                     }

                     InvUtil.select(swordSlot);
                  }

                  mc.field_1761.method_2918(p, target);
                  p.method_6104(class_1268.field_5808);
                  if (this.doubleTap.get()) {
                     if (this.dtapPending) {
                        this.clearDoubleTap();
                     } else {
                        this.dtapPending = true;
                        this.dtapTargetId = target.method_5628();
                        this.dtapTicks = this.doubleTapWindow.getInt();
                     }
                  } else {
                     this.clearDoubleTap();
                  }
               }
            }
         }
      } else {
         this.cancelSlam(true);
         this.releaseShieldAxe();
         this.release();
      }
   }

   private static enum SlamPhase {
      NONE,
      JUMP,
      AIM_AND_CHARGE,
      RISING,
      FALLING;
   }
}
