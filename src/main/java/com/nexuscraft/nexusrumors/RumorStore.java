package com.nexuscraft.nexusrumors;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/** Everything the plugin remembers: what happened (facts), what villagers are saying (mills) and what each player has heard (books). */
final class RumorStore {

    /** One player's notebook and record. */
    static final class Book {
        String name = "";
        boolean hidden;
        int trueCount;
        int exaggeratedCount;
        int falseCount;
        int staleCount;
        final Map<String, Heard> heard = new LinkedHashMap<>();
        final Set<String> posted = new LinkedHashSet<>();

        int checked() {
            return trueCount + exaggeratedCount + falseCount;
        }
    }

    private final File factsFile;
    private final File rumorsFile;
    private final File playersFile;
    private final Logger log;
    long nextFact = 1;
    final Map<String, int[]> origins = new LinkedHashMap<>();
    final Map<String, Fact> facts = new LinkedHashMap<>();
    final Map<String, List<Rumor>> mills = new LinkedHashMap<>();
    final Map<UUID, Book> books = new LinkedHashMap<>();
    private boolean dirty;

    RumorStore(File folder, Logger log) {
        this.factsFile = new File(folder, "facts.yml");
        this.rumorsFile = new File(folder, "rumors.yml");
        this.playersFile = new File(folder, "players.yml");
        this.log = log;
    }

    Book book(UUID id) {
        return books.computeIfAbsent(id, k -> new Book());
    }

    void touch() {
        dirty = true;
    }

    boolean dirty() {
        return dirty;
    }

    List<Rumor> mill(Region r) {
        return mills.computeIfAbsent(r.key(), k -> new ArrayList<>());
    }

    int rumorCount() {
        int n = 0;
        for (List<Rumor> l : mills.values()) {
            n += l.size();
        }
        return n;
    }

    void load() {
        facts.clear();
        mills.clear();
        books.clear();
        origins.clear();
        YamlConfiguration f = YamlConfiguration.loadConfiguration(factsFile);
        nextFact = Math.max(1, f.getLong("next", 1L));
        for (String line : f.getStringList("origins")) {
            List<String> p = Codec.split(line);
            if (p.size() == 3) {
                try {
                    origins.put(p.get(0), new int[]{Integer.parseInt(p.get(1)), Integer.parseInt(p.get(2))});
                } catch (NumberFormatException skip) {
                    // a damaged origin is simply worked out again from the world's spawn
                }
            }
        }
        for (String line : f.getStringList("facts")) {
            Fact fact = parseFact(Codec.split(line));
            if (fact != null) {
                facts.put(fact.id, fact);
            }
        }
        YamlConfiguration r = YamlConfiguration.loadConfiguration(rumorsFile);
        for (String line : r.getStringList("rumors")) {
            List<String> p = Codec.split(line);
            Telling t = Codec.parseTelling(p, 3);
            if (p.size() >= 3 && t != null) {
                try {
                    mills.computeIfAbsent(p.get(0), k -> new ArrayList<>()).add(new Rumor(p.get(1), t, Integer.parseInt(p.get(2))));
                } catch (NumberFormatException skip) {
                    // a damaged rumor is dropped
                }
            }
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(playersFile);
        for (String key : y.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException notAPlayer) {
                continue;
            }
            Book b = new Book();
            b.name = y.getString(key + ".name", "");
            b.hidden = y.getBoolean(key + ".hidden", false);
            b.trueCount = Math.max(0, y.getInt(key + ".true", 0));
            b.exaggeratedCount = Math.max(0, y.getInt(key + ".exaggerated", 0));
            b.falseCount = Math.max(0, y.getInt(key + ".false", 0));
            b.staleCount = Math.max(0, y.getInt(key + ".stale", 0));
            b.posted.addAll(y.getStringList(key + ".posted"));
            for (String line : y.getStringList(key + ".heard")) {
                readHeard(b, Codec.split(line));
            }
            books.put(id, b);
        }
        dirty = false;
    }

