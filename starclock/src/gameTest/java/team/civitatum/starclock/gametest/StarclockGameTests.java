package team.civitatum.starclock.gametest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import team.civitatum.starclock.CivitatumStarclock;
import team.civitatum.starclock.data.PendingPrediction;
import team.civitatum.starclock.data.StarClockData;
import team.civitatum.starclock.prediction.*;
import team.civitatum.starclock.trader.SignTrades;

@GameTestHolder(CivitatumStarclock.ID)
@PrefixGameTestTemplate(false)
public final class StarclockGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void savedDataRoundTrip(GameTestHelper helper) throws Exception {
        var data = new StarClockData();
        var player = UUID.randomUUID();
        long now = helper.getLevel().getServer().overworld().getGameTime();
        var plan = PredictionBonuses.append(PredictionRegistry.catalog().get("starclock:spear_upright", 1));
        var pending = new PendingPrediction(UUID.randomUUID(), player, "minecraft:overworld",
                1, 70, 3, now + 6000, 3, plan, true, 1, 0, 0);
        data.schedule(now, pending);
        data.grantTowerKey(player);
        data.consumeTowerKey(player);
        data.consumeTowerKey(player);
        var buffer = new ByteArrayOutputStream();
        NbtIo.writeCompressed(data.save(new CompoundTag(), helper.getLevel().registryAccess()), buffer);
        var nbt = NbtIo.readCompressed(new ByteArrayInputStream(buffer.toByteArray()), NbtAccounter.unlimitedHeap());
        var restored = StarClockData.load(nbt, helper.getLevel().registryAccess());
        helper.assertTrue(restored.pending().equals(java.util.List.of(pending)), "Очередь изменилась при сохранении");
        helper.assertTrue(restored.towerKeyCharges(player) == 3, "Потерян запас Ключа Башни");
        helper.assertTrue(restored.remainingCooldown(now) > 0, "Потерян кулдаун");
        var old = new CompoundTag();
        old.putLong("LastRitual", now);
        var migrated = StarClockData.load(old, helper.getLevel().registryAccess());
        helper.assertTrue(migrated.pending().isEmpty() && migrated.remainingCooldown(now) > 0,
                "Миграция M1 потеряла кулдаун");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void registriesAndOffers(GameTestHelper helper) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        for (var sign : Constellation.values()) {
            var offers = SignTrades.create(player, sign, false);
            helper.assertTrue(offers.size() == 3, "Неверное число сделок: " + sign);
            helper.assertTrue(offers.stream().allMatch(offer -> offer.getMaxUses() == 1),
                    "Торговля допускает бесконечный обмен");
            var key = ResourceKey.create(Registries.LOOT_TABLE,
                    ResourceLocation.parse("starclock:sign/" + sign.id()));
            helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY,
                    "Не загружена лут-таблица: " + sign.id());
        }
        for (var plan : PredictionRegistry.catalog().allPlans()) {
            for (var action : PredictionBonuses.append(plan).actions()) {
                var params = action.params();
                if ("spawn".equals(action.type())) {
                    helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(
                            ResourceLocation.parse(params.get("entity").getAsString())), "Нет сущности");
                    if (params.has("equipment")) {
                        for (var value : params.getAsJsonObject("equipment").entrySet()) {
                            ItemFactory.create(value.getValue().getAsJsonObject(), player);
                        }
                    }
                }
                if ("effect".equals(action.type()) && params.has("effects")) {
                    for (var value : params.getAsJsonArray("effects")) {
                        helper.assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(ResourceLocation.parse(
                                value.getAsJsonObject().get("id").getAsString())), "Нет эффекта");
                    }
                }
                if ("loot".equals(action.type()) && params.has("items")) {
                    for (var value : params.getAsJsonArray("items")) ItemFactory.create(value.getAsJsonObject(), player);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void offlinePredictionWaits(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var data = StarClockData.get(server);
        long now = server.overworld().getGameTime();
        UUID id = UUID.randomUUID();
        UUID offline = UUID.randomUUID();
        var plan = PredictionRegistry.catalog().get("starclock:spear_upright", 1);
        data.schedule(now, new PendingPrediction(id, offline, "minecraft:overworld", 0, 70, 0,
                now, 1, plan, false, 0, 0, 0));
        helper.runAfterDelay(25, () -> {
            var pending = data.pending().stream().filter(value -> value.id().equals(id)).findFirst();
            helper.assertTrue(pending.isPresent() && pending.get().waited(),
                    "Событие офлайн-игрока потеряно или не помечено ожидающим");
            data.removePrediction(id);
            helper.succeed();
        });
    }

    private StarclockGameTests() {}
}
