package net.spell_engine.api.spell.registry;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;

public class SpellRegistry {
    /**
     * Using vanilla name space on purpose!
     * So spell data file path looks like this:
     * `data/MOD/spell/SPELL.json`
     * instead of this:
     * `data/MOD/spell_engine/spell/SPELL.json`
     */
    public static final Identifier ID = Identifier.ofVanilla("spell");
    public static final RegistryKey<Registry<Spell>> KEY = RegistryKey.ofRegistry(ID);
    public static Registry<Spell> from(World world) {
        return world.getRegistryManager().get(KEY);
    }
    
    private static final Gson gson = new GsonBuilder().create();
    public static final Codec<Spell> LOCAL_CODEC = Codecs.JSON_ELEMENT.xmap(
            json -> {
                return gson.fromJson(json, Spell.class);
            },
            spell -> {
                JsonElement jsonElement = gson.toJsonTree(spell);
                return jsonElement;
            }
    );

    public static final Codec<Spell> NETWORK_CODEC = Codec.STRING.comapFlatMap(
            encoded -> {
                var bytes = encoded.getBytes();
                String json;
                try {
                    json = decompress(Base64.getDecoder().decode(bytes));
                } catch (Exception e) {
                    return DataResult.error(null);
                }
                var spell = gson.fromJson(json, Spell.class);
                return DataResult.success(spell);
            },
            spell -> {
                var json = gson.toJson(spell);
                try {
                    return Base64.getEncoder().encodeToString(compress(json));
                } catch (Exception e) {
                    return "";
                }
            }
    );

    public static RegistryEntryList.Named<Spell> find(World world, Identifier tagId) {
        var manager = world.getRegistryManager();
        var lookup = manager.createRegistryLookup().getOrThrow(KEY); // RegistryEntryLookup<Spell>
        var tag = TagKey.of(KEY, tagId);
        return lookup.getOrThrow(tag);
    }

    public static List<RegistryEntry<Spell>> entries(World world, @Nullable Identifier id) {
        try {
            return find(world, id).stream().toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public static List<RegistryEntry<Spell>> entries(World world, @Nullable String pool) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        var id = Identifier.of(pool);
        return entries(world, id);
    }

    public static Stream<RegistryEntry.Reference<Spell>> stream(World world) {
        var manager = world.getRegistryManager();
        var registry = manager.get(KEY);
        return registry.streamEntries();
    }
    
// Utility method to compress a string using Deflater
private static byte[] compress(String str) throws Exception {
    var deflater = new Deflater();
    deflater.setInput(str.getBytes());
    deflater.finish();

    try (var outputStream = new ByteArrayOutputStream()) {
        var buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        return outputStream.toByteArray();
    } finally {
        deflater.end();
    }
}

// Utility method to decompress a byte array using Inflater
private static String decompress(byte[] compressedBytes) throws Exception {
    var inflater = new Inflater();
    inflater.setInput(compressedBytes);

    try (var outputStream = new ByteArrayOutputStream()) {
        var buffer = new byte[1024];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        return outputStream.toString();
    } finally {
        inflater.end();
    }
}
}