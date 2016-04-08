package com.fredtargaryen.fragileglass.worldgen;

import com.fredtargaryen.fragileglass.FragileGlassBase;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.Random;

public class PatchGen implements IWorldGenerator
{
    //If it goes a long time without genning a patch, forces a bonus patch to gen
    private int timeSinceLastPatch;
    private final int timeToWaitBeforeBonusPatch;

    public PatchGen () {
        this.timeSinceLastPatch = 0;
        this.timeToWaitBeforeBonusPatch = FragileGlassBase.genChance + 1;
    }

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        BiomeGenBase b = world.getBiomeGenForCoords(new BlockPos(chunkX, 62, chunkZ));
        if (b.getEnableSnow())
        {
            if(random.nextInt(FragileGlassBase.genChance) == 0)
            {
                this.genPatch(random, chunkX, chunkZ, world);
                this.timeSinceLastPatch = 0;
            }
            else if(this.timeSinceLastPatch == this.timeToWaitBeforeBonusPatch)
            {
                this.genPatch(random, chunkX, chunkZ, world);
                this.timeSinceLastPatch = 0;
            }
            else
            {
                this.timeSinceLastPatch++;
            }
        }
    }

    public void genPatch(Random random, int chunkX, int chunkZ, World world)
    {
        //Coords of middle of patch
        int midX = (chunkX * 16) + random.nextInt(16);
        int midZ = (chunkZ * 16) + random.nextInt(16);
        //Usually the water level...
        int y = 62;
        int patchRad = (int)(((2*random.nextGaussian()) + FragileGlassBase.avePatchSize)/2);
        for(int rad = patchRad; rad > 0; rad--)
        {
            for(double t = 0; t < 360; t += 10)
            {
                BlockPos currentPos = new BlockPos((int)(midX + (rad * Math.cos(t))), y, (int)(midZ + (rad * Math.sin(t))));
                if(world.getBlockState(currentPos).getBlock() == Blocks.ice)
                {
                    //Adds a little randomness to the outside of patches, to avoid perfect circles all the time
                    if(rad > patchRad - 2) {
                        if (random.nextBoolean()) {
                            world.setBlockState(currentPos, FragileGlassBase.thinIce.getDefaultState());
                        }
                    }
                    else
                    {
                        world.setBlockState(currentPos, FragileGlassBase.thinIce.getDefaultState());
                    }
                }
            }
        }
    }
}
