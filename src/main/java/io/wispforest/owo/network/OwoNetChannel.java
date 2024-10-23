package io.wispforest.owo.network;

import io.wispforest.endec.impl.RecordEndec;
import io.wispforest.endec.impl.ReflectiveEndecBuilder;
import io.wispforest.owo.mixin.ServerCommonNetworkHandlerAccessor;
import io.wispforest.endec.Endec;
import io.wispforest.endec.StructEndec;
import io.wispforest.owo.serialization.CodecUtils;
import io.wispforest.owo.serialization.endec.MinecraftEndecs;
import io.wispforest.owo.util.OwoFreezer;
import io.wispforest.owo.util.ReflectionUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLLoader;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * An efficient networking abstraction that uses {@code record}s to store
 * and define packet data. Serialization for most types is fully automatic
 * and no custom handling needs to be done.
 *
 * <p> Should one of your record components be of an unsupported type, either use {@link io.wispforest.endec.impl.ReflectiveEndecBuilder#register(Endec, Class)}
 * to register an appropriate endec, or supply it directly using {@link #registerClientbound(Class, StructEndec, ChannelHandler)} and {@link #registerServerbound(Class, StructEndec, ChannelHandler)}
 *
 * <p> To define a packet class suited for use with this wrapper, simply create a
 * standard Java {@code record} class and put the desired data into the record header.
 *
 * <p>To register a packet onto this channel, use either {@link #registerClientbound(Class, ChannelHandler)}
 * or {@link #registerServerbound(Class, ChannelHandler)}, depending on which direction the packet goes.
 * Bidirectional registration of the same class is explicitly supported. <b>For synchronization purposes,
 * all registration must happen on both client and server, even for clientbound packets. Otherwise,
 * joining the server will fail with a handshake error</b>
 *
 * <p>To send a packet, use any of the {@code handle} methods to obtain a handle for sending. These are
 * named after where the packet is sent <i>from</i>, meaning the {@link #clientHandle()} is used for sending
 * <i>to the server</i> and vice-versa.
 *
 * <p> The registered packet handlers are executed synchronously on the target environment's
 * game thread instead of Netty's event loops - there is no need to call {@code .execute(...)}
 */
public class OwoNetChannel {

    static final Map<Identifier, OwoNetChannel> REGISTERED_CHANNELS = new HashMap<>();
    static final Map<Identifier, OwoNetChannel> REQUIRED_CHANNELS = new HashMap<>();
    static final Map<Identifier, OwoNetChannel> OPTIONAL_CHANNELS = new HashMap<>();

    private final ReflectiveEndecBuilder builder;

    private final Map<Class<?>, IndexedEndec<?>> endecsByClass = new HashMap<>();
    final Int2ObjectMap<IndexedEndec<?>> endecsByIndex = new Int2ObjectOpenHashMap<>();

    private final List<ChannelHandler<Record, ClientAccess>> clientHandlers = new ArrayList<>();
    private final List<ChannelHandler<Record, ServerAccess>> serverHandlers = new ArrayList<>();

    private final Reference2IntMap<Class<?>> deferredClientEndecs = new Reference2IntOpenHashMap<>();

    final CustomPayload.Id<MessagePayload> packetId;
    private final String ownerClassName;
    final boolean required;

    private ClientHandle clientHandle = null;
    private ServerHandle serverHandle = null;

    /**
     * Creates a new required channel with given ID. Duplicate channel
     * IDs are not allowed - if there is a collision, the name of the
     * class that previously registered the channel will be part of
     * the exception. <b>This may be called at any stage during
     * mod initialization</b>
     *
     * @param id The desired channel ID
     * @return The created channel
     */
    public static OwoNetChannel create(Identifier id) {
        return new OwoNetChannel(id, ReflectionUtils.getCallingClassName(2), true);
    }

    /**
     * Creates a new optional channel with given ID. Duplicate channel
     * IDs are not allowed - if there is a collision, the name of the
     * class that previously registered the channel will be part of
     * the exception. <b>This may be called at any stage during
     * mod initialization</b>
     *
     * @param id The desired channel ID
     * @return The created channel
     */
    public static OwoNetChannel createOptional(Identifier id) {
        return new OwoNetChannel(id, ReflectionUtils.getCallingClassName(2), false);
    }

    private OwoNetChannel(Identifier id, String ownerClassName, boolean required) {
        OwoFreezer.checkRegister("Network channels");

        this.builder = new ReflectiveEndecBuilder(builder -> {
            builder.register(Endec.VAR_INT, Integer.class, int.class);
            builder.register(Endec.VAR_LONG, Long.class, long.class);
            MinecraftEndecs.addDefaults(builder);
        });

        if (REGISTERED_CHANNELS.containsKey(id)) {
            throw new IllegalStateException("Channel with id '" + id + "' was already registered from class '" + REGISTERED_CHANNELS.get(id).ownerClassName + "'");
        }

        this.deferredClientEndecs.defaultReturnValue(-1);

        this.packetId = new CustomPayload.Id<>(id);
        this.ownerClassName = ownerClassName;
        this.required = required;

        OwoHandshake.enable();
        if (required) {
            OwoHandshake.requireHandshake();
        }

        Endec<MessagePayload> serverEndec = Endec.<Record, Integer>dispatched(
            index -> this.endecsByIndex.get(index).endec,
            msg -> this.endecsByClass.get(msg.getClass()).serverHandlerIndex,
            Endec.VAR_INT
        )
            .xmap(x -> new MessagePayload(this.packetId, x), x -> x.message);

        Endec<MessagePayload> clientEndec = Endec.<Record, Integer>dispatched(
                index -> this.endecsByIndex.get(-index).endec,
                msg -> this.endecsByClass.get(msg.getClass()).clientHandlerIndex,
                Endec.VAR_INT
            )
            .xmap(x -> new MessagePayload(this.packetId, x), x -> x.message);

        PayloadTypeRegistry.playC2S().register(this.packetId, CodecUtils.toPacketCodec(serverEndec));
        PayloadTypeRegistry.playS2C().register(this.packetId, CodecUtils.toPacketCodec(clientEndec));

        ServerPlayNetworking.registerGlobalReceiver(this.packetId, (payload, context) -> {
            serverHandlers.get(endecsByClass.get(payload.message().getClass()).serverHandlerIndex).handle(payload.message, new ServerAccess(context.player()));
        });

        if (FMLLoader.getDist() == Dist.CLIENT) {
            ClientPlayNetworking.registerGlobalReceiver(this.packetId, (payload, context) -> {
                clientHandlers.get(endecsByClass.get(payload.message.getClass()).clientHandlerIndex).handle(payload.message, new ClientAccess(context.player().networkHandler));
            });
        }

        clientHandlers.add(null);
        serverHandlers.add(null);
        REGISTERED_CHANNELS.put(id, this);

        if (required) {
            REQUIRED_CHANNELS.put(id, this);
        } else {
            OPTIONAL_CHANNELS.put(id, this);
        }
    }

    public OwoNetChannel addEndecs(Consumer<ReflectiveEndecBuilder> endecBuilder) {
        endecBuilder.accept(this.builder);

        return this;
    }

    public ReflectiveEndecBuilder builder() {
        return this.builder;
    }

    /**
     * Registers a handler <i>on the client</i> for the specified message class.
     * This also ensures the required endec is available. If an exception
     * about a missing endec is thrown, register one
     *
     * @param messageClass The type of packet data to send and serialize
     * @param handler      The handler that will receive the deserialized
     * @see #serverHandle(PlayerEntity)
     * @see #serverHandle(MinecraftServer)
     * @see #serverHandle(ServerWorld, BlockPos)
     */
    public <R extends Record> void registerClientbound(Class<R> messageClass, ChannelHandler<R, ClientAccess> handler) {
        registerClientbound(messageClass, handler, () -> RecordEndec.create(this.builder, messageClass));
    }

    /**
     * Registers a message class <i>on the client</i> with deferred handler registration.
     * This also ensures the required endec is available. If an exception
     * about a missing endec is thrown, register one
     *
     * @param messageClass The type of packet data to send and serialize
     * @see #serverHandle(PlayerEntity)
     * @see #serverHandle(MinecraftServer)
     * @see #serverHandle(ServerWorld, BlockPos)
     */
    public <R extends Record> void registerClientboundDeferred(Class<R> messageClass) {
        registerClientboundDeferred(messageClass, () -> RecordEndec.create(this.builder, messageClass));
    }

    /**
     * Registers a handler <i>on the server</i> for the specified message class.
     * This also ensures the required endec is available. If an exception
     * about a missing endec is thrown, register one
     *
     * @param messageClass The type of packet data to send and serialize
     * @param handler      The handler that will receive the deserialized
     * @see #clientHandle()
     */
    public <R extends Record> void registerServerbound(Class<R> messageClass, ChannelHandler<R, ServerAccess> handler) {
        registerServerbound(messageClass, handler, () -> RecordEndec.create(this.builder, messageClass));
    }

    //--

    /**
     * Registers a handler <i>on the client</i> for the specified message class
     *
     * @param messageClass The type of packet data to send and serialize
     * @param endec        The endec to serialize messages with
     * @param handler      The handler that will receive the deserialized
     * @see #serverHandle(PlayerEntity)
     * @see #serverHandle(MinecraftServer)
     * @see #serverHandle(ServerWorld, BlockPos)
     */
    public <R extends Record> void registerClientbound(Class<R> messageClass, StructEndec<R> endec, ChannelHandler<R, ClientAccess> handler) {
        registerClientbound(messageClass, handler, () -> endec);
    }

    /**
     * Registers a message class <i>on the client</i> with deferred handler registration
     *
     * @param messageClass The type of packet data to send and serialize
     * @param endec        The endec to serialize messages with
     * @see #serverHandle(PlayerEntity)
     * @see #serverHandle(MinecraftServer)
     * @see #serverHandle(ServerWorld, BlockPos)
     */
    public <R extends Record> void registerClientboundDeferred(Class<R> messageClass, StructEndec<R> endec) {
        registerClientboundDeferred(messageClass, () -> endec);
    }

    /**
     * Registers a handler <i>on the server</i> for the specified message class
     *
     * @param messageClass The type of packet data to send and serialize
     * @param endec        The endec to serialize messages with
     * @param handler      The handler that will receive the deserialized
     * @see #clientHandle()
     */
    public <R extends Record> void registerServerbound(Class<R> messageClass, StructEndec<R> endec, ChannelHandler<R, ServerAccess> handler) {
        registerServerbound(messageClass, handler, () -> endec);
    }

    //--

    @SuppressWarnings("unchecked")
    private  <R extends Record> void registerClientbound(Class<R> messageClass, ChannelHandler<R, ClientAccess> handler, Supplier<StructEndec<R>> endec) {
        int deferredIndex = deferredClientEndecs.removeInt(messageClass);
        if (deferredIndex != -1) {
            OwoFreezer.checkRegister("Network handlers");

            this.clientHandlers.set(deferredIndex, (ChannelHandler<Record, ClientAccess>) handler);
            return;
        }

        int index = this.clientHandlers.size();
        this.createEndec(messageClass, index, Dist.CLIENT, endec);
        this.clientHandlers.add((ChannelHandler<Record, ClientAccess>) handler);
    }

    private <R extends Record> void registerClientboundDeferred(Class<R> messageClass, Supplier<StructEndec<R>> endec) {
        int index = this.clientHandlers.size();
        this.createEndec(messageClass, index, Dist.CLIENT, endec);
        this.clientHandlers.add(null);

        this.deferredClientEndecs.put(messageClass, index);
    }

    @SuppressWarnings("unchecked")
    private <R extends Record> void registerServerbound(Class<R> messageClass, ChannelHandler<R, ServerAccess> handler, Supplier<StructEndec<R>> endec) {
        int index = this.serverHandlers.size();
        this.createEndec(messageClass, index, Dist.DEDICATED_SERVER, endec);
        this.serverHandlers.add((ChannelHandler<Record, ServerAccess>) handler);
    }

    //--

    public boolean canSendToPlayer(ServerPlayerEntity player) {
        return canSendToPlayer(player.networkHandler);
    }

    public boolean canSendToPlayer(ServerPlayNetworkHandler networkHandler) {
        if (required) return true;

        return OwoHandshake.isValidClient() ?
                getChannelSet(((ServerCommonNetworkHandlerAccessor) networkHandler).owo$getConnection()).contains(this.packetId.id())
                : ServerPlayNetworking.canSend(networkHandler, this.packetId);
    }

    @OnlyIn(Dist.CLIENT)
    public boolean canSendToServer() {
        if (required) return true;

        return OwoHandshake.isValidClient() ?
                getChannelSet(MinecraftClient.getInstance().getNetworkHandler().getConnection()).contains(this.packetId.id())
                : ClientPlayNetworking.canSend(this.packetId);
    }

    private static Set<Identifier> getChannelSet(ClientConnection connection) {
        return ((OwoClientConnectionExtension) connection).owo$getChannelSet();
    }

    /**
     * Obtains the client handle of this channel, used to
     * send packets <i>to the server</i>
     *
     * @return The client handle of this channel
     */
    public ClientHandle clientHandle() {
        if (FMLLoader.getDist() != Dist.CLIENT)
            throw new NetworkException("Cannot obtain client handle in environment type '" + FMLLoader.getDist() + "'");

        if (this.clientHandle == null) this.clientHandle = new ClientHandle();
        return clientHandle;
    }

    /**
     * Obtains a server handle used to send packets
     * <i>to all players on the given server</i>
     * <p>
     * <b>This handle will be reused - do not retain references</b>
     *
     * @param server The server to target
     * @return A server handle configured for sending packets
     * to all players on the given server
     */
    public ServerHandle serverHandle(MinecraftServer server) {
        var handle = getServerHandle();
        handle.targets = PlayerLookup.all(server);
        return handle;
    }

    /**
     * Obtains a server handle used to send packets
     * <i>to all given players</i>. Use {@link PlayerLookup} to obtain
     * the required collections
     * <p>
     * <b>This handle will be reused - do not retain references</b>
     *
     * @param targets The players to target
     * @return A server handle configured for sending packets
     * to all players in the given collection
     * @see PlayerLookup
     */
    public ServerHandle serverHandle(Collection<ServerPlayerEntity> targets) {
        var handle = getServerHandle();
        handle.targets = targets;
        return handle;
    }

    /**
     * Obtains a server handle used to send packets
     * <i>to the given player only</i>
     * <p>
     * <b>This handle will be reused - do not retain references</b>
     *
     * @param player The player to target
     * @return A server handle configured for sending packets
     * to the given player only
     */
    public ServerHandle serverHandle(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) throw new NetworkException("'player' must be a 'ServerPlayerEntity'");

        var handle = getServerHandle();
        handle.targets = Collections.singleton(serverPlayer);
        return handle;
    }

    /**
     * Obtains a server handle used to send packets
     * <i>to all players tracking the given block entity</i>
     * <p>
     * <b>This handle will be reused - do not retain references</b>
     *
     * @param entity The block entity to look up trackers for
     * @return A server handle configured for sending packets
     * to all players tracking the given block entity
     */
    public ServerHandle serverHandle(BlockEntity entity) {
        if (entity.getWorld().isClient) throw new NetworkException("Server handle cannot be obtained on the client");
        return serverHandle(PlayerLookup.tracking(entity));
    }

    /**
     * Obtains a server handle used to send packets <i>to all
     * players tracking the given position in the given world</i>
     * <p>
     * <b>This handle will be reused - do not retain references</b>
     *
     * @param world The world to look up players in
     * @param pos   The position to look up trackers for
     * @return A server handle configured for sending packets
     * to all players tracking the given position in the given world
     */
    public ServerHandle serverHandle(ServerWorld world, BlockPos pos) {
        return serverHandle(PlayerLookup.tracking(world, pos));
    }

    private ServerHandle getServerHandle() {
        if (this.serverHandle == null) this.serverHandle = new ServerHandle();
        return serverHandle;
    }

    private <R extends Record> void createEndec(Class<R> messageClass, int handlerIndex, Dist target, Supplier<StructEndec<R>> supplier) {
        OwoFreezer.checkRegister("Network handlers");

        var endec = endecsByClass.get(messageClass);
        if (endec == null) {
            final var indexedEndec = IndexedEndec.create(messageClass, supplier.get(), handlerIndex, target);
            endecsByClass.put(messageClass, indexedEndec);
            endecsByIndex.put(target == Dist.CLIENT ? -handlerIndex : handlerIndex, indexedEndec);
        } else if (endec.handlerIndex(target) == -1) {
            endec.setHandlerIndex(handlerIndex, target);
            endecsByIndex.put(target == Dist.CLIENT ? -handlerIndex : handlerIndex, endec);
        } else {
            throw new IllegalStateException("Message class '" + messageClass.getName() + "' is already registered for target environment " + target);
        }
    }

    public class ClientHandle {

        /**
         * Sends the given message to the server
         *
         * @param message The message to send
         * @see #send(Record[])
         */
        public <R extends Record> void send(R message) {
            ClientPlayNetworking.send(new MessagePayload(packetId, message));
        }

        /**
         * Sends the given messages to the server
         *
         * @param messages The messages to send
         */
        @SafeVarargs
        public final <R extends Record> void send(R... messages) {
            for (R message : messages) send(message);
        }
    }

    public class ServerHandle {

        private Collection<ServerPlayerEntity> targets = Collections.emptySet();

        /**
         * Sends the given message to the configured target(s)
         * <b>Resets the target(s) after sending - this cannot be used
         * for multiple messages on the same handle</b>
         *
         * @param message The message to send
         * @see #send(Record[])
         */
        public <R extends Record> void send(R message) {
            this.targets.forEach(player -> ServerPlayNetworking.send(player, new MessagePayload(packetId, message)));
            this.targets = null;
        }

        /**
         * Sends the given messages to the configured target(s)
         * <b>Resets the target(s) after sending - this cannot be used
         * multiple times on the same handle</b>
         *
         * @param messages The messages to send
         */
        @SafeVarargs
        public final <R extends Record> void send(R... messages) {
            this.targets.forEach(player -> {
                for (R message : messages) {
                    ServerPlayNetworking.send(player, new MessagePayload(packetId, message));
                }
            });
            this.targets = null;
        }
    }

    public interface ChannelHandler<R extends Record, E extends EnvironmentAccess<?, ?, ?>> {

        /**
         * Executed on the game thread to handle the incoming
         * message - this can safely modify game state
         *
         * @param message The message that was received
         * @param access  The {@link EnvironmentAccess} used to obtain references
         *                to the execution environment
         */
        void handle(R message, E access);
    }

    /**
     * A simple wrapper that provides access to the environment a packet
     * is being received / message is being handled in
     *
     * @param <P> The type of player to receive the packet
     * @param <R> The runtime that the packet is being received in
     * @param <N> The network handler that received the packet
     */
    public interface EnvironmentAccess<P extends PlayerEntity, R, N> {

        /**
         * @return The player that received the packet
         */
        P player();

        /**
         * @return The environment the packet is being received in,
         * either a {@link MinecraftServer} or a {@link net.minecraft.client.MinecraftClient}
         */
        R runtime();

        /**
         * @return The network handler of the player or client that received the packet,
         * either a {@link net.minecraft.client.network.ClientPlayNetworkHandler} or a
         * {@link net.minecraft.server.network.ServerPlayNetworkHandler}
         */
        N netHandler();
    }

    private void verify() {
        if (FMLLoader.getDist() == Dist.CLIENT) {
            if (!this.deferredClientEndecs.isEmpty()) {
                throw new NetworkException("Some deferred client handlers for channel " + this.packetId + " haven't been registered: " + deferredClientEndecs.keySet().stream().map(Class::getName).collect(Collectors.joining(", ")));
            }
        }
    }

    static {
        OwoFreezer.registerFreezeCallback(() -> {
            for (OwoNetChannel channel : OwoNetChannel.REGISTERED_CHANNELS.values()) {
                channel.verify();
            }
        });
    }

    static final class IndexedEndec<R extends Record> {
        private int clientHandlerIndex = -1;
        private int serverHandlerIndex = -1;

        private final Class<R> recordClass;
        private final StructEndec<R> endec;

        private IndexedEndec(Class<R> recordClass, StructEndec<R> endec) {
            this.endec = endec;
            this.recordClass = recordClass;
        }

        public static <R extends Record> IndexedEndec<R> create(Class<R> rClass, StructEndec<R> endec, int index, Dist target) {
            return new IndexedEndec<>(rClass, endec).setHandlerIndex(index, target);
        }

        public IndexedEndec<R> setHandlerIndex(int index, Dist target) {
            switch (target) {
                case CLIENT -> this.clientHandlerIndex = index;
                case DEDICATED_SERVER -> this.serverHandlerIndex = index;
            }
            return this;
        }

        public int handlerIndex(Dist target) {
            return switch (target) {
                case CLIENT -> clientHandlerIndex;
                case DEDICATED_SERVER -> serverHandlerIndex;
            };
        }

        public Class<R> getRecordClass(){
            return this.recordClass;
        }
    }

    record MessagePayload(CustomPayload.Id<MessagePayload> id, Record message) implements CustomPayload {
        @Override
        public Id<? extends CustomPayload> getId() {
            return id;
        }
    }
}

