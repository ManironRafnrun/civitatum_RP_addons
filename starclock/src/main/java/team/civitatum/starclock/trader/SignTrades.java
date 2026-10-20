package team.civitatum.starclock.trader;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import team.civitatum.starclock.prediction.Constellation;
import team.civitatum.starclock.prediction.ItemFactory;

public final class SignTrades {
    public static MerchantOffers create(ServerPlayer player, Constellation sign, boolean discount) {
        MerchantOffers offers = new MerchantOffers();
        switch (sign) {
            case SPEAR -> {
                add(offers, player, "iron_sword", 1, 3, discount, false);
                add(offers, player, "arrow", 16, 2, discount, false);
                add(offers, player, "shield", 1, 4, discount, false);
            }
            case SMITH -> {
                add(offers, player, "iron_ingot", 8, 5, discount, false);
                add(offers, player, "anvil", 1, 6, discount, false);
                add(offers, player, "iron_pickaxe", 1, 3, discount, false);
            }
            case MESSENGER -> {
                add(offers, player, "map", 1, 3, discount, false);
                add(offers, player, "saddle", 1, 4, discount, false);
                add(offers, player, "lead", 1, 1, discount, false);
            }
            case SHEPHERD -> {
                add(offers, player, "white_wool", 8, 2, discount, false);
                add(offers, player, "milk_bucket", 1, 2, discount, false);
                add(offers, player, "bell", 1, 5, discount, false);
            }
            case ANCESTOR -> {
                add(offers, player, "bone", 8, 2, discount, false);
                add(offers, player, "gold_ingot", 2, 3, discount, false);
                add(offers, player, "book", 1, 1, discount, false);
            }
            case MOTHER -> {
                add(offers, player, "bread", 8, 2, discount, false);
                add(offers, player, "wheat_seeds", 8, 1, discount, false);
                add(offers, player, "milk_bucket", 1, 2, discount, false);
            }
            case SPINNER -> {
                add(offers, player, "string", 8, 1, discount, false);
                add(offers, player, "lead", 2, 2, discount, false);
                add(offers, player, "purple_dye", 4, 1, discount, false);
            }
            case WANDERER -> {
                add(offers, player, "compass", 1, 2, discount, false);
                add(offers, player, "map", 1, 3, discount, false);
                add(offers, player, "leather_boots", 1, 2, discount, false);
            }
            case HEARTH -> {
                add(offers, player, "coal", 8, 2, discount, false);
                add(offers, player, "torch", 8, 1, discount, false);
                add(offers, player, "campfire", 1, 3, discount, false);
            }
            case TOWER -> {
                add(offers, player, "cobblestone", 8, 1, discount, false);
                add(offers, player, "shield", 1, 4, discount, false);
                // Ключ теперь даётся пророчеством как способность, а не продаётся предметом.
                add(offers, player, "iron_helmet", 1, 6, discount, false);
            }
            case MERCHANT -> {
                add(offers, player, "emerald", 8, 5, discount, false);
                add(offers, player, "gold_ingot", 4, 4, discount, false);
                add(offers, player, "amethyst_shard", 2, 3, discount, false);
            }
            case BEAST -> {
                add(offers, player, "beef", 8, 2, discount, false);
                add(offers, player, "leather", 4, 2, discount, false);
                add(offers, player, "bone", 8, 1, discount, false);
            }
            case SERPENT -> {
                add(offers, player, "spider_eye", 2, 3, discount, false);
                add(offers, player, "tipped_arrow", 3, 5, discount, true);
                add(offers, player, "potion", 1, 6, discount, true);
            }
        }
        return offers;
    }

    private static void add(MerchantOffers offers, ServerPlayer player, String id, int count,
                            int cost, boolean discount, boolean poison) {
        int price = discount ? Math.max(1, (int) Math.ceil(cost * 0.8)) : cost;
        JsonObject item = new JsonObject();
        item.addProperty("id", "minecraft:" + id);
        item.addProperty("poison", poison);
        var stack = ItemFactory.create(item, player).copyWithCount(count);
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), stack, 1, 1, 0.0F));
    }

    private SignTrades() {}
}
