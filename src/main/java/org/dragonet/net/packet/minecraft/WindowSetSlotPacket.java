/*
 * GNU LESSER GENERAL PUBLIC LICENSE
 *                       Version 3, 29 June 2007
 *
 * Copyright (C) 2007 Free Software Foundation, Inc. <http://fsf.org/>
 * Everyone is permitted to copy and distribute verbatim copies
 * of this license document, but changing it is not allowed.
 *
 * You can view LICENCE file for details. 
 *
 * @author The Dragonet Team
 */
package org.dragonet.net.packet.minecraft;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.dragonet.inventory.PEInventorySlot;
import org.dragonet.utilities.io.PEBinaryReader;
import org.dragonet.utilities.io.PEBinaryWriter;

public class WindowSetSlotPacket extends PEPacket {

    public byte windowID;
    public short slot;
    public PEInventorySlot item;
    
    public WindowSetSlotPacket(byte[] data) {
        this.setData(data);
    }
    
    @Override
    public int pid() {
        return PEPacketIDs.WINDOW_SET_SLOT_PACKET;
    }

    @Override
    public void encode() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PEBinaryWriter writer = new PEBinaryWriter(bos);
            writer.writeByte((byte) (this.pid() & 0xFF));
            writer.writeByte(this.windowID);
            writer.writeShort(this.slot);
            PEInventorySlot.writeSlot(writer, this.item);
            this.setData(bos.toByteArray());
        } catch (IOException e) {
        }
    }

    @Override
    public void decode() {
        try {
            PEBinaryReader reader = new PEBinaryReader(new ByteArrayInputStream(this.getData()));
            reader.readByte();
            this.windowID = reader.readByte();
            this.slot = reader.readShort();
            this.item = PEInventorySlot.readSlot(reader);
        } catch (IOException e) {
        }
    }

}
