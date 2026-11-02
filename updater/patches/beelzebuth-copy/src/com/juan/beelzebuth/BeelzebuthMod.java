package com.juan.beelzebuth;

import com.juan.beelzebuth.menu.BeelzebuthMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod("beelzebuth")
public final class BeelzebuthMod {
    public static final String MOD_ID="beelzebuth";
    public BeelzebuthMod(IEventBus bus) {
        BeelzebuthMenus.register(bus);
        BeelzebuthRegistry.register();
        System.out.println("[Beelzebuth] 1.3.8: depredación de 25 bloques, estómago persistente, copia instantánea y Voz del Mundo.");
    }
}
