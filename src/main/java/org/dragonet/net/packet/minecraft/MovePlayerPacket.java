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
import org.dragonet.utilities.io.PEBinaryReader;
import org.dragonet.utilities.io.PEBinaryWriter;

public class MovePlayerPacket extends PEPacket {
    public int eid;
    public float x;
    public float y;
    public float z;
    public float yaw;
    public float pitch;
    public float bodyYaw;
    public boolean teleport;

    public MovePlayerPacket(byte[] data) {
        this.setData(data);
    }

    public MovePlayerPacket() {
    }
    
    public MovePlayerPacket(int eid, float x, float y, float z, float yaw, float pitch, float bodyYaw, boolean teleport) {
        this.eid = eid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.bodyYaw = bodyYaw;
        this.teleport = teleport;
    }
    
    @Override
    public int pid() {
        return PEPacketIDs.MOVE_PLAYER_PACKET;
    }

    @Override
    public void encode() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PEBinaryWriter writer = new PEBinaryWriter(bos);
            writer.writeByte((byte) (this.pid() & 0xFF));
            writer.writeInt(this.eid);
            writer.writeFloat(this.x);
            writer.writeFloat(this.y);
            writer.writeFloat(this.z);
            writer.writeFloat(this.yaw);
            writer.writeFloat(this.pitch);
            writer.writeFloat(this.bodyYaw);
            //writer.writeByte((byte)0x80);
            writer.writeByte((byte)(this.teleport ? 0x80 : 0x00));
            this.setData(bos.toByteArray());
        } catch (IOException e) {
        }
    }

    @Override
    public void decode() {
        try {
            PEBinaryReader reader = new PEBinaryReader(new ByteArrayInputStream(this.getData()));
            reader.readByte(); //PID
            this.eid = reader.readInt();
            this.x = reader.readFloat();
            this.y = reader.readFloat();
            this.z = reader.readFloat();
            this.yaw = reader.readFloat();
            this.pitch = reader.readFloat();
            this.bodyYaw = reader.readFloat();
        } catch (IOException e) {
        }
    }

}
