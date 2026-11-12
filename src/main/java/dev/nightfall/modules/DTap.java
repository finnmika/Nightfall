package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;

public class DTap extends Module {
   private boolean fired;

   public DTap() {
      super("DTap", "Fuehrt die AutoPvP-Combo einmal per Tastendruck aus.", Category.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.fired = false;
      ModuleManagerBridge.trigger();
   }

   @Override
   public void onTick() {
      if (!this.fired) {
         this.fired = true;
      }
      this.setEnabled(false);
   }

   private static final class ModuleManagerBridge {
      private static void trigger() {
         dev.nightfall.module.ModuleManager.get(AutoPvp.class).triggerDTab();
      }
   }
}
