package team.civitatum.inventory.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.*;
import java.util.function.BiConsumer;

/** NeoForge attribute formatting and Apothic's optional modifier-source registry. */
public final class AttributeView {
    private static final TagKey<Attribute> DYNAMIC=TagKey.create(Registries.ATTRIBUTE,ResourceLocation.fromNamespaceAndPath("apothic_attributes","dynamic_base"));
    private static List<Component> hover=List.of();
    public static List<AttributeInstance> entries(Player player,boolean modified) {
        Set<?> hidden=Set.of();
        if(CompatBridge.loaded("apothic_attributes"))try{hidden=(Set<?>)Class.forName("dev.shadowsoffire.apothic_attributes.ALConfig").getField("hiddenAttributes").get(null);}catch(ReflectiveOperationException ignored){}
        Set<?> excluded=hidden;
        return BuiltInRegistries.ATTRIBUTE.holders().map(player::getAttribute).filter(Objects::nonNull)
            .filter(a->!excluded.contains(BuiltInRegistries.ATTRIBUTE.getKey(a.getAttribute().value())))
            .filter(a->!modified||!a.getModifiers().isEmpty()||Double.compare(a.getBaseValue(),a.getValue())!=0)
            .sorted(Comparator.comparing(a->Component.translatable(a.getAttribute().value().getDescriptionId()).getString())).toList();
    }
    public static int render(GuiGraphics g,Font font,Player player,InventoryPanel panel,int scroll,boolean modified,int mx,int my) {
        var entries=entries(player,modified);int x=panel.x+9,y=panel.y+51,w=panel.width-18,bottom=panel.y+panel.height-32;
        int max=Math.max(0,entries.size()*25-(bottom-y));scroll=Math.max(0,Math.min(scroll,max));hover=List.of();
        g.enableScissor(x,y,x+w,bottom);
        for(int i=0;i<entries.size();i++){
            var a=entries.get(i);int row=y+i*25-scroll;if(row+24<y||row>=bottom)continue;
            boolean hot=mx>=x&&mx<x+w&&my>=Math.max(y,row)&&my<Math.min(bottom,row+24);
            g.fill(x-2,row-2,x+w,row+22,hot?0x664F4431:i%2==0?0x242C261C:0x1217130E);
            g.drawString(font,font.plainSubstrByWidth(Component.translatable(a.getAttribute().value().getDescriptionId()).getString(),w-5),x+3,row,0xE9E3D4,false);
            Component value=a.getAttribute().is(DYNAMIC)?CivitatumInventoryScreen.tr("attributes.dynamic"):a.getAttribute().value().toValueComponent(null,a.getValue(),TooltipFlag.Default.NORMAL);
            g.drawString(font,value,x+w-font.width(value)-4,row+11,0xD3B478,false);
            g.fill(x,row+23,x+w,row+24,0x354F4330);
            if(hot)hover=details(player,a);
        }
        g.disableScissor();return scroll;
    }
    public static void tooltip(GuiGraphics g,Font font,InventoryPanel panel){SideTooltip.render(g,font,panel,hover);}
    private static List<Component> details(Player player,AttributeInstance a){
        var attr=a.getAttribute().value();var lines=new ArrayList<Component>();
        lines.add(Component.translatable(attr.getDescriptionId()).withColor(0xD3B478));
        if(I18n.exists(attr.getDescriptionId()+".desc"))lines.add(Component.translatable(attr.getDescriptionId()+".desc"));
        if(a.getAttribute().is(DYNAMIC))lines.add(CivitatumInventoryScreen.tr("attributes.dynamic_hint"));
        else{
            lines.add(CivitatumInventoryScreen.tr("attributes.current").copy().append(attr.toValueComponent(null,a.getValue(),TooltipFlag.Default.NORMAL)));
            lines.add(CivitatumInventoryScreen.tr("attributes.base").copy().append(attr.toValueComponent(null,a.getBaseValue(),TooltipFlag.Default.NORMAL)));
        }
        Map<ResourceLocation,Object> sources=new HashMap<>();
        try{
            Class<?> type=Class.forName("dev.shadowsoffire.apothic_attributes.client.ModifierSourceType");
            for(Object source:(Collection<?>)type.getMethod("getTypes").invoke(null))type.getMethod("extract",LivingEntity.class,BiConsumer.class).invoke(source,player,(BiConsumer<AttributeModifier,Object>)(m,data)->sources.put(m.id(),data));
        }catch(ReflectiveOperationException|LinkageError ignored){}
        for(var modifier:a.getModifiers()){
            var line=attr.toComponent(modifier,TooltipFlag.Default.NORMAL);Object source=sources.get(modifier.id());
            if(source!=null)try{
                Object data=Class.forName("dev.shadowsoffire.apothic_attributes.client.ModifierSource").getMethod("getData").invoke(source);
                if(data instanceof ItemStack item)line.append(" · ").append(item.getHoverName());
                else if(data instanceof MobEffectInstance effect)line.append(" · ").append(effect.getEffect().value().getDisplayName());
            }catch(ReflectiveOperationException ignored){}
            lines.add(line);
        }
        return lines;
    }
}
