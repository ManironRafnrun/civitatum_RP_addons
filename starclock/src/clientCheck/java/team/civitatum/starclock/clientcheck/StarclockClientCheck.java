package team.civitatum.starclock.clientcheck;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Screenshot;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import software.bernie.geckolib.event.GeoRenderEvent;
import team.civitatum.starclock.CivitatumStarclock;
import team.civitatum.starclock.client.RitualClientState;
import team.civitatum.starclock.client.render.StarToolRenderer;
import team.civitatum.starclock.item.StarToolItem;
import team.civitatum.starclock.network.RitualStatePayload;
import team.civitatum.starclock.registry.ModItems;

// Только набор clientCheck: этот класс отсутствует в выпускаемом jar.
@EventBusSubscriber(modid = CivitatumStarclock.ID, value = Dist.CLIENT)
public final class StarclockClientCheck {
    private static final List<String> completed = new ArrayList<>();
    private static final List<String> failures = new ArrayList<>();
    private static int current = -1;
    private static int waitTicks;
    private static int frames;
    private static float lowest;
    private static float highest;
    private static long phaseStart;
    private static boolean drawing;
    private static boolean captured;
    private static boolean finished;
    private static int cancellationStage;
    private static long cancellationStarted;
    private static boolean cancellationPassed;
    private static UUID session;
    private static StarToolItem tool;
    private static InteractionHand hand;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("starclock.clientCheck") || finished) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        try {
            if (current < 0) {
                mc.setScreen(null);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = false;
                beginNext(mc);
                return;
            }
            if (waitTicks > 0) {
                waitTicks--;
                if (waitTicks == 0) startAnimation(mc);
                return;
            }
            if (current == 24) {
                checkCancellation(mc);
                return;
            }
            if (!drawing && mc.level.getGameTime() - phaseStart >= 40) {
                require(frames > 10, "Нет проходов GeoItemRenderer: " + label());
                require(highest - lowest > 0.5F, "Ключевые кадры не изменили модель: " + label());
                drawing = true;
                phaseStart = mc.level.getGameTime();
                RitualClientState.accept(new RitualStatePayload(session, hand, tool.kind(), tool.tier(), true, phaseStart));
            } else if (drawing && mc.level.getGameTime() - phaseStart >= 6) {
                require(RitualClientState.animationTicks(0) > 39, "Не удержана конечная поза");
                RitualClientState.clear();
                checkPunchy(true);
                completed.add(label());
                CivitatumStarclock.LOG.info("CLIENT_CHECK_CASE_OK {}", label());
                beginNext(mc);
            }
            if (!captured && !drawing && mc.level.getGameTime() - phaseStart >= 18) {
                captured = true;
                Screenshot.grab(mc.gameDirectory, "starclock-" + current + ".png",
                        mc.getMainRenderTarget(), message -> CivitatumStarclock.LOG.info("{}", message.getString()));
            }
        } catch (Throwable error) {
            failures.add(error.toString());
            CivitatumStarclock.LOG.error("CLIENT_CHECK_FAILURE", error);
            finish(mc);
        }
    }

    private static String label() {
        return tool.kind().id() + "_" + tool.tier().strength() + "/" + hand + "/"
                + Minecraft.getInstance().options.mainHand().get();
    }

    private static void beginNext(Minecraft mc) {
        current++;
        if (current > ModItems.TOOLS.size() * 2) {
            finish(mc);
            return;
        }
        tool = ModItems.TOOLS.get(current == 24 ? 0 : current / 2).get();
        hand = current % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        mc.options.mainHand().set(current / 4 % 2 == 0 ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
        mc.player.setMainArm(mc.options.mainHand().get());
        mc.player.setXRot(-60);
        mc.player.setYRot(0);
        frames = 0;
        lowest = Float.POSITIVE_INFINITY;
        highest = Float.NEGATIVE_INFINITY;
        drawing = false;
        captured = false;
        waitTicks = 24;
        var id = mc.player.getUUID();
        var next = tool;
        var selectedHand = hand;
        mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer();
            if (server == null) return;
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(0.5, 100, 0.5);
            player.setNoGravity(true);
            player.getInventory().selected = 0;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setItemInHand(selectedHand, new ItemStack(next));
            if (current == 24) team.civitatum.starclock.data.StarClockData.get(server).resetCooldown();
            server.overworld().setDayTime(15000);
            server.overworld().setWeatherParameters(12000, 0, false, false);
        });
    }

    private static void startAnimation(Minecraft mc) throws Exception {
        require(mc.player.getItemInHand(hand).getItem() == tool, "Предмет не синхронизирован");
        checkPunchy(true);
        if (current == 24) {
            cancellationStarted = mc.level.getGameTime();
            cancellationStage = 1;
            mc.options.keyUse.setDown(true);
            mc.gameMode.useItem(mc.player, hand);
            return;
        }
        session = UUID.randomUUID();
        phaseStart = mc.level.getGameTime();
        RitualClientState.accept(new RitualStatePayload(session, hand, tool.kind(), tool.tier(), false, phaseStart));
        checkPunchy(false);
        Method attack = Minecraft.class.getDeclaredMethod("startAttack");
        attack.setAccessible(true);
        require(Boolean.FALSE.equals(attack.invoke(mc)), "Миксин не заблокировал атаку");
        Method use = Minecraft.class.getDeclaredMethod("startUseItem");
        use.setAccessible(true);
        use.invoke(mc);
        require(!mc.player.isUsingItem(), "Миксин не заблокировал повторное использование");
        int selected = mc.player.getInventory().selected;
        Method scroll = mc.mouseHandler.getClass().getDeclaredMethod("onScroll", long.class, double.class, double.class);
        scroll.setAccessible(true);
        scroll.invoke(mc.mouseHandler, mc.getWindow().getWindow(), 0.0, 1.0);
        require(selected == mc.player.getInventory().selected, "Колесо переключило слот");
    }

    private static void checkCancellation(Minecraft mc) throws Exception {
        long elapsed = mc.level.getGameTime() - cancellationStarted;
        require(elapsed < 60, "Сервер не подтвердил запуск/отмену настоящего обряда");
        if (cancellationStage == 1 && RitualClientState.isActive()) {
            cancellationStage = 2;
            checkPunchy(false);
        }
        if (cancellationStage == 2 && elapsed >= 10) {
            Method press = mc.mouseHandler.getClass().getDeclaredMethod("onPress",
                    long.class, int.class, int.class, int.class);
            press.setAccessible(true);
            press.invoke(mc.mouseHandler, mc.getWindow().getWindow(),
                    org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT, org.lwjgl.glfw.GLFW.GLFW_RELEASE, 0);
            require(!mc.options.keyUse.isDown(), "Миксин поглотил отпускание кнопки");
            cancellationStage = 3;
        } else if (cancellationStage == 3 && !RitualClientState.isActive()) {
            require(!mc.player.isUsingItem(), "После отмены предмет используется");
            checkPunchy(true);
            cancellationPassed = true;
            CivitatumStarclock.LOG.info("CLIENT_CHECK_CANCEL_OK: сервер подтвердил отпускание кнопки");
            finish(mc);
        }
    }

    @SubscribeEvent
    public static void rendered(GeoRenderEvent.Item.Post event) {
        if (!Boolean.getBoolean("starclock.clientCheck") || waitTicks > 0 || drawing || finished) return;
        if (!(event.getRenderer() instanceof StarToolRenderer renderer) || !RitualClientState.isActive()) return;
        frames++;
        float height = renderer.getGeoModel().getBone("hand").orElseThrow().getPosY();
        lowest = Math.min(lowest, height);
        highest = Math.max(highest, height);
    }

    private static void checkPunchy(boolean enabled) throws Exception {
        if (!ModList.get().isLoaded("punchy")) return;
        var method = Class.forName("punchy.config.PunchyConfig").getMethod("isModEnabled");
        require(Boolean.valueOf(enabled).equals(method.invoke(null)), "Punchy не переключился: " + enabled);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void finish(Minecraft mc) {
        finished = true;
        RitualClientState.clear();
        mc.options.keyUse.setDown(false);
        try {
            Path report = mc.gameDirectory.toPath().resolve("starclock-client-check.json");
            var result = new java.util.LinkedHashMap<String, Object>();
            result.put("punchy", ModList.get().isLoaded("punchy"));
            result.put("completed", completed);
            result.put("failures", failures);
            result.put("cancellation", cancellationPassed);
            result.put("success", failures.isEmpty() && completed.size() == 24 && cancellationPassed);
            Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
            CivitatumStarclock.LOG.info("CLIENT_CHECK_FINISHED {} проверок, {} ошибок", completed.size(), failures.size());
        } catch (Exception error) {
            CivitatumStarclock.LOG.error("Не удалось записать результат clientCheck", error);
        }
        mc.stop();
    }

    private StarclockClientCheck() {}
}
