package com.fredtargaryen.fragileglass.tileentity;

import com.fredtargaryen.fragileglass.DataReference;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.*;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ITickable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TileEntityFragile extends TileEntity implements ITickable
{
    public TileEntityFragile(){super();}

    @Override
    public void update()
    {
        if(!this.worldObj.isRemote)
        {
            BlockPos pos = new BlockPos(this.pos);
            //Get all entities near enough to break it if fast enough
            IBlockState myBlockState = this.worldObj.getBlockState(pos);
            AxisAlignedBB normAABB = myBlockState.getBlock().getCollisionBoundingBox(myBlockState, this.worldObj, pos);
            AxisAlignedBB checkAABB = normAABB.expand(DataReference.GLASS_DETECTION_RANGE, DataReference.GLASS_DETECTION_RANGE, DataReference.GLASS_DETECTION_RANGE);
            List<Entity> entities = this.worldObj.getEntitiesWithinAABBExcludingEntity(null, checkAABB);
            Iterator<Entity> remover = entities.iterator();
            while(remover.hasNext())
            {
                Entity nextEnt = remover.next();
                if(!(nextEnt instanceof EntityLivingBase
                        || nextEnt instanceof EntityArrow
                        || nextEnt instanceof EntityFireball
                        || nextEnt instanceof EntityMinecart
                        || nextEnt instanceof EntityFallingBlock
                        || nextEnt instanceof EntityFireworkRocket
                        || nextEnt instanceof EntityBoat
                        || nextEnt instanceof EntityTNTPrimed))
                {
                    remover.remove();
                }
            }
            if (entities.size() > 0)
            {
                //Check if any of the possible entities are fast enough
                boolean stop = false;
                remover = entities.iterator();
                while(!stop && remover.hasNext())
                {
                    Entity nextEnt = remover.next();
                    if(nextEnt instanceof EntityFallingBlock)
                    {
                        this.worldObj.destroyBlock(pos, false);
                        stop = true;
                    }
                    else
                    {
                        double mx = nextEnt.motionX;
                        double my = nextEnt.motionY;
                        double mz = nextEnt.motionZ;
                        if ((nextEnt.posX < normAABB.minX && mx > DataReference.MINIMUM_ENTITY_SPEED)
                                || (nextEnt.posX > normAABB.maxX && mx < -1 * DataReference.MINIMUM_ENTITY_SPEED)
                                || (nextEnt.posY < normAABB.maxY && my > DataReference.MINIMUM_ENTITY_SPEED)
                                || (nextEnt.posY > normAABB.maxY && my < -1 * DataReference.MINIMUM_ENTITY_SPEED)
                                || (nextEnt.posZ < normAABB.maxZ && mz > DataReference.MINIMUM_ENTITY_SPEED)
                                || (nextEnt.posZ > normAABB.maxZ && mz < -1 * DataReference.MINIMUM_ENTITY_SPEED)) {
                            //breaks it
                            this.worldObj.destroyBlock(pos, false);
                            stop = true;
                        }
                    }
                }
            }
        }
    }
}