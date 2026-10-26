package com.nexuscraft.nexusrumors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** /rumors: read what you've heard, pass it on, check it, and for admins start one by hand. */
public final class RumorCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("tell", "post", "forget", "ledger", "hide", "show", "help");
    private static final List<String> ADMIN_SUBS = List.of("report", "stats", "reload");

    private final RumorService service;
    private Supplier<List<String>> reload = List::of;

    RumorCommand(RumorService service) {
        this.service = service;
    }

    void setReload(Supplier<List<String>> reload) {
        this.reload = reload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("nexusrumors.admin");
        if (ADMIN_SUBS.contains(sub) && !admin) {
            sender.sendMessage("§cYou may not do that.");
            return true;
        }
        if (!sub.isEmpty() && !ADMIN_SUBS.contains(sub) && !sender.hasPermission("nexusrumors.use")) {
            sender.sendMessage("§cYou may not use rumors.");
            return true;
        }
        switch (sub) {
            case "" -> mine(sender, null);
            case "help" -> Texts.send(sender, Texts.help(admin));
            case "tell" -> tell(sender, args);
            case "post" -> post(sender, args);
            case "forget" -> forget(sender, args);
            case "ledger" -> ledger(sender);
            case "hide" -> hide(sender, true);
            case "show" -> hide(sender, false);
            case "report" -> report(sender, args);
            case "stats" -> stats(sender);
            case "reload" -> reload(sender);
            default -> {
                Integer n = number(args[0]);
                if (n == null) {
                    sender.sendMessage("§cNot sure what you mean. /rumors help");
                } else {
                    mine(sender, n);
                }
            }
        }
        return true;
    }

    private static Integer number(String s) {
        try {
            return Integer.valueOf(s.startsWith("#") ? s.substring(1) : s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Player player(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        sender.sendMessage("§cOnly a player has rumors.");
        return null;
    }

    private void mine(CommandSender sender, Integer which) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        List<Heard> list = service.entries(p.getUniqueId());
        if (which == null) {
            Texts.send(sender, Texts.list(p.getName(), list, service.config()));
        } else if (which < 1 || which > list.size()) {
            sender.sendMessage("§cYou haven't heard a rumor #" + which + ". /rumors lists them.");
        } else {
            Texts.send(sender, Texts.detail(which, list.get(which - 1), service.config()));
        }
    }

    private void tell(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        Integer n = args.length >= 3 ? number(args[2]) : null;
        if (n == null) {
            sender.sendMessage("§cUsage: /rumors tell <player> <#>");
            return;
        }
        String problem = service.tell(p, args[1], n);
        if (problem != null) {
            sender.sendMessage("§c" + problem);
        }
    }

    private void post(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        Integer n = args.length >= 2 ? number(args[1]) : null;
        if (n == null) {
            sender.sendMessage("§cUsage: /rumors post <#>");
            return;
        }
        String problem = service.post(p, n);
        if (problem != null) {
            sender.sendMessage("§c" + problem);
        }
    }

    private void forget(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
            sender.sendMessage("§7Forgot " + service.forgetAll(p.getUniqueId()) + " rumor(s).");
            return;
        }
        Integer n = args.length >= 2 ? number(args[1]) : null;
        if (n == null || !service.forget(p.getUniqueId(), n)) {
            sender.sendMessage("§cUsage: /rumors forget <#|all>");
            return;
        }
        sender.sendMessage("§7Forgotten.");
    }

    private void ledger(CommandSender sender) {
        Player p = player(sender);
        if (p != null) {
            Texts.send(sender, Texts.ledger(p.getName(), service.store().book(p.getUniqueId())));
        }
    }

    private void hide(CommandSender sender, boolean hidden) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        service.setHidden(p, hidden);
        sender.sendMessage(hidden
                ? "§7Villagers will no longer gossip about where you've been."
                : "§7Villagers may mention where they've seen you again (by region only).");
    }

    private void report(CommandSender sender, String[] args) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        Kind kind = args.length >= 2 ? Kind.parse(args[1]) : null;
        if (kind != Kind.MARKET && kind != Kind.DANGER) {
            sender.sendMessage("§cUsage: /rumors report <market|danger> [number] <what>");
            return;
        }
        int at = 2;
        int count = 0;
        Integer maybe = args.length > 2 ? number(args[2]) : null;
        if (maybe != null) {
            count = maybe;
            at = 3;
        }
        if (args.length <= at) {
            sender.sendMessage("§cSay what happened: /rumors report " + args[1].toLowerCase(Locale.ROOT) + " [number] <what>");
            return;
        }
        String what = String.join(" ", List.of(args).subList(at, args.length));
        RumorService.Reported r = service.report(kind, p.getLocation(), what, kind == Kind.DANGER ? "note" : "", count, "", null);
        sender.sendMessage(r.fact() == null ? "§cNot recorded: rumors are off here, or this world isn't tracked."
                : "§7Recorded as " + r.fact().id + ": " + Tale.sentence(kind, r.fact().exact()));
    }

    private void stats(CommandSender sender) {
        RumorStore s = service.store();
        int trueN = 0;
        int exaggerated = 0;
        int wrong = 0;
        for (RumorStore.Book b : s.books.values()) {
            trueN += b.trueCount;
            exaggerated += b.exaggeratedCount;
            wrong += b.falseCount;
        }
        sender.sendMessage("§6[Rumors] §7facts " + s.facts.size() + ", rumors in circulation " + s.rumorCount() + " across "
                + s.mills.size() + " region(s), notebooks " + s.books.size() + ". Checked so far: " + trueN + " true, "
                + exaggerated + " exaggerated, " + wrong + " false.");
    }

    private void reload(CommandSender sender) {
        List<String> problems = reload.get();
        sender.sendMessage("§7Rumors config reloaded. (Timer lengths change after a restart.)");
        for (String line : problems) {
            sender.sendMessage("§e  " + line);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> all = new ArrayList<>(SUBS);
            if (sender.hasPermission("nexusrumors.admin")) {
                all.addAll(ADMIN_SUBS);
            }
            for (String s : all) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("tell")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("report")) {
            out.add("market");
            out.add("danger");
        }
        return out;
    }
}
