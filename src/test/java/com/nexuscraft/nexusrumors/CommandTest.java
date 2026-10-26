package com.nexuscraft.nexusrumors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Villager;

import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexusrumors.Kit.expect;

/** /rumors in all its forms. */
public class CommandTest {

    public static void main(String[] args) {
        notes();
        passing();
        small();
        admin();
        tabs();
        Kit.finish();
    }

    static BukkitKit.Rig rig() {
        BukkitKit.reset();
        BukkitKit.world();
        BukkitKit.Rig rig = new BukkitKit.Rig();
        rig.dice.always = 0.9;
        return rig;
    }

    static void notes() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        String empty = rig.say(ann);
        expect(empty.contains("Ann's Rumors") && empty.contains("haven't heard anything yet") && empty.contains("Sneak up to a villager"), "an empty notebook explains itself: " + empty);
        Fact f = ServiceTest.plant(rig, 0, 0, "lava");
        Fact m = rig.service.report(Kind.MARKET, ServiceTest.at(400, 0), "a Mending book", "librarian", 3, "", null).fact();
        Heard h = ServiceTest.learn(rig, ann, f, 2);
        h.lastAt = 10;
        Heard hm = ServiceTest.learn(rig, ann, m, 1);
        hm.lastAt = 20;
        hm.add(new Heard.Version("p:Bo", 2, 21, m.exact().withNumber(1)), 6);
        String list = rig.say(ann);
        expect(list.contains("#1 [Market]") && list.contains("#2 [Danger]") && list.indexOf("#1") < list.indexOf("#2"), "newest first: " + list);
        expect(list.contains("Someone died to lava in the plains, around spawn") && list.contains("heard once"), "what it says, how often");
        expect(list.contains("heard 2x, 1 agree") && list.contains("told differently"), "disagreement shows: " + list);
        expect(list.contains("Stand where it says for 15s"), "and how to check");

        String one = rig.say(ann, "2");
        expect(one.contains("Rumor #2 (Danger)") && one.contains("Most tellings agree: Someone died to lava") && one.contains("a plain-spoken villager")
                && one.contains("2 steps from the event") && one.contains("around spawn for 15 seconds"), "detail: " + one);
        String detail = rig.say(ann, "#1");
        expect(detail.contains("Rumor #1 (Market)") && detail.contains("1 step from the event") && detail.contains("Bo (2 steps"), "# works and singular step: " + detail);
        List<String> raw = Texts.detail(1, hm, rig.config);
        expect(raw.stream().anyMatch(x -> x.contains("§a- a plain-spoken villager")) && raw.stream().anyMatch(x -> x.contains("§c- Bo")),
                "tellers who agree with most are green, the odd one out red: " + raw);
        expect(rig.say(ann, "9").contains("haven't heard a rumor #9") && rig.say(ann, "0").contains("haven't heard a rumor #0"), "no such rumor");
        expect(rig.say(ann, "dance").contains("Not sure what you mean"), "nonsense");
        expect(rig.sayConsole().contains("Only a player"), "the console has no notebook");
        expect(rig.sayConsole("3").contains("Only a player"), "nor does it read one");

        String forget = rig.say(ann, "forget", "1");
        expect(forget.contains("Forgotten") && rig.service.entries(ann.getUniqueId()).size() == 1, "forget one");
        expect(rig.say(ann, "forget", "7").contains("Usage") && rig.say(ann, "forget").contains("Usage") && rig.say(ann, "forget", "x").contains("Usage"), "forget needs a real number");
        expect(rig.say(ann, "forget", "all").contains("Forgot 1 rumor"), "forget all");
        expect(rig.sayConsole("forget", "all").contains("Only a player"), "console forget");

