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
        long sealed = data.readingSeed();
        data.grantTowerKey(player);
        data.consumeTowerKey(player);
        data.consumeTowerKey(player);
        var buffer = new ByteArrayOutputStream();
        NbtIo.writeCompressed(data.save(new CompoundTag(), helper.getLevel().registryAccess()), buffer);
        var nbt = NbtIo.readCompressed(new ByteArrayInputStream(buffer.toByteArray()), NbtAccounter.unlimitedHeap());
        var restored = StarClockData.load(nbt, helper.getLevel().registryAccess());
        helper.assertTrue(restored.pending().equals(java.util.List.of(pending)), "Очередь изменилась при сохранении");
        helper.assertTrue(restored.readingSeed() == sealed, "Отмена/перезапуск меняет неисполненный выбор");
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
    public static void drawingPacketsRoundTrip(GameTestHelper helper) {
        var session = UUID.randomUUID();
        var hand = net.minecraft.world.InteractionHand.OFF_HAND;
        var kind = team.civitatum.starclock.item.ToolKind.DECK;
        var tier = team.civitatum.starclock.item.ToolTier.III;
        var preparation = new team.civitatum.starclock.network.RitualStatePayload(
                session, hand, kind, tier, false, 123, null);
        var packets = new java.util.ArrayList<team.civitatum.starclock.network.RitualStatePayload>();
        packets.add(preparation);
        for (var sign : Constellation.values()) packets.add(
                new team.civitatum.starclock.network.RitualStatePayload(session, hand, kind, tier, true, 163, sign));
        for (var packet : packets) {
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                team.civitatum.starclock.network.RitualStatePayload.CODEC.encode(buffer, packet);
                var decoded = team.civitatum.starclock.network.RitualStatePayload.CODEC.decode(buffer);
                helper.assertTrue(decoded.equals(packet), "Пакет фазы изменился в сети");
                helper.assertTrue(buffer.readableBytes() == 0, "В пакете остались лишние данные");
            } finally {
                buffer.release();
            }
        }
        helper.assertTrue(preparation.figure() == null, "Подготовка раскрыла фигуру");
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


    @GameTest(template = "empty", timeoutTicks = 100)
    public static void towerKeyOpensExactlyFiveTimes(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "StarclockKeyTest"));
        player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 3));
        level.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var lower = net.minecraft.world.level.block.Blocks.IRON_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
        level.setBlock(pos, lower, 2);
        level.setBlock(pos.above(), lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() - 2.5, 180, 0);
        var data = StarClockData.get(level.getServer());
        data.grantTowerKey(player.getUUID());
        team.civitatum.starclock.ability.TowerKeyService.cast(player);
        helper.assertTrue(data.towerKeyCharges(player.getUUID()) == 5, "Промах израсходовал заряд");
        player.setYRot(0);
        helper.runAfterDelay(6, () -> {
            var door = (net.minecraft.world.level.block.DoorBlock) level.getBlockState(pos).getBlock();
            door.setOpen(player, level, level.getBlockState(pos), pos, true);
            team.civitatum.starclock.ability.TowerKeyService.cast(player);
            helper.assertTrue(data.towerKeyCharges(player.getUUID()) == 5, "Открытая дверь израсходовала заряд");
            door.setOpen(player, level, level.getBlockState(pos), pos, false);
        });
        for (int i = 0; i < 5; i++) {
            int completed = i + 1;
            helper.runAfterDelay(12 + i * 6, () -> {
                team.civitatum.starclock.ability.TowerKeyService.cast(player);
                helper.assertTrue(level.getBlockState(pos).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),
                        "Способность не открыла дверь");
                helper.assertTrue(data.towerKeyCharges(player.getUUID()) == 5 - completed,
                        "Заряд израсходован неверно");
                ((net.minecraft.world.level.block.DoorBlock) level.getBlockState(pos).getBlock())
                        .setOpen(player, level, level.getBlockState(pos), pos, false);
            });
        }
        helper.runAfterDelay(42, () -> {
            team.civitatum.starclock.ability.TowerKeyService.cast(player);
            helper.assertTrue(!level.getBlockState(pos).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),
                    "Исчерпанная способность открыла дверь");
            helper.assertTrue(data.towerKeyCharges(player.getUUID()) == 0, "Запас восстановился сам");
            helper.succeed();
        });
    }


    @GameTest(template = "empty", timeoutTicks = 100)
    public static void safePositionsRejectFire(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
        level.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        var feet = new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        helper.assertTrue(WorldSafety.free(level, player, feet, true), "Свободная площадка отклонена");
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 2);
        helper.assertTrue(!WorldSafety.free(level, player, feet, true), "Телепорт допускает попадание в огонь");
        helper.succeed();
    }

    private StarclockGameTests() {}
}
