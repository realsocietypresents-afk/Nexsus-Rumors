package com.nexuscraft.nexusrumors;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/** Everything the plugin says. */
final class Texts {

    static final String PREFIX = "§6[Rumors] ";

    private Texts() {
    }

    static void send(CommandSender to, String line) {
        to.sendMessage(line);
    }

    static void send(CommandSender to, List<String> lines) {
        for (String l : lines) {
            to.sendMessage(l);
        }
    }

    static List<String> cooldown(long seconds) {
        return List.of(PREFIX + "§7They have said their piece for now. Try again in " + seconds + "s.");
    }

    static List<String> nothingToSay(Temperament t) {
        return List.of(PREFIX + "§7This " + t.adjective() + " villager shrugs. Nothing worth repeating lately.");
    }

    static List<String> toldAll(Temperament t) {
        return List.of(PREFIX + "§7This " + t.adjective() + " villager has told you everything they know. Try another village.");
    }

    /** What a villager said. {@code before} is what you believed about the same rumor until now (null if it is new to you). */
    static List<String> told(String opener, Kind kind, Telling said, Telling before, boolean hadBefore) {
        List<String> out = new ArrayList<>();
        out.add(PREFIX + "§e" + kind.label() + " §f\"" + opener + Tale.sentence(kind, said) + "\"");
        if (hadBefore && before != null) {
            out.add(before.equals(said) ? "§7  Same story you already had." : "§e  That doesn't quite match what you heard before.");
        }
        out.add("§8  /rumors to read your notes.");
        return out;
    }

    static List<String> fromPlayer(String name, Kind kind, Telling said) {
        return List.of(PREFIX + "§e" + kind.label() + " §f" + name + " tells you: \"" + Tale.sentence(kind, said) + "\"",
                "§8  /rumors to read your notes.");
    }

    static List<String> checking(Kind kind, Telling t, int seconds) {
        return List.of(PREFIX + "§7You're where the rumor says: §f" + Grid.where(t.region()) + "§7. Stay about " + seconds
                + " seconds and look around.");
    }

    /** "a wary villager" / "Steve". */
    static String teller(String teller) {
        String[] p = teller.split(":", 3);
        if (p.length == 3 && p[0].equals("v")) {
            try {
                return "a " + Temperament.valueOf(p[2]).adjective() + " villager";
            } catch (IllegalArgumentException unknown) {
                return "a villager";
            }
        }
        return p.length >= 2 && p[0].equals("p") ? teller.substring(2) : teller;
    }

    static List<String> verdict(Verdict v, Kind kind, Telling believed, Fact fact, int xp) {
        List<String> out = new ArrayList<>();
        String tale = "§7\"" + Tale.sentence(kind, believed) + "\"";
        switch (v) {
            case TRUE -> {
                out.add(PREFIX + "§aIt was true! " + tale);
                out.add("§7  What really happened: §f" + Tale.sentence(fact.kind, fact.exact()));
            }
            case EXAGGERATED -> {
                out.add(PREFIX + "§eIt happened, but it grew in the telling. " + tale);
                out.add("§7  What really happened: §f" + Tale.sentence(fact.kind, fact.exact()));
            }
            case FALSE -> out.add(PREFIX + "§cNothing like that here. Somewhere along the way the tale went wrong. " + tale);
            case STALE -> out.add(PREFIX + "§7Too late, or nobody remembers: you can't tell any more. " + tale);
        }
        if (xp > 0) {
            out.add("§a  +" + xp + " experience for checking it yourself.");
        }
        return out;
    }

    static List<String> list(String name, List<Heard> entries, RumorsConfig c) {
        List<String> out = new ArrayList<>();
        out.add("§6§m     §r §6§l" + name + "'s Rumors §6§m     ");
        if (entries.isEmpty()) {
            out.add("§7You haven't heard anything yet. Sneak up to a villager and right-click to hear what they say.");
            return out;
        }
        int i = 1;
        for (Heard h : entries) {
            Telling t = h.consensus();
            String how = h.versions.size() == 1 ? "heard once" : "heard " + h.versions.size() + "x, " + h.agreeing() + " agree";
            out.add(" §e#" + i++ + " §7[" + h.kind.label() + "] §f" + Tale.sentence(h.kind, t) + " §8(" + how
                    + (h.unanimous() ? "" : ", told differently") + ")");
        }
        out.add("§7/rumors <#> reads one closely. Stand where it says for " + c.checkSeconds + "s to find out if it's true.");
        return out;
    }

    static List<String> detail(int number, Heard h, RumorsConfig c) {
        List<String> out = new ArrayList<>();
        Telling t = h.consensus();
        out.add("§6§m   §r §6Rumor #" + number + " §7(" + h.kind.label() + ") §6§m   ");
        out.add("§fMost tellings agree: §e" + Tale.sentence(h.kind, t));
        out.add("§7Who told you:");
        for (Heard.Version v : h.versions) {
            boolean odd = !v.telling().region().equals(t.region()) || v.telling().number() != t.number();
            out.add(" " + (odd ? "§c" : "§a") + "- " + teller(v.teller()) + " §8(" + v.hops() + " step"
                    + (v.hops() == 1 ? "" : "s") + " from the event)§7: " + Tale.sentence(h.kind, v.telling()));
        }
        out.add("§7To check it, stand in §f" + Grid.where(t.region()) + " §7for " + c.checkSeconds
                + " seconds. §8/rumors tell <player> " + number + "  |  /rumors post " + number);
        return out;
    }

    static List<String> ledger(String name, RumorStore.Book b) {
        List<String> out = new ArrayList<>();
        out.add("§6§m     §r §6§l" + name + "'s Record §6§m     ");
        int n = b.checked();
        if (n == 0 && b.staleCount == 0) {
            out.add("§7You haven't checked a rumor yet. Go where one says and see for yourself.");
            return out;
        }
        out.add("§7Checked: §f" + n + " §8| §aTrue " + b.trueCount + " §8| §eExaggerated " + b.exaggeratedCount + " §8| §cFalse "
                + b.falseCount + (b.staleCount > 0 ? " §8| §7Too late " + b.staleCount : ""));
        if (n > 0) {
            out.add("§7Of the rumors you chased, §f" + Math.round(100.0 * b.trueCount / n) + "% §7held up.");
        }
        return out;
    }

    static List<String> help(boolean admin) {
        List<String> out = new ArrayList<>();
        out.add("§6[Rumors] §fRumors: heard from villagers, true or not");
        out.add("§e/rumors §7- what you've heard");
        out.add("§e/rumors <#> §7- one rumor, and who told you what");
        out.add("§e/rumors tell <player> <#> §7- pass one to a nearby player");
        out.add("§e/rumors post <#> §7- tell the nearest villager so it spreads");
        out.add("§e/rumors forget <#|all> §7- clear notes");
        out.add("§e/rumors ledger §7- how often you were right to trust");
        out.add("§e/rumors hide|show §7- keep your whereabouts out of rumors (or not)");
        out.add("§7Sneak and right-click a villager to hear one.");
        if (admin) {
            out.add("§c/rumors report <market|danger> [number] <text> §7- start a rumor by hand");
            out.add("§c/rumors stats | reload");
        }
        return out;
    }
}
