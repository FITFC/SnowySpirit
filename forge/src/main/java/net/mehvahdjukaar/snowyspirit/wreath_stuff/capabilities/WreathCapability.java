package net.mehvahdjukaar.snowyspirit.wreath_stuff.capabilities;

import com.mojang.datafixers.util.Pair;
import net.mehvahdjukaar.snowyspirit.reg.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//provider & instance. Only one instance is attached to a world at a time
public class WreathCapability implements ICapabilitySerializable<CompoundTag> {

    private final LazyOptional<WreathCapability> lazyOptional = LazyOptional.of(() -> this);

    public Map<BlockPos, WreathData> wreathBlocks = new HashMap<>();

    public void invalidate() {
        lazyOptional.invalidate();
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return cap == CapabilityHandler.WREATH_CAPABILITY ?
                lazyOptional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag total = new CompoundTag();
        int i = 0;
        for (BlockPos pos : wreathBlocks.keySet()) {
            NbtUtils.writeBlockPos(pos);
            total.put(i + "", NbtUtils.writeBlockPos(pos));
            i++;
        }
        total.putInt("Count", i);
        return total;
    }

    @Override
    public void deserializeNBT(CompoundTag total) {
        for (int i = 0; i < total.getInt("Count"); i++) {
            CompoundTag tag = total.getCompound(i + "");
            BlockPos pos = NbtUtils.readBlockPos(tag);
            this.addWreath(pos);
        }
    }

    public WreathData addWreath(BlockPos pos) {
        return wreathBlocks.computeIfAbsent(pos, WreathData::new);
    }

    public void removeWreath(BlockPos p, Level level, boolean animationAndDrop) {
        wreathBlocks.remove(p);
        if (animationAndDrop) {
            ItemEntity itementity = new ItemEntity(level, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                    ModRegistry.WREATH.get().asItem().getDefaultInstance());
            itementity.setDefaultPickUpDelay();
            level.addFreshEntity(itementity);
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, p, Block.getId(ModRegistry.WREATH.get().defaultBlockState()));
        }
    }

    public Map<BlockPos, WreathData> getWreathBlocks() {
        return wreathBlocks;
    }

    public boolean hasWreath(BlockPos pos) {
        return this.wreathBlocks.containsKey(pos);
    }

    public void refreshWreathVisual(BlockPos pos, Level level) {
        if (level.isLoaded(pos)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock) {
                Direction dir = state.getValue(DoorBlock.FACING);
                boolean open = state.getValue(DoorBlock.OPEN);
                boolean hinge = state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT;
                WreathData data = this.addWreath(pos);
                data.face = dir;
                data.hinge = hinge;
                data.open = open;
                if (data.needsInitialization) {
                    this.calculateDoorDimensions(level, pos, state, data);
                    data.needsInitialization = false;
                }
            } else {
                this.removeWreath(pos, level, false);
            }
        }
    }

    private void calculateDoorDimensions(Level level, BlockPos pos, BlockState state, WreathData data) {
        state = state.setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.OPEN, Boolean.FALSE)
                .setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT);
        VoxelShape shape = state.getShape(level, pos);
        AABB bounds = shape.bounds();
        if (bounds.maxX - bounds.minX >= 1) {
            double front = bounds.minZ -1;
            double back = -bounds.maxZ;
            data.closedDimensions = Pair.of((float) front, (float) back);
        }
        state = state.setValue(DoorBlock.OPEN, Boolean.TRUE).setValue(DoorBlock.FACING, Direction.EAST);
        shape = state.getShape(level, pos);
        bounds = shape.bounds();
        if (bounds.maxX - bounds.minX >= 1) {
            double front = bounds.minZ -1;
            double back = -bounds.maxZ;
            data.openDimensions = Pair.of((float) front, (float) back);
        }
    }

    public void refreshClientBlocksVisuals(Level level) {
        Set<BlockPos> positions = new HashSet<>(this.wreathBlocks.keySet());
        positions.forEach(p -> refreshWreathVisual(p, level));
    }

    public void updateAllBlocks(ServerLevel level) {
        Set<BlockPos> positions = new HashSet<>(this.wreathBlocks.keySet());
        positions.forEach(p -> {
            //prevents removing when not loaded
            if (level.isLoaded(p)) {
                BlockState state = level.getBlockState(p);
                if (!(state.getBlock() instanceof DoorBlock)) {
                    this.removeWreath(p, level, true);
                }
            }
        });
    }


    public static class WreathData{
        public Direction face = Direction.NORTH;
        public boolean open = true;
        public boolean hinge = true;

        public boolean needsInitialization = true;
        public Pair<Float, Float> openDimensions = null;
        public Pair<Float, Float> closedDimensions = null;

        public WreathData(BlockPos pos) {}
    }

}