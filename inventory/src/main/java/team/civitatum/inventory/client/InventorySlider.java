package team.civitatum.inventory.client;

import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public final class InventorySlider extends AbstractSliderButton {
    private final String label;
    private final double min,max;
    private final DoubleConsumer change;
    public InventorySlider(String label,double current,double min,double max,DoubleConsumer change){
        super(0,0,158,18,Component.empty(),(current-min)/(max-min));
        this.label=label;this.min=min;this.max=max;this.change=change;updateMessage();
    }
    @Override protected void updateMessage(){setMessage(CivitatumInventoryScreen.tr(label,Math.round((min+(max-min)*value)*100)));}
    @Override protected void applyValue(){double steps=min==1?20:100;double actual=Math.round((min+(max-min)*value)*steps)/steps;value=(actual-min)/(max-min);change.accept(actual);}
    @Override public void onRelease(double x,double y){super.onRelease(x,y);InventoryPreferences.get().save();}
}
