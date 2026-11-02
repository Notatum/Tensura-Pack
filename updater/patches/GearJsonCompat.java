package net.studio.redraven.tensura_ep_scaling.compat;

import com.google.gson.*;

/** Same optional maxEP default as Tensura 2.0.1.4's GearExistenceData codec. */
public final class GearJsonCompat {
    public static JsonElement get(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value == null && member.equals("maxEP") ? new JsonPrimitive(2_000_000) : value;
    }
}
