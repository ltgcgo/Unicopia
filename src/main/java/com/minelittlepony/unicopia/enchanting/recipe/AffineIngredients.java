package com.minelittlepony.unicopia.enchanting.recipe;

import java.util.Map;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

public class AffineIngredients extends JsonDataLoader implements IdentifiableResourceReloadListener {
    private static final Identifier ID = new Identifier("unicopia", "ingredients");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final AffineIngredients INSTANCE = new AffineIngredients();

    public static AffineIngredients getInstance() {
        return INSTANCE;
    }

    private final Map<Identifier, SpellIngredient> storedIngredients = Maps.newHashMap();

    AffineIngredients() {
        super(GSON, "ingredients");
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    protected void apply(Map<Identifier, JsonObject> data, ResourceManager manager, Profiler profiled) {
        storedIngredients.clear();
        data.forEach((id, json) -> {
            SpellIngredient ingredient = SpellIngredient.parse(json.get("items"));

            if (ingredient != null) {
                storedIngredients.put(id, ingredient);
            }
        });
    }

    public SpellIngredient getIngredient(Identifier res) {
        SpellIngredient result = storedIngredients.get(res);

        if (result == null) {
            new RuntimeException("Ingredient `" + res + "` was not found.").printStackTrace();
            return SpellIngredient.EMPTY;
        }

        return result;
    }
}
