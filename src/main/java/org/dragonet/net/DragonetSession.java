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
package org.dragonet.net;

import com.flowpowered.networking.Message;
import com.flowpowered.networking.exception.ChannelClosedException;
import io.netty.channel.ChannelFuture;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.Getter;
import lombok.Setter;
import net.glowstone.EventFactory;
import net.glowstone.GlowServer;
import net.glowstone.entity.GlowPlayer;
import net.glowstone.entity.meta.profile.PlayerProfile;
import net.glowstone.io.PlayerDataService;
import net.glowstone.net.GlowSession;
import net.glowstone.net.message.play.game.UserListItemMessage;
import org.apache.commons.lang.ArrayUtils;
import org.bukkit.event.player.PlayerLoginEvent;
import org.dragonet.DragonetServer;
import org.dragonet.entity.DragonetPlayer;
import org.dragonet.net.packet.EncapsulatedPacket;
import org.dragonet.net.packet.RaknetDataPacket;
import org.dragonet.net.packet.minecraft.ClientConnectPacket;
import org.dragonet.net.packet.minecraft.PEPacket;
import org.dragonet.net.packet.minecraft.PEPacketIDs;
import org.dragonet.net.packet.minecraft.PingPongPacket;
import org.dragonet.net.packet.minecraft.ServerHandshakePacket;
import org.dragonet.net.translator.Translator;
import org.dragonet.utilities.io.PEBinaryReader;
import org.dragonet.utilities.io.PEBinaryWriter;

public class DragonetSession extends GlowSession {

    private @Getter
    DragonetServer dServer;

    private SocketAddress remoteAddress;
    private String remoteIP;
    private int remotePort;

    private @Getter
    long clientID;
    private @Getter
    short clientMTU;

    private @Getter
    long clientSessionID ;

    private @Getter
    int sequenceNum;        //Server->Client
    private @Getter
    int lastSequenceNum;    //Server<-Client

    private @Getter
    @Setter
    int messageIndex;          //Server->Client
    private @Getter
    @Setter
    int splitID;

    private RaknetDataPacket queue;

    private ArrayList<Integer> queueACK = new ArrayList<>();
    private ArrayList<Integer> queueNACK = new ArrayList<>();
    private HashMap<Integer, RaknetDataPacket> cachedOutgoingPacket = new HashMap<>();

    private Translator translator;

    public DragonetSession(DragonetServer dServer, SocketAddress remoteAddress, long clientID, short clientMTU) {
        super(dServer.getServer());
        this.dServer = dServer;
        this.clientID = clientID;
        this.clientMTU = clientMTU;
        this.remoteAddress = remoteAddress;
        this.remoteIP = this.remoteAddress.toString().substring(1, this.remoteAddress.toString().indexOf(":"));
        this.remotePort = Integer.parseInt(this.remoteAddress.toString().substring(this.remoteAddress.toString().indexOf(":")+1));
        this.queue = new RaknetDataPacket(this.sequenceNum);
    }

    /**
     * Trigger a tick update for the session
     */
    public void onTick() {
        sendAllACK();
        sendAllNACK();
        if (this.queue.getEncapsulatedPackets().size() > 0) {
            this.fireQueue();
        }
    }

