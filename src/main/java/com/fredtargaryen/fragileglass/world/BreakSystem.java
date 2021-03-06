package com.fredtargaryen.fragileglass.world;

import com.fredtargaryen.fragileglass.DataReference;
import com.fredtargaryen.fragileglass.FragileGlassBase;
import com.fredtargaryen.fragileglass.entity.capability.IBreakCapability;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Iterator;
import java.util.concurrent.ExecutionException;

import static com.fredtargaryen.fragileglass.FragileGlassBase.BREAKCAP;

public class BreakSystem
{
    private World world;
    public void init(World world)
    {
        this.world = world;
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void end(World world)
    {
        if(this.world == world) {
            MinecraftForge.EVENT_BUS.unregister(this);
        }
    }

    @SubscribeEvent(priority= EventPriority.HIGHEST)
    public void breakCheck(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.START)
        {
            //foreach leads to ConcurrentModificationExceptions
            Iterator<Entity> i = event.world.loadedEntityList.iterator();
            while(i.hasNext())
            {
                Entity e = i.next();
                if(!e.isDead) {
                    //Entities must have an instance of IBreakCapability or they will never be able to break blocks with
                    //IFragileCapability.
                    if (e.hasCapability(BREAKCAP, null)) {
                        IBreakCapability ibc = e.getCapability(BREAKCAP, null);
                        //Update the capability before determining speed. Convenience method; not used by default
                        ibc.update(e);
                        //Get the squared speed; just to avoid performing a sqrt operation more often than necessary
                        double speedSq = ibc.getSpeedSquared(e);
                        if (this.isValidMoveSpeedSquared(speedSq)) {
                            double speed = Math.sqrt(speedSq);
                            //Check the entity is currently able to break blocks.
                            //Checking whether the block is currently able to break would happen in IFragileCapability#onCrash.
                            if (ibc.isAbleToBreak(e, speed)) {
                                this.breakBlocksInWay(e, ibc.getMotionX(e), ibc.getMotionY(e), ibc.getMotionZ(e), speed, ibc.getNoOfBreaks(e));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Using a new physics system. It's an improvement in the crash physics by breaking blocks ahead of the entity,
     * so that they won't collide with the blocks before they break, and lose all speed. The ability to break blocks is
     * now tied to Capabilities. This is only called for entities which have a Capability extending IBreakCapability.
     * The calculations are more complicated and use the event bus, but are less frequent. I believe performance is
     * slightly improved this way - at the very least, the effect is better and modders can integrate with this mod more
     * easily.
     * The method loops according to noOfBreaks. See IBreakCapability#getNumberOfBreaks for advice on the value of
     * noOfBreaks. Each loop offsets the bounding box and performs another break at the given speed. If noOfBreaks is 3
     * and the speed is 0.3, this attempts 3 breaks, over a distance of 0.9 blocks.
     * NOTE: Depending on implementation this may not be the same as 1 break and a speed of 0.9. For example 1 break at
     * speed 0.6 would break any fragile glass 0.6 blocks away, but 3 breaks at speed 0.2 would not break any fragile
     * glass because 0.2 is too slow.
     * 1.   [motionX, motionY, motionZ] make up a 3D vector representing the amount by which the
     *      entity will move this tick. If this vector intersects the fragile block's bounding
     *      box, then the entity intends to pass through the block this tick so onCrash should be called.
     *      This avoids the problem of the previous system (see step P).
     * 2.   It is not enough to only look at the vector, as in general the vector will only pass
     *      through one block in a fragile glass wall (not enough for larger entities to get
     *      through). Instead the bounding box of the entity has to be "stretched" along the vector
     *      so that all blocks it intersects with will break, always providing a large enough gap.
     * 3.   If the entity is moving diagonally this creates a shape which is not a cube, so cannot
     *      be represented using AxisAlignedBB. Instead, AxisAlignedBB#offset(x, y, z) will be
     *      used to effectively move the entity bounding box along the movement vector, checking for
     *      intersections with block bounding boxes along the way. Upon any such intersections, onCrash is called.
     *      The implementation of this "algorithm" is explained further in inline comments below.
     * P.   This problem is most clear when a player falls onto a fragile glass ceiling. Rather than
     *      smoothly crashing through the ceiling and being damaged when they hit the floor, the
     *      player instead hits the glass ceiling (cancelling their downward movement), gets damaged,
     *      then crashes through to the floor. This problem makes shooting a fragile glass wall
     *      disappointing as well, because the arrow hits the wall (losing all its speed), then
     *      breaks the wall, then falls down as the block is no longer there.
     * @param e The entity that is moving.
     * @param xToUse The x motion value to use; not necessarily e.motionX, especially in the player's case.
     * @param yToUse The y motion value to use; not necessarily e.motionY, especially in the player's case.
     * @param zToUse The z motion value to use; not necessarily e.motionZ, especially in the player's case.
     * @param distance The distance in blocks that Entity e will travel in this current tick.
     * @param noOfBreaks Effectively multiplies the range of blocks to call onCrash on, but does not multiply the
     *                   speed of e when onCrash is called.
     */
    private void breakBlocksInWay(Entity e, double xToUse, double yToUse, double zToUse, double distance, byte noOfBreaks)
    {
        AxisAlignedBB originalAABB = e.getEntityBoundingBox();
        AxisAlignedBB aabb;
        for(byte breaks = 0; breaks < noOfBreaks; ++breaks) {
            aabb = originalAABB;
            double xComp = xToUse / distance;
            double yComp = yToUse / distance;
            double zComp = zToUse / distance;
            while (distance > 1.0) {
                //The end of the movement vector is more than one block away from the current
                //entity bounding box, so at the end of the tick it will have passed through
                //at least one whole block. Offset the entity bounding box by a distance of
                //1m (the length of a block), and check that it intersects with any fragile
                //block bounding boxes.
                aabb = aabb.offset(xComp, yComp, zComp);
                distance -= 1.0;
                this.breakNearbyFragileBlocks(e, aabb, distance);
            }
            //The end of the movement vector is now less than one block away from the current
            //entity bounding box. Offset the entity bounding box right to the end of the
            //movement vector, and check that it intersects with the block bounding box.
            originalAABB = originalAABB.offset(xToUse, yToUse, zToUse);
            this.breakNearbyFragileBlocks(e, originalAABB, distance);
        }
    }

    /**
     * @param e The entity doing the breaking
     * @param aabb The bounding box to break blocks around
     * @param speed The speed e is travelling at
     */
    private void breakNearbyFragileBlocks(Entity e, AxisAlignedBB aabb, double speed)
    {
        BlockPos blockPos;
        Block block;
        for (double x = Math.floor(aabb.minX); x < Math.ceil(aabb.maxX); ++x)
        {
            for (double y = Math.floor(aabb.minY); y < Math.ceil(aabb.maxY); ++y)
            {
                for (double z = Math.floor(aabb.minZ); z < Math.ceil(aabb.maxZ); ++z)
                {
                    blockPos = new BlockPos(x, y, z);
                    IBlockState state = e.world.getBlockState(blockPos);
                    block = state.getBlock();
                    //Chances are the block will be an air block (pass through no question) so best check this first
                    if (block != Blocks.AIR)
                    {
                        if (block.hasTileEntity(state)) {
                            TileEntity te = e.world.getTileEntity(blockPos);
                            try{
                                if (te.hasCapability(FragileGlassBase.FRAGILECAP, null)) {
                                    te.getCapability(FragileGlassBase.FRAGILECAP, null).onCrash(state, te, e, speed);
                                }
                            }catch (Exception CodeCrime){}
                        }
                    }
                }
            }
        }
    }

    /**
     * Moving faster than MAXIMUM_ENTITY_SPEED_SQUARED means moving faster than chunks can be loaded.
     * If this is happening there is not much point trying to break blocks.
     */
    private boolean isValidMoveSpeedSquared(double blocksPerTick)
    {
        return blocksPerTick <= DataReference.MAXIMUM_ENTITY_SPEED_SQUARED;
    }
}