    private static Fact parseFact(List<String> p) {
        if (p.size() < 13) {
            return null;
        }
        Kind kind = Kind.parse(p.get(1));
        if (kind == null) {
            return null;
        }
        try {
            UUID subjectId = p.get(10).isEmpty() ? null : UUID.fromString(p.get(10));
            return new Fact(p.get(0), kind, new Region(p.get(2), Integer.parseInt(p.get(3)), Integer.parseInt(p.get(4))), p.get(5),
                    p.get(6), p.get(7), Integer.parseInt(p.get(8)), p.get(9), subjectId, Long.parseLong(p.get(11)), Long.parseLong(p.get(12)));
        } catch (IllegalArgumentException damaged) {
            return null;
        }
    }

    private static void readHeard(Book b, List<String> p) {
        Kind kind = p.size() > 1 ? Kind.parse(p.get(1)) : null;
        Telling t = Codec.parseTelling(p, 6);
        if (kind == null || t == null || p.size() < 6) {
            return;
        }
        try {
            Heard h = b.heard.computeIfAbsent(p.get(0), k -> new Heard(p.get(0), kind));
            long lastAt = Long.parseLong(p.get(2));
            h.versions.add(new Heard.Version(p.get(3), Integer.parseInt(p.get(4)), Long.parseLong(p.get(5)), t));
            h.lastAt = Math.max(h.lastAt, lastAt);
        } catch (NumberFormatException skip) {
            // a damaged account is dropped
        }
    }

    void save() {
        YamlConfiguration f = new YamlConfiguration();
        f.set("next", nextFact);
        List<String> origin = new ArrayList<>();
        for (Map.Entry<String, int[]> e : origins.entrySet()) {
            origin.add(Codec.join(List.of(e.getKey(), Integer.toString(e.getValue()[0]), Integer.toString(e.getValue()[1]))));
        }
        f.set("origins", origin);
        List<String> factLines = new ArrayList<>();
        for (Fact x : facts.values()) {
            factLines.add(Codec.join(List.of(x.id, x.kind.name(), x.region.world(), Integer.toString(x.region.cx()),
                    Integer.toString(x.region.cz()), x.biome, x.detail, x.extra, Integer.toString(x.number), x.subject,
                    x.subjectId == null ? "" : x.subjectId.toString(), Long.toString(x.createdAt), Long.toString(x.expiresAt))));
        }
        f.set("facts", factLines);
        YamlConfiguration r = new YamlConfiguration();
        List<String> rumorLines = new ArrayList<>();
        for (Map.Entry<String, List<Rumor>> e : mills.entrySet()) {
            for (Rumor x : e.getValue()) {
                List<String> fields = new ArrayList<>(List.of(e.getKey(), x.factId(), Integer.toString(x.hops())));
                fields.addAll(Codec.tellingFields(x.telling()));
                rumorLines.add(Codec.join(fields));
            }
        }
        r.set("rumors", rumorLines);
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Book> e : books.entrySet()) {
            String k = e.getKey().toString();
            Book b = e.getValue();
            y.set(k + ".name", b.name);
            y.set(k + ".hidden", b.hidden);
            y.set(k + ".true", b.trueCount);
            y.set(k + ".exaggerated", b.exaggeratedCount);
            y.set(k + ".false", b.falseCount);
            y.set(k + ".stale", b.staleCount);
            y.set(k + ".posted", new ArrayList<>(b.posted));
            List<String> heard = new ArrayList<>();
            for (Heard h : b.heard.values()) {
                for (Heard.Version v : h.versions) {
                    List<String> fields = new ArrayList<>(List.of(h.factId, h.kind.name(), Long.toString(h.lastAt), v.teller(),
                            Integer.toString(v.hops()), Long.toString(v.at())));
                    fields.addAll(Codec.tellingFields(v.telling()));
                    heard.add(Codec.join(fields));
                }
            }
            y.set(k + ".heard", heard);
        }
        boolean ok = write(factsFile, f) & write(rumorsFile, r) & write(playersFile, y);
        if (ok) {
            dirty = false;
        }
    }

    private boolean write(File file, YamlConfiguration y) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            File tmp = new File(file.getPath() + ".tmp");
            y.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            log.warning("Could not save " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }
}