    private synchronized void sendAllACK() {
        if (this.queueACK.isEmpty()) {
            return;
        }
        int[] ackSeqs = ArrayUtils.toPrimitive(this.queueACK.toArray(new Integer[0]));
        Arrays.sort(ackSeqs);
        this.queueACK.clear();
        ByteArrayOutputStream allRecBos = new ByteArrayOutputStream();
        PEBinaryWriter allRecWriter = new PEBinaryWriter(allRecBos);
        try {
            int count = ackSeqs.length;
            int records = 0;
            if (count > 0) {
                int pointer = 1;
                int start = ackSeqs[0];
                int last = ackSeqs[0];
                ByteArrayOutputStream recBos = new ByteArrayOutputStream();
                PEBinaryWriter recWriter;
                while (pointer < count) {
                    int current = ackSeqs[pointer++];
                    int diff = current - last;
                    if (diff == 1) {
                        last = current;
                    } else if (diff > 1) { //Forget about duplicated packets (bad queues?)
                        recBos.reset();
                        recWriter = new PEBinaryWriter(recBos);
                        if (start == last) {
                            recWriter.writeByte((byte) 0x01);
                            recWriter.writeTriad(start);
                            start = last = current;
                        } else {
                            recWriter.writeByte((byte) 0x00);
                            recWriter.writeTriad(start);
                            recWriter.writeTriad(last);
                            start = last = current;
                        }
                        records++;
                    }
                }
                if (start == last) {
                    allRecWriter.writeByte((byte) 0x01);
                    allRecWriter.writeTriad(start);
                } else {
                    allRecWriter.writeByte((byte) 0x00);
                    allRecWriter.writeTriad(start);
                    allRecWriter.writeTriad(last);
                }
                records++;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PEBinaryWriter writer = new PEBinaryWriter(allRecBos);
            writer.writeByte((byte) 0xC0);
            writer.writeShort((short) (records & 0xFFFF));
            writer.write(allRecBos.toByteArray());
            this.dServer.getNetworkHandler().send(bos.toByteArray(), this.remoteAddress);
        } catch (IOException e) {
        }
    }

    private synchronized void sendAllNACK() {
        if (this.queueNACK.isEmpty()) {
            return;
        }
        int[] ackSeqs = ArrayUtils.toPrimitive(this.queueNACK.toArray(new Integer[0]));
        Arrays.sort(ackSeqs);
        this.queueNACK.clear();
        ByteArrayOutputStream allRecBos = new ByteArrayOutputStream();
        PEBinaryWriter allRecWriter = new PEBinaryWriter(allRecBos);
        try {
            int count = ackSeqs.length;
            int records = 0;
            if (count > 0) {
                int pointer = 1;
                int start = ackSeqs[0];
                int last = ackSeqs[0];
                ByteArrayOutputStream recBos = new ByteArrayOutputStream();
                PEBinaryWriter recWriter;
                while (pointer < count) {
                    int current = ackSeqs[pointer++];
                    int diff = current - last;
                    if (diff == 1) {
                        last = current;
                    } else if (diff > 1) { //Forget about duplicated packets (bad queues?)
                        recBos.reset();
                        recWriter = new PEBinaryWriter(recBos);
                        if (start == last) {
                            recWriter.writeByte((byte) 0x01);
                            recWriter.writeTriad(start);
                            start = last = current;
                        } else {
                            recWriter.writeByte((byte) 0x00);
                            recWriter.writeTriad(start);
                            recWriter.writeTriad(last);
                            start = last = current;
                        }
                        records++;
                    }
                }
                if (start == last) {
                    allRecWriter.writeByte((byte) 0x01);
                    allRecWriter.writeTriad(start);
                } else {
                    allRecWriter.writeByte((byte) 0x00);
                    allRecWriter.writeTriad(start);
                    allRecWriter.writeTriad(last);
                }
                records++;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PEBinaryWriter writer = new PEBinaryWriter(allRecBos);
            writer.writeByte((byte) 0xA0);
            writer.writeShort((short) (records & 0xFFFF));
            writer.write(allRecBos.toByteArray());
            this.dServer.getNetworkHandler().send(bos.toByteArray(), this.remoteAddress);
        } catch (IOException e) {
        }
    }

    /**
     * Process a ACK packet
     *
     * @param buffer The ACK packet binary array
     */
    public void processACKPacket(byte[] buffer) {
        try {
            PEBinaryReader reader = new PEBinaryReader(new ByteArrayInputStream(buffer));
            int count = reader.readShort();
            List<Integer> packets = new ArrayList<>();
            for (int i = 0; i < count && reader.available() > 0; ++i) {
                byte[] tmp = new byte[6];
                if (reader.readByte() == (byte) 0x00) {
                    int start = reader.readTriad();
                    int end = reader.readTriad();
                    if ((end - start) > 4096) {
                        end = start + 4096;
                    }
                    for (int c = start; c <= end; ++c) {
                        packets.add(c);
                    }
                } else {
                    packets.add(reader.readTriad());
                }
            }
            int[] seqNums = ArrayUtils.toPrimitive(packets.toArray(new Integer[0]));
            for (int seq : seqNums) {
                if (this.cachedOutgoingPacket.containsKey(seq)) {
                    this.cachedOutgoingPacket.remove(seq);
                }
            }
        } catch (IOException e) {
        }
    }

    /**
     * Process a NACK packet
     *
     * @param buffer The NACK packet binary array
     */
    public void processNACKPacket(byte[] buffer) {
        try {
            PEBinaryReader reader = new PEBinaryReader(new ByteArrayInputStream(buffer));
            int count = reader.readShort();
            List<Integer> packets = new ArrayList<>();
            for (int i = 0; i < count && reader.available() > 0; ++i) {
                byte[] tmp = new byte[6];
                if (reader.readByte() == (byte) 0x00) {
                    int start = reader.readTriad();
                    int end = reader.readTriad();
                    if ((end - start) > 4096) {
                        end = start + 4096;
                    }
                    for (int c = start; c <= end; ++c) {
                        packets.add(c);
                    }
                } else {
                    packets.add(reader.readTriad());
                }
            }
            int[] seqNums = ArrayUtils.toPrimitive(packets.toArray(new Integer[0]));
            for (int seq : seqNums) {
                if (this.cachedOutgoingPacket.containsKey(seq)) {
                    this.dServer.networkHandler.getUdp().send(this.cachedOutgoingPacket.get(seq).getData(), this.remoteAddress);
                }
            }
        } catch (IOException e) {
        }
    }

    /**
     * Send a packet to the client with a reliability defined
     *
     * @param packet Packet to send
     * @param reliability Packet reliability
     */
    public void send(PEPacket packet, int reliability) {
        if(!(packet instanceof PEPacket)) return;
        packet.encode();
        if (this.queue.getLength() > this.clientMTU) {
            this.fireQueue();
        }
        EncapsulatedPacket[] encapsulatedPacket = EncapsulatedPacket.fromPEPacket(this, packet, reliability);
        for (EncapsulatedPacket ePacket : encapsulatedPacket) {
            ePacket.encode();
            if (this.queue.getLength() + ePacket.getData().length > this.clientMTU - 24) {
                this.fireQueue();
            }
            this.queue.getEncapsulatedPackets().add(ePacket);
        }
    }
    
    /***
     * Send a packet to the client with default packet reliability 2
     * @param packet Packet to send
     */
    public void send(PEPacket packet){
        this.send(packet, 2);
    }
    
    

    private synchronized void fireQueue() {
        this.cachedOutgoingPacket.put(this.queue.getSequenceNumber(), this.queue);
        this.queue.encode();
        this.dServer.getNetworkHandler().getUdp().send(this.queue.getData(), this.remoteAddress);
        this.queue = new RaknetDataPacket(this.sequenceNum++);
    }

    /**
     * Send a message to the client
     *
     * @param message Message to send
     */
    @Override
    public void send(Message message) throws ChannelClosedException {
        PEPacket[] packets = this.translator.translateToPE(message);
        if (packets == null) {
            return;
        }
        for (PEPacket packet : packets) {
            if (packet == null) {
                continue;
            }
            this.send(packet);
        }
    }

    /**
     * Send multiple messages
     *
     * @param messages Messages to send
     */
    @Override
    public void sendAll(Message... messages) throws ChannelClosedException {
        for (Message message : messages) {
            this.send(message);
        }
    }

    /**
     * Send a message to the client
     *
     * @param message Message to send
     * @return Returns nothing, just for implementing the interface
     */
    @Override
    public ChannelFuture sendWithFuture(Message message) {
        this.send(message);
        return null;
    }

    public void processDataPacket(RaknetDataPacket dataPacket) {
        if (dataPacket.getSequenceNumber() - this.lastSequenceNum > 1) {
            for (int i = this.lastSequenceNum + 1; i < dataPacket.getSequenceNumber(); i++) {
                this.queueNACK.add(i);
            }
        } else {
            this.lastSequenceNum = dataPacket.getSequenceNumber();
        }
        this.queueACK.add(dataPacket.getSequenceNumber());
        if (dataPacket.getEncapsulatedPackets().isEmpty()) {
            return;
        }
        for (EncapsulatedPacket epacket : dataPacket.getEncapsulatedPackets()) {
            PEPacket packet = PEPacket.fromBinary(epacket.buffer);
            if (packet == null) {
                continue;
            }
            switch (packet.pid()) {
                case PEPacketIDs.CLIENT_CONNECT:
                    this.clientSessionID = ((ClientConnectPacket)packet).sessionID;
                    ServerHandshakePacket pkServerHandshake = new ServerHandshakePacket();
                    pkServerHandshake.port = (short)(this.remotePort & 0xFFFF);
                    pkServerHandshake.session = this.clientSessionID;
                    pkServerHandshake.session2 = 0x04440BA9L;
                    this.send(pkServerHandshake);
                    break;
                case PEPacketIDs.PING:
                    PingPongPacket pkPong = new PingPongPacket();
                    pkPong.pingID = ((PingPongPacket)packet).pingID;
                    this.send(pkPong, 0);
                    break;
                default:
                    if(!(this.translator instanceof Translator)) break;
                    Message[] msgs = this.translator.translateToPC(packet);
                    if (msgs == null) {
                        return;
                    }
                    for (Message msg : msgs) {
                        if (msg == null) {
                            continue;
                        }
                        this.messageReceived(msg);
                    }
                    break;
            }
        }
    }

    @Override
    @Deprecated
    public void enableCompression(int threshold) {
    }

    @Override
    @Deprecated
    public void enableEncryption(SecretKey sharedSecret) {
    }

    /**
     * Sets the player associated with this session.
     *
     * @param profile The player's profile with name and UUID information.
     * @throws IllegalStateException if there is already a player associated
     * with this session.
     */
    @Override
    public void setPlayer(PlayerProfile profile) {
        if (this.getPlayer() != null) {
            throw new IllegalStateException("Cannot set player twice");
        }

        // isActive check here in case player disconnected during authentication
        if (!isActive()) {
            // no need to call onDisconnect() since it only does anything if there's a player set
            return;
        }

        // initialize the player
        PlayerDataService.PlayerReader reader = this.getServer().getPlayerDataService().beginReadingData(profile.getUniqueId());
        //this.player = new GlowPlayer(this, profile, reader);
        this.player = new DragonetPlayer(this, profile, reader);

        // isActive check here in case player disconnected after authentication,
        // but before the GlowPlayer initialization was completed
        if (!isActive()) {
            onDisconnect();
            return;
        }

        // login event
        PlayerLoginEvent event = EventFactory.onPlayerLogin(player, this.getHostname());
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            disconnect(event.getKickMessage(), true);
            return;
        }

        // Kick other players with the same UUID
        for (GlowPlayer other : getServer().getOnlinePlayers()) {
            if (other != player && other.getUniqueId().equals(player.getUniqueId())) {
                other.getSession().disconnect("You logged in from another location.", true);
                break;
            }
        }

        player.getWorld().getRawPlayers().add(player);

        GlowServer.logger.info(player.getName() + " [" + this.getAddress() + "] connected, UUID: " + player.getUniqueId());

        // message and user list
        String message = EventFactory.onPlayerJoin(player).getJoinMessage();
        if (message != null && !message.isEmpty()) {
            this.getServer().broadcastMessage(message);
        }

        // todo: display names are included in the outgoing messages here, but
        // don't show up on the client. A workaround or proper fix is needed.
        Message addMessage = new UserListItemMessage(UserListItemMessage.Action.ADD_PLAYER, player.getUserListEntry());
        List<UserListItemMessage.Entry> entries = new ArrayList<>();
        for (GlowPlayer other : this.getServer().getOnlinePlayers()) {
            if (other != player && other.canSee(player)) {
                other.getSession().send(addMessage);
            }
            if (player.canSee(other)) {
                entries.add(other.getUserListEntry());
            }
        }
        send(new UserListItemMessage(UserListItemMessage.Action.ADD_PLAYER, entries));
    }

}