        String ledger = rig.say(ann, "ledger");
        expect(ledger.contains("Ann's Record") && ledger.contains("haven't checked a rumor yet"), "an empty record");
        RumorStore.Book b = rig.store.book(ann.getUniqueId());
        b.trueCount = 3;
        b.falseCount = 1;
        b.exaggeratedCount = 0;
        ledger = rig.say(ann, "ledger");
        expect(ledger.contains("Checked: 4") && ledger.contains("True 3") && ledger.contains("False 1") && ledger.contains("75% held up") && !ledger.contains("Too late"), "a record: " + ledger);
        b.staleCount = 2;
        expect(rig.say(ann, "ledger").contains("Too late 2"), "stale ones show");
        b.trueCount = 0;
        b.falseCount = 0;
        expect(!rig.say(ann, "ledger").contains("held up"), "no percentage without checks");
        expect(rig.sayConsole("ledger").contains("Only a player"), "console ledger");
    }

    static void passing() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        BukkitKit.TestPlayer bo = BukkitKit.join("Bo");
        Fact f = ServiceTest.plant(rig, 400, 0, "lava");
        ServiceTest.learn(rig, ann, f, 1);
        expect(rig.say(ann, "tell").contains("Usage") && rig.say(ann, "tell", "Bo").contains("Usage") && rig.say(ann, "tell", "Bo", "x").contains("Usage"), "tell usage");
        expect(rig.say(ann, "tell", "Bo", "5").contains("haven't heard a rumor #5"), "tell a rumor you don't have");
        String ok = rig.say(ann, "tell", "Bo", "1");
        expect(ok.contains("You pass it on to Bo") && bo.text().contains("Ann tells you"), "tell: " + ok);
        expect(rig.sayConsole("tell", "Bo", "1").contains("Only a player"), "console tell");

        expect(rig.say(ann, "post").contains("Usage") && rig.say(ann, "post", "x").contains("Usage"), "post usage");
        expect(rig.say(ann, "post", "1").contains("no villager close enough"), "post with no one near");
        BukkitKit.villager(20, 20);
        expect(rig.say(ann, "post", "1").contains("You tell the villagers"), "post");
        expect(rig.sayConsole("post", "1").contains("Only a player"), "console post");
    }

    static void small() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        String help = rig.say(ann, "help");
        expect(help.contains("/rumors tell <player> <#>") && help.contains("Sneak and right-click") && !help.contains("report"), "help for players: " + help);
        ann.perms.add("nexusrumors.admin");
        expect(rig.say(ann, "help").contains("/rumors report") && rig.say(ann, "help").contains("stats"), "and for admins");
        ann.perms.remove("nexusrumors.admin");

        expect(rig.say(ann, "hide").contains("no longer gossip") && rig.service.isHidden(ann.getUniqueId()), "hide");
        expect(rig.say(ann, "show").contains("may mention where they've seen you") && !rig.service.isHidden(ann.getUniqueId()), "show");
        expect(rig.sayConsole("hide").contains("Only a player"), "console hide");

        ann.perms.clear();
        expect(rig.say(ann, "tell", "Bo", "1").contains("may not use rumors") && rig.say(ann, "hide").contains("may not use rumors"), "no permission, no gossip");
        expect(rig.say(ann).contains("Ann's Rumors"), "(a bare /rumors is only a look at your own notes)");
        expect(rig.say(ann, "reload").contains("may not do that") && rig.say(ann, "stats").contains("may not do that") && rig.say(ann, "report", "danger", "x").contains("may not do that"),
                "admin commands need admin");
        expect(rig.store.facts.isEmpty(), "and did nothing");
    }

    static void admin() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer root = BukkitKit.join("Root");
        root.perms.add("nexusrumors.admin");
        root.moveTo(BukkitKit.world(), 400, 0);
        String r = rig.say(root, "report", "market", "4", "a", "Mending", "book");
        Fact f = rig.store.facts.get("f1");
        expect(r.contains("Recorded as f1") && f.kind == Kind.MARKET && f.detail.equals("a Mending book") && f.number == 4 && f.extra.isEmpty()
                && f.region.equals(new Region("world", 2, 0)), "report a market rumor by hand: " + r);
        rig.say(root, "report", "danger", "a", "cave-in");
        Fact d = rig.store.facts.get("f2");
        expect(d.kind == Kind.DANGER && d.extra.equals("note") && d.detail.equals("a cave-in") && d.number == 0, "a danger note");
        expect(rig.say(root, "report", "danger", "a", "cave-in").contains("Recorded as f2"), "again is the same story");
        expect(rig.say(root, "report").contains("Usage") && rig.say(root, "report", "sighting", "x").contains("Usage") && rig.say(root, "report", "nope", "x").contains("Usage"),
                "kinds are market or danger");
        expect(rig.say(root, "report", "market").contains("Say what happened") && rig.say(root, "report", "market", "5").contains("Say what happened"), "a report needs words");
        expect(rig.say(root, "report", "market", "-3", "a", "thing").contains("Recorded") && rig.store.facts.get("f3").number == 0, "negative numbers are no number");
        root.stubSetLocation(new Location(BukkitKit.nether(), 0, 70, 0));
        expect(rig.say(root, "report", "market", "a", "thing").contains("Not recorded"), "not in the Nether");
        root.moveTo(BukkitKit.world(), 0, 0);
        expect(rig.sayConsole("report", "market", "x").contains("Only a player"), "console report");

        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        Heard h = ServiceTest.learn(rig, ann, f, 1);
        rig.store.book(ann.getUniqueId()).trueCount = 2;
        rig.store.book(UUID.randomUUID()).falseCount = 1;
        rig.store.book(ann.getUniqueId()).exaggeratedCount = 1;
        String stats = rig.say(root, "stats");
        expect(stats.contains("facts 3") && stats.contains("rumors in circulation 3") && stats.contains("across 1 region(s)") && stats.contains("notebooks 2")
                && stats.contains("2 true, 1 exaggerated, 1 false") && h != null, "stats: " + stats);
        expect(rig.sayConsole("stats").contains("facts 3"), "the console may ask for stats");

        // reload
        rig.command.setReload(() -> List.of("market.notable-materials: X is not a material"));
        String reload = rig.say(root, "reload");
        expect(reload.contains("reloaded") && reload.contains("restart") && reload.contains("X is not a material"), "reload lists problems: " + reload);
        rig.command.setReload(List::of);
        expect(!rig.say(root, "reload").contains("not a material"), "and none when there are none");
    }

    static void tabs() {
        BukkitKit.Rig rig = rig();
        BukkitKit.TestPlayer ann = BukkitKit.join("Ann");
        BukkitKit.join("Anders");
        BukkitKit.join("Bo");
        List<String> subs = rig.command.onTabComplete(ann, BukkitKit.command("rumors"), "rumors", new String[]{""});
        expect(subs.containsAll(List.of("tell", "post", "forget", "ledger", "hide", "show", "help")) && !subs.contains("report"), "players see their commands: " + subs);
        ann.perms.add("nexusrumors.admin");
        subs = rig.command.onTabComplete(ann, BukkitKit.command("rumors"), "rumors", new String[]{""});
        expect(subs.contains("report") && subs.contains("stats") && subs.contains("reload"), "admins also see theirs");
        expect(rig.command.onTabComplete(ann, BukkitKit.command("rumors"), "rumors", new String[]{"fo"}).equals(List.of("forget")), "prefix");
        expect(rig.command.onTabComplete(ann, BukkitKit.command("rumors"), "rumors", new String[]{"tell", "an"}).containsAll(List.of("Ann", "Anders"))
                && rig.command.onTabComplete(ann, BukkitKit.command("rumors"), "rumors", new String[]{"tell", "an"}).size() == 2, "player names after tell");
        expect(rig.command.onTabComplete(ann, BukkitKit.command("rumors"), "rumors", new String[]{"report", ""}).equals(List.of("market", "danger")), "report kinds");
        expect(rig.command.onTabComplete(ann, BukkitKit.command("rumors"), "rumors", new String[]{"forget", ""}).isEmpty()
                && rig.command.onTabComplete(ann, BukkitKit.command("rumors"), "rumors", new String[]{"tell", "Ann", ""}).isEmpty(), "nothing else");
        Villager unused = BukkitKit.villager(0, 0);
        expect(unused != null && Bukkit.ENTITIES.size() == 1, "(setup sanity)");
    }
}
