package net.glowstone.net.message.play.inv;

import com.flowpowered.networking.Message;
import lombok.Data;
import org.bukkit.inventory.ItemStack;

@Data
public final class CreativeItemMessage implements Message {

    public final int slot;
    public final ItemStack item;

}
