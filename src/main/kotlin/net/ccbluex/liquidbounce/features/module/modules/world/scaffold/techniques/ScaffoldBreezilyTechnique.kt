package net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques

import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold.getTargetedPosition
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.techniques.ScaffoldNormalTechnique.NORMAL_INVESTIGATION_OFFSETS
import net.ccbluex.liquidbounce.utils.aiming.Rotation
import net.ccbluex.liquidbounce.utils.aiming.raycast
import net.ccbluex.liquidbounce.utils.block.targetFinding.BlockPlacementTarget
import net.ccbluex.liquidbounce.utils.block.targetFinding.BlockPlacementTargetFindingOptions
import net.ccbluex.liquidbounce.utils.block.targetFinding.CenterTargetPositionFactory
import net.ccbluex.liquidbounce.utils.block.targetFinding.findBestBlockPlacementTarget
import net.ccbluex.liquidbounce.utils.entity.SimulatedPlayer
import net.ccbluex.liquidbounce.utils.entity.getMovementDirectionOfInput
import net.ccbluex.liquidbounce.utils.kotlin.random
import net.ccbluex.liquidbounce.utils.math.geometry.Line
import net.ccbluex.liquidbounce.utils.math.toBlockPos
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.block.Blocks
import net.minecraft.entity.EntityPose
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.floor
import kotlin.math.round

object ScaffoldBreezilyTechnique : ScaffoldTechnique("Breezily") {

    private var lastSideways = 0f
    private var lastAirTime = 0L
    private var currentEdgeDistanceRandom = 0.45

    private val edgeDistance by floatRange(
        "EdgeDistance", 0.45f..0.5f, 0.25f..0.5f, "blocks"
    )

    fun ledge(simulatedPlayer: SimulatedPlayer, rotation: Rotation): Boolean {
        if (!isActive) {
            return false
        }

        val ticks = ModuleScaffold.ScaffoldRotationConfigurable.howLongToReach(rotation)
        val simClone = simulatedPlayer.clone()
        simClone.tick()

        val ledgeSoon = simulatedPlayer.clipLedged || simClone.clipLedged
        return (ticks >= 1 || !ModuleScaffold.hasBlockToBePlaced()) && ledgeSoon
    }

    override fun findPlacementTarget(
        predictedPos: Vec3d,
        predictedPose: EntityPose,
        optimalLine: Line?,
        bestStack: ItemStack
    ): BlockPlacementTarget? {
        val searchOptions = BlockPlacementTargetFindingOptions(
            NORMAL_INVESTIGATION_OFFSETS,
            bestStack,
            CenterTargetPositionFactory,
            BlockPlacementTargetFindingOptions.PRIORITIZE_LEAST_BLOCK_DISTANCE,
            predictedPos,
            predictedPose
        )

        return findBestBlockPlacementTarget(getTargetedPosition(predictedPos.toBlockPos()), searchOptions)
    }

    @Suppress("unused")
    private val handleMovementInput = handler<MovementInputEvent> { event ->
        if (!event.directionalInput.forwards || player.isSneaking) {
            return@handler
        }

        if (world.getBlockState(player.blockPos.offset(Direction.DOWN, 1)).block == Blocks.AIR) {
            lastAirTime = System.currentTimeMillis()
        } else if (System.currentTimeMillis() - lastAirTime > 500) {
            return@handler
        }

        val modX = player.x - floor(player.x)
        val modZ = player.z - floor(player.z)

        val ma = 1 - currentEdgeDistanceRandom
        var currentSideways = 0f
        when (Direction.fromRotation(player.yaw.toDouble())) {
            Direction.SOUTH -> {
                if (modX > ma) currentSideways = 1f
                if (modX < currentEdgeDistanceRandom) currentSideways = -1f
            }

            Direction.NORTH -> {
                if (modX > ma) currentSideways = -1f
                if (modX < currentEdgeDistanceRandom) currentSideways = 1f
            }

            Direction.EAST -> {
                if (modZ > ma) currentSideways = -1f
                if (modZ < currentEdgeDistanceRandom) currentSideways = 1f
            }

            Direction.WEST -> {
                if (modZ > ma) currentSideways = 1f
                if (modZ < currentEdgeDistanceRandom) currentSideways = -1f
            }
            else -> {
                // do nothing
            }
        }

        if (lastSideways != currentSideways && currentSideways != 0f) {
            lastSideways = currentSideways
            currentEdgeDistanceRandom = edgeDistance.random()
        }

        event.directionalInput = DirectionalInput(
            event.directionalInput.forwards,
            event.directionalInput.backwards,
            lastSideways == -1f,
            lastSideways == 1f
        )
    }

    override fun getRotations(target: BlockPlacementTarget?): Rotation? {
        val dirInput = DirectionalInput(player.input)

        if (dirInput == DirectionalInput.NONE) {
            target ?: return null

            return getRotationForNoInput(target)
        }

        val direction = getMovementDirectionOfInput(player.yaw, dirInput) + 180

        // Round to 45°-steps (NORTH, NORTH_EAST, etc.)
        val movingYaw = round(direction / 45) * 45
        val isMovingStraight = movingYaw % 90 == 0f

        return if (isMovingStraight) {
            getRotationForStraightInput(movingYaw)
        } else {
            getRotationForDiagonalInput(movingYaw)
        }

    }

    private fun getRotationForStraightInput(movingYaw: Float) = Rotation(movingYaw, 80f)

    private fun getRotationForDiagonalInput(movingYaw: Float) = Rotation(movingYaw, 75.6f)

    private fun getRotationForNoInput(target: BlockPlacementTarget): Rotation {
        val axisMovement = floor(target.rotation.yaw / 90) * 90

        val yaw = axisMovement + 45
        val pitch = 75f

        return Rotation(yaw, pitch)
    }

}
