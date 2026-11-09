package team.civitatum.charactercreation.gametest;

import static team.civitatum.charactercreation.gametest.Case.*;

import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import team.civitatum.charactercreation.BirthsignCatalog;
import team.civitatum.charactercreation.CharacterData;
import team.civitatum.charactercreation.Civitatum;
import team.civitatum.charactercreation.RaceCatalog;
import team.civitatum.charactercreation.RaceService;
import team.civitatum.charactercreation.RaceState;
import team.civitatum.charactercreation.SignCommands;
import team.civitatum.charactercreation.power.TemporaryBlocks;

/** How a character gets its sign, the sign commands, and the blocks of the powers across a save. */
@GameTestHolder(Civitatum.ID)
@PrefixGameTestTemplate(false)
public final class RuleTests {
    /** The token of the editor session the server opened for the player; a client gets it in the editor packet. */
    @SuppressWarnings("unchecked")
    static long token(ServerPlayer p) {
        Map<UUID, Object> edits = field(null, RaceService.class, "EDITS");
        Object edit = edits.get(p.getUUID());
        return (long) call(edit, edit.getClass(), "token", new Class<?>[0]);
    }

    static void save(ServerPlayer p, CharacterData c) {
        RaceService.save(p, token(p), Civitatum.GSON.toJson(c));
    }

    @GameTest(template = TEMPLATE, batch = "sign_choice")
    public static void signChoice(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("choice", "human", "none", 16, 16);
        p.setData(Civitatum.STATE, p.getData(Civitatum.STATE).withCharacter(new CharacterData()));
        RaceService.open(p, true, false);
        var draft = RaceCatalog.defaults("dwarf", "male");
        draft.birthsign = BirthsignCatalog.NONE;
        save(p, draft);
        check(!p.getData(Civitatum.STATE).character().selected, "a character without a sign is refused");
        draft.birthsign = BirthsignCatalog.SERPENT;
        save(p, draft);
        check(!p.getData(Civitatum.STATE).character().selected, "the Serpent cannot be chosen");
        draft.birthsign = BirthsignCatalog.LOT;
        save(p, draft);
        var saved = p.getData(Civitatum.STATE).character();
        var sign = BirthsignCatalog.get(saved.birthsign);
        check(saved.selected && sign != null && (sign.isMonth() || sign.id().equals(BirthsignCatalog.SERPENT))
            && p.hasSaid("sign_drawn"),
            "the lot is drawn by the server", saved.birthsign);
        RaceService.open(p, false, false);
        var edit = saved.copy();
        String kept = edit.birthsign;
        edit.birthsign = kept.equals("spear") ? "smith" : "spear";
        save(p, edit);
        check(p.getData(Civitatum.STATE).character().birthsign.equals(kept), "a sign does not change afterwards");
        var old = p.getData(Civitatum.STATE).json().replace("\"birthsign\": \"" + kept + "\"",
            "\"birthsign\": \"warrior\"");
        var loaded = RaceState.read(old);
        check(loaded.character().selected && loaded.character().birthsign.equals(BirthsignCatalog.NONE),
            "an old TES sign becomes none");
        c.end(1);
    }

    @GameTest(template = TEMPLATE, batch = "sign_commands")
    public static void signCommands(GameTestHelper h) {
        var c = new Case(h);
        var p = c.player("commands", "human", "spear", 16, 16);
        CommandSourceStack source = c.level.getServer().createCommandSourceStack().withSuppressedOutput();
        call(null, SignCommands.class, "set", new Class<?>[]{CommandSourceStack.class, ServerPlayer.class,
            String.class}, source, p, "serpent");
        check(p.getData(Civitatum.STATE).character().birthsign.equals(BirthsignCatalog.SERPENT),
            "an administrator gives the Serpent");
        h.setDayTime(6000);
        p.experienceLevel = 3;
        call(null, SignCommands.class, "rewrite", new Class<?>[]{ServerPlayer.class}, p);
        check(p.hasSaid("rewrite.not_night") && p.experienceLevel == 3, "no rewriting the sky by day");
        h.setDayTime(18000);
        call(null, SignCommands.class, "rewrite", new Class<?>[]{ServerPlayer.class}, p);
        var s = p.getData(Civitatum.STATE);
        check(p.experienceLevel == 2 && s.character().birthsign.equals(BirthsignCatalog.NONE) && s.skyRewritten(),
            "at night: a level, and the sign is cleared");
        var sleep = NeoForge.EVENT_BUS.post(new CanPlayerSleepEvent(p, p.blockPosition(), null));
        check(sleep.getProblem() == Player.BedSleepingProblem.OTHER_PROBLEM, "no sleep until dawn");
        var tower = s.character().copy();
        tower.birthsign = "tower";
        p.setData(Civitatum.STATE, s.withCharacter(tower));
        call(null, SignCommands.class, "rewrite", new Class<?>[]{ServerPlayer.class}, p);
        check(p.hasSaid("rewrite.used") && p.getData(Civitatum.STATE).character().birthsign.equals("tower"),
            "once per character");
        RaceService.reset(p);
        check(!p.getData(Civitatum.STATE).skyRewritten(), "a new character may rewrite again");
        c.end(1);
    }

    /** What a restart does to the blocks of the powers: the world saves them and loads them back. */
    @GameTest(template = TEMPLATE, batch = "temporary_blocks", timeoutTicks = 60)
    public static void temporaryBlocks(GameTestHelper h) {
        var c = new Case(h);
        BlockPos at = c.pos(16, 1, 16);
        check(TemporaryBlocks.place(c.level, at, Blocks.BELL.defaultBlockState(), 40), "a bell for 2 s");
        check(!TemporaryBlocks.place(c.level, at, Blocks.CHAIN.defaultBlockState(), 40), "not twice on one place");
        var data = c.level.getDataStorage().get(new net.minecraft.world.level.saveddata.SavedData.Factory<>(
            () -> null, (tag, registries) -> null, null), Civitatum.ID + "_temporary_blocks");
        CompoundTag saved = ((TemporaryBlocks) data).save(new CompoundTag(), c.level.registryAccess());
        var loaded = (TemporaryBlocks) call(null, TemporaryBlocks.class, "load",
            new Class<?>[]{CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class}, saved,
                c.level.registryAccess());
        check(loaded.save(new CompoundTag(), c.level.registryAccess()).equals(saved), "survives a save and a load");
        BlockPos stale = c.pos(18, 1, 16);
        check(TemporaryBlocks.place(c.level, stale, Blocks.CHAIN.defaultBlockState(), 400), "a chain");
        c.level.setBlock(stale, Blocks.AIR.defaultBlockState(), 3);
        check(TemporaryBlocks.place(c.level, stale, Blocks.CHAIN.defaultBlockState(), 400),
            "a block removed by other means frees its place");
        c.at(45, () -> check(c.level.getBlockState(at).isAir() && !TemporaryBlocks.isTemporary(c.level, at),
            "the bell is gone after its time"));
        c.end(46);
    }
}
