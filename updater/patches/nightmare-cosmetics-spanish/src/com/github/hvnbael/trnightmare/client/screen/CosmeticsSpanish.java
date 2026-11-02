package com.github.hvnbael.trnightmare.client.screen;

import java.util.Locale;
import java.util.Map;

/** Display-only Spanish text. Registry IDs, option keys and saved values never pass through here. */
public final class CosmeticsSpanish {
    private static final Map<String,String> TEXT=Map.ofEntries(
        Map.entry("Cosmetics","Cosméticos"),Map.entry("Sections","Secciones"),
        Map.entry("Body","Cuerpo"),Map.entry("Dragon","Dragón"),Map.entry("No clan","Sin clan"),
        Map.entry("Your race","Tu raza"),Map.entry("Your clan","Tu clan"),
        Map.entry("From your skin","Colores de tu skin"),Map.entry("Default","Predeterminado"),
        Map.entry("Tip","Punta"),Map.entry("Match","Igualar"),Map.entry("Dragon Form","Forma de dragón"),
        Map.entry("Outline","Contorno"),Map.entry("Shown","Visible"),Map.entry("Hidden","Oculto"),
        Map.entry("Type","Tipo"),Map.entry("Race","Raza"),Map.entry("Storm","Tormenta"),
        Map.entry("Frost","Escarcha"),Map.entry("Flame","Fuego"),Map.entry("Earth","Tierra"),
        Map.entry("Holy","Sagrado"),Map.entry("Daemonic","Demoníaco"),Map.entry("Nether","Nether"),
        Map.entry("Ender","Ender"),Map.entry("Void","Vacío"),Map.entry("Death","Muerte"),
        Map.entry("Vampiric","Vampírico"),Map.entry("Water","Agua"),Map.entry("Sea","Mar"),
        Map.entry("Form","Forma"),Map.entry("Front","Frente"),Map.entry("Back","Espalda"),
        Map.entry("Face","Rostro"),Map.entry("Spin","Girar"),Map.entry("Fly","Volar"),
        Map.entry("Stand","De pie"),Map.entry("Swim","Nadar"),
        Map.entry("Scaled","Escamoso"),Map.entry("Metallic","Metálico"),Map.entry("Fleshy","Carnoso"),
        Map.entry("Rotting","Putrefacto"),Map.entry("Skeletal","Esquelético"),Map.entry("Smooth","Liso"),
        Map.entry("Active","Activo"),Map.entry("Inactive","Inactivo"),Map.entry("All","Todo"),
        Map.entry("Available","Disponible"),Map.entry("Unavailable","No disponible"),
        Map.entry("Granted","Concedido"),Map.entry("Locked","Bloqueado"),Map.entry("Layers","Capas"),
        Map.entry("None","Ninguno"),Map.entry("On","Activado"),Map.entry("Off","Desactivado"),
        Map.entry("Not your clan","No pertenece a tu clan"),Map.entry("Not your race","No pertenece a tu raza"),
        Map.entry("No cosmetics loaded","No hay cosméticos cargados"),
        Map.entry("Drag to turn, scroll to zoom","Arrastra para girar; usa la rueda para acercar"),
        Map.entry("Outfits granted by your skills. They vanish if you lose the skill.","Atuendos concedidos por tus habilidades. Desaparecen si pierdes la habilidad."),
        Map.entry("Daemon","Demonio"),Map.entry("Beastfolk","Bestias humanoides"),
        Map.entry("Chimera","Quimera"),Map.entry("Demon Clan","Clan demoníaco"),
        Map.entry("Fairy Clan","Clan de las hadas"),Map.entry("Goddess Clan","Clan de las diosas"),
        Map.entry("Demon Clan, of the royal line","Clan demoníaco, linaje real"),
        Map.entry("Chimera, wearing the Demon Clan look","Quimera con apariencia del clan demoníaco"),
        Map.entry("Chimera, wearing the Fairy Clan look","Quimera con apariencia del clan de las hadas"),
        Map.entry("Chimera, wearing the Goddess Clan look","Quimera con apariencia del clan de las diosas"),
        Map.entry("Elf","Elfo"),Map.entry("Goblin","Goblin"),Map.entry("Harpy","Arpía"),
        Map.entry("Lizardman","Hombre lagarto"),Map.entry("Merfolk","Tritón"),Map.entry("Mystic Fox","Zorro místico"),
        Map.entry("Oni","Oni"),Map.entry("Slime","Slime"),Map.entry("Wight","Espectro"),
        Map.entry("Metallic bodies belong to the Scrap and Netherite dragon line","Los cuerpos metálicos requieren el linaje de dragón de chatarra o netherita")
    );

    private CosmeticsSpanish() {}

    public static String display(String text) {
        if(text==null || text.isEmpty()) return text;
        String exact=TEXT.get(text);
        if(exact!=null) return exact;
        if(text.equals(text.toUpperCase(Locale.ROOT))) {
            for(var entry:TEXT.entrySet())
                if(entry.getKey().equalsIgnoreCase(text)) return entry.getValue().toUpperCase(Locale.ROOT);
        }
        String prefix="Cosmetics for ",suffix=" races. Hide any you don't want.";
        if(text.startsWith(prefix) && text.endsWith(suffix)) {
            String race=text.substring(prefix.length(),text.length()-suffix.length());
            return "Cosméticos de "+display(race)+". Oculta los que no quieras.";
        }
        prefix="Only shows while you are of a ";suffix=" race.";
        if(text.startsWith(prefix) && text.endsWith(suffix)) {
            String race=text.substring(prefix.length(),text.length()-suffix.length());
            return "Solo se muestra mientras perteneces a la raza "+display(race)+".";
        }
        suffix=" bodies belong to the Zombie, Death and Gehenna dragon line";
        if(text.endsWith(suffix))
            return "Los cuerpos de tipo "+display(text.substring(0,text.length()-suffix.length()))+" requieren el linaje de dragón zombi, de la muerte o de Gehenna";
        return text;
    }
}
