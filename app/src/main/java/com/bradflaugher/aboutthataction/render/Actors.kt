package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Body
import com.bradflaugher.aboutthataction.engine.Enemy
import com.bradflaugher.aboutthataction.engine.EnemyKind
import com.bradflaugher.aboutthataction.engine.EnemyState
import com.bradflaugher.aboutthataction.engine.FloorState
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.PickupKind
import com.bradflaugher.aboutthataction.engine.PlayerState
import com.bradflaugher.aboutthataction.engine.World
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Everyone who moves: the agent, the guards, the drones, the demons, the loot.
 *
 * Every humanoid is a [Rig] pose painted through [ActorPaint]: one ink outline
 * weight, a ceiling key light and a neon rim from behind. The agent is the
 * brightest thing on screen: slate sneaking suit, cyan visor, a long red scarf.
 */
internal class Actors(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly
    private val p = ActorPaint(f)
    private val k = Rig()
    private val body = ActorBody(p, k)
    private val cast = ActorCast(f, p, k, body)
    private val look = Look()

    // Renderer-side animation memory (visual only; the simulation never reads it).
    private var wasAir = false
    private var landT = 9f
    private var landHard = 0f
    private var watchT = 0f

    companion object {
        const val SUIT = 0xFF2A3454.toInt()
        const val SUIT_LIT = 0xFF53669C.toInt()
        const val SUIT_DARK = 0xFF1A2038.toInt()
        const val ARMOR = 0xFF0F121C.toInt()
        const val VISOR = 0xFF3CF4FF.toInt()
        const val RIM = 0xFF7CF4FF.toInt()
        const val SCARF = 0xFFFF2E3E.toInt()
        const val SCARF_LIT = 0xFFFFB0A0.toInt()
        const val SCARF_DARK = 0xFFB0102A.toInt()
        /** The agent reads a touch larger than the guards: the hero scale. */
        const val HS = 1.06f
    }

    // ================================================================= player

    fun player(force: Boolean = false) {
        val pl = f.w.player
        if (pl.state == PlayerState.ELEVATOR && !force) return
        if (pl.state == PlayerState.INTEL) return
        val gy = Geo.groundY(pl.floorF)
        if (f.w.floors[0] != null) helicopter()
        if (pl.state == PlayerState.INTRO && f.w.difficulty.startFloor > 0) hatch(pl.x, pl.floorF)
        val foot = gy - pl.z
        val dir = if (pl.facing >= 0) 1 else -1

        // Landing memory for the squash.
        val air = pl.state == PlayerState.INTRO || (pl.state == PlayerState.NORMAL && pl.z > 0.001f)
        if (f.dt > 0f) {
            if (wasAir && !air && pl.state == PlayerState.NORMAL) {
                landT = 0f
                landHard = if (pl.stateTime < 0.05f) 1f else 0.6f
            } else {
                landT += f.dt
            }
            wasAir = air
        }

        p.reset()
        if (pl.invuln > 0f && pl.state != PlayerState.DEAD && !f.inPerk) {
            if (pl.invuln > 1.12f) {
                p.flat = 0xFFFF3A48.toInt()
                p.flatAmt = ((pl.invuln - 1.12f) / 0.18f).coerceIn(0f, 0.85f)
            } else if ((f.t * 15f).toInt() % 2 == 0) {
                p.flat = 0xFFD8FBFF.toInt()
                p.flatAmt = 0.55f
                p.alphaMul = 0.8f
            }
        }

        if (pl.state != PlayerState.DOOR && pl.state != PlayerState.ELEVATOR) {
            val hw = if (pl.state == PlayerState.BOX) 0.62f else 0.4f
            p.contactShadow(pl.x, gy, hw, pl.z)
        }
        // Backlight halo: the player is always the easiest thing to find.
        if (pl.state != PlayerState.DOOR && pl.state != PlayerState.DEAD) {
            val cy = if (pl.state == PlayerState.BOX) foot - 0.4f else foot - 0.8f
            val a = p.alphaMul
            g.blend(Gfx.Blend.ADD)
            g.glow(pl.x, cy, 1.4f, Col.alpha(VISOR, 0.26f * a))
            // Cyan pool on the floor under the agent.
            val fz = (1f - pl.z / 2.5f).coerceIn(0f, 1f)
            if (fz > 0f) {
                g.save()
                g.translate(pl.x, gy - 0.02f)
                g.scale(1f, 0.2f)
                g.glow(0f, 0f, 0.95f, Col.alpha(VISOR, 0.6f * a * fz))
                g.restore()
            }
            g.blend(Gfx.Blend.NORMAL)
        }

        when (pl.state) {
            PlayerState.BOX -> box(pl.x, gy, dir, pl.vx, pl.stateTime)
            PlayerState.DOOR -> doorHide(pl.x, foot, dir)
            PlayerState.DEAD -> deadHero(pl.x, gy, dir)
            else -> {
                // Slow-mo afterimages: flat, unoutlined echoes.
                if (f.w.slowMo && abs(pl.vx) > 0.5f) {
                    val keepA = p.alphaMul
                    val keepF = p.flatAmt
                    val keepC = p.flat
                    for (i in 3 downTo 1) {
                        p.noInk = true
                        p.alphaMul = keepA * (0.3f - i * 0.07f)
                        p.flat = if (i % 2 == 0) VISOR else 0xFFFF3D9A.toInt()
                        p.flatAmt = 1f
                        poseHero(pl.x - pl.vx * 0.045f * i, foot, dir)
                        drawHero(ghost = true)
                    }
                    p.noInk = false
                    p.alphaMul = keepA
                    p.flatAmt = keepF
                    p.flat = keepC
                }
                poseHero(pl.x, foot, dir)
                val flip = flipAngle()
                if (flip != 0f) {
                    g.save()
                    val cx = pl.x
                    val cy = foot - 0.75f
                    g.translate(cx, cy)
                    g.rotate(flip * dir)
                    g.translate(-cx, -cy)
                }
                if (pl.state == PlayerState.TAKEDOWN) {
                    drawHero(ghost = false, skipFrontArm = true)
                    val victim = takedownVictim()
                    if (victim != null) {
                        val keepA = p.alphaMul
                        val keepF = p.flatAmt
                        val keepC = p.flat
                        cast.chokedVictim(victim, gy, heroNeckX, heroNeckY, pl.stateTime)
                        p.alphaMul = keepA; p.flatAmt = keepF; p.flat = keepC
                        poseHero(pl.x, foot, dir)
                    }
                    p.twoPass { body.arm(k.armF, look, far = false) }
                } else {
                    drawHero(ghost = false)
                }
                if (flip != 0f) g.restore()
            }
        }
        p.reset()
    }

    private var heroNeckX = 0f
    private var heroNeckY = 0f

    private fun takedownVictim(): Enemy? {
        val id = f.w.player.takedownTarget
        val list = f.w.enemies
        for (i in list.indices) {
            val e = list[i]
            if (e.id == id && e.state == EnemyState.CHOKED) return e
        }
        return null
    }

    /** Double-jump front flip, derived from the jump's age. */
    private fun flipAngle(): Float {
        val pl = f.w.player
        if (pl.state != PlayerState.NORMAL || pl.jumpsUsed < 2 || pl.z <= 0.01f) return 0f
        val age = (World.JUMP_V - pl.vz) / World.GRAVITY
        if (age < 0f || age > 0.42f) return 0f
        return Rig.smooth(age / 0.42f) * 360f
    }

    // --------------------------------------------------------------- posing

    /** Gun state chosen by [poseHero]. */
    private var gunKind = 0
    private var gunUp = 0f
    private var gunX = 0f
    private var gunY = 0f
    private var showGun = true
    private var flash = false
    private var recoil = 0f
    private var magInHand = false

    private fun weaponKind(): Int = when (f.w.player.weapon) {
        PickupKind.SHOTGUN -> 1
        PickupKind.MINIGUN -> 2
        else -> 0
    }

    /** What the auto-aim would pick, to aim the gun at it (mirrors World.pickTarget). */
    private fun aimTarget(): Enemy? {
        val pl = f.w.player
        var best: Enemy? = null
        var bestScore = Float.MAX_VALUE
        val list = f.w.enemies
        for (i in list.indices) {
            val e = list[i]
            if (e.floor != pl.floor || !e.alive) continue
            val dx = e.x - pl.x
            if (abs(dx) > 11f) continue
            val score = abs(dx) + if (dx * pl.facing < -0.2f) 3f else 0f
            if (score < bestScore) {
                bestScore = score
                best = e
            }
        }
        return best
    }

    private fun poseHero(x: Float, foot: Float, dir: Int) {
        val pl = f.w.player
        val state = pl.state
        k.setup(dir, foot - 0.045f * HS, HS)
        val speed = abs(pl.vx)
        val airborne = state == PlayerState.INTRO || (state == PlayerState.NORMAL && pl.z > 0.001f)
        val breathe = sin(f.t * 2.4f)
        gunKind = weaponKind()
        showGun = true
        flash = false
        recoil = 0f
        magInHand = false
        val shooting = pl.sinceShot < 0.42f && !pl.reloading &&
            (state == PlayerState.NORMAL || state == PlayerState.ELEVATOR)
        var lowAim = false
        var aimUp = 0f
        // Easing the gun back down after the last shot instead of snapping to low ready.
        val lower = if (shooting) Rig.smooth((pl.sinceShot - 0.3f) / 0.12f) else 0f
        if (shooting) {
            val t = aimTarget()
            if (t != null && (t.x - x) * dir > 0f) {
                val dx = abs(t.x - x).coerceAtLeast(0.5f)
                when {
                    t.kind == EnemyKind.TURRET -> aimUp = atan2(t.targetZ - (pl.z + 1.0f), dx).coerceIn(-0.2f, 1.1f)
                    t.kind == EnemyKind.DRONE -> aimUp = atan2(t.targetZ - (pl.z + 1.0f), dx).coerceIn(-0.3f, 0.6f)
                    t.ducking && !airborne -> lowAim = true
                }
            }
        }
        var lean: Float
        var nod = 0f
        when {
            state == PlayerState.STAIRS -> {
                val u = pl.stateTime * 2.4f
                k.locomote(x, u, 0.35f)
                lean = 0.12f
                k.spine(lean, 0.12f)
                k.swingArms(u, 0.35f, 0.7f)
            }
            state == PlayerState.INTRO && f.w.difficulty.startFloor == 0 -> {
                // Rappelling: both hands up the rope, legs together.
                val h = foot - 0.045f
                k.hip(x, h - 0.7f * HS)
                k.ik(k.legF, x + 0.08f * dir, h - 0.02f, true)
                k.ik(k.legB, x - 0.02f * dir, h + 0.0f, true)
                k.legF.pitch = 0.5f; k.legB.pitch = 0.6f
                lean = -0.08f
                k.spine(lean, -0.15f)
                k.ik(k.armF, x + 0.04f * dir, foot - 1.9f, false)
                k.ik(k.armB, x + 0.02f * dir, foot - 1.62f, false)
                showGun = false
            }
            airborne -> {
                airPose(x, foot, pl.vz)
                lean = k.lean
            }
            pl.invuln > 1.0f && state == PlayerState.NORMAL -> {
                // Knocked back by the hit: chest caved, head snapped, arms thrown.
                val q = Rig.easeOut((pl.invuln - 1.0f) / 0.3f)
                val back = if (pl.vx * dir <= 0f) 1f else -1f
                k.stand(x, 0.06f + 0.06f * q, 0.16f, -0.2f)
                k.ik(k.legF, x + (0.18f + 0.08f * q) * dir, k.ground - 0.12f * q, true)
                k.legF.pitch = -0.3f * q
                lean = -0.45f * q * back
                k.spine(lean, -0.5f * q * back)
                k.armFK(k.armF, 1.2f + 0.8f * q, 0.4f)
                k.armFK(k.armB, -1.4f * q - 0.2f, 0.5f)
            }
            state == PlayerState.TAKEDOWN -> {
                val q = (pl.stateTime / World.TAKEDOWN_TIME).coerceIn(0f, 1f)
                val grab = Rig.easeOut(min(1f, pl.stateTime / 0.08f))
                k.stand(x, 0.1f + 0.04f * grab, 0.24f, -0.26f)
                lean = -0.14f - 0.14f * q
                k.spine(lean, 0.15f)
                heroNeckX = k.neckX
                heroNeckY = k.neckY
                // Forearm across the victim's throat, other hand locking the back of the head.
                val vx = x + 0.1f * dir
                val vy = foot - 1.1f - 0.07f * q
                k.ik(k.armF, vx + 0.16f * dir, vy, false)
                k.ik(k.armB, vx - 0.02f * dir, vy - 0.2f, false)
                showGun = false
            }
            speed > 0.6f && state == PlayerState.NORMAL -> {
                val runBlend = ((speed - 1f) / 2.8f).coerceIn(0f, 1f)
                val u = pl.runTime * 2.05f
                k.locomote(x, u, runBlend)
                lean = 0.1f + 0.22f * runBlend
                k.spine(lean, -0.05f)
                k.swingArms(u, runBlend)
            }
            else -> {
                // Idle: weight settled, slow breath; landing squash on top.
                val land = if (landT < 0.22f) (1f - landT / 0.22f) else 0f
                val squash = land * land * (0.1f + 0.08f * landHard)
                val wide = if (shooting && !lowAim) 1f - lower else 0f
                k.stand(x, 0.035f + breathe * 0.006f + squash, Rig.mix(0.1f, 0.17f, wide), Rig.mix(-0.13f, -0.2f, wide))
                lean = 0.05f + breathe * 0.01f + squash * 1.2f
                k.spine(lean, 0f)
                k.armFK(k.armB, -0.12f - breathe * 0.02f, 0.3f)
                k.armFK(k.armF, 0.25f, 0.5f)
            }
        }
        heroNeckX = k.neckX
        heroNeckY = k.neckY
        if (state == PlayerState.TAKEDOWN || state == PlayerState.INTRO && f.w.difficulty.startFloor == 0) return

        val headX = k.headX
        when {
            pl.reloading && state != PlayerState.STAIRS -> {
                val t = 1f - pl.reloadTime / max(0.01f, pl.reloadTotal)
                gunX = k.neckX + 0.24f * dir
                gunY = k.neckY + 0.32f
                gunUp = 0.9f - 0.3f * Rig.smooth((t - 0.7f) / 0.3f)
                k.ik(k.armF, gunX, gunY, false)
                val bx: Float
                val by: Float
                when {
                    t < 0.3f -> {
                        val u = Rig.smooth(t / 0.3f)
                        bx = Rig.mix(gunX, k.hipX - 0.02f * dir, u); by = Rig.mix(gunY + 0.1f, k.hipY - 0.02f, u)
                    }
                    t < 0.65f -> {
                        val u = Rig.smooth((t - 0.3f) / 0.35f)
                        bx = Rig.mix(k.hipX - 0.02f * dir, gunX - 0.02f * dir, u); by = Rig.mix(k.hipY - 0.02f, gunY + 0.12f, u)
                        magInHand = true
                    }
                    else -> {
                        val u = Rig.smooth((t - 0.65f) / 0.35f)
                        bx = gunX + (0.02f - 0.08f * u) * dir; by = gunY - 0.12f + 0.04f * u
                    }
                }
                k.ik(k.armB, bx, by, false)
                nod = 0.2f
            }
            shooting -> {
                val since = pl.sinceShot
                recoil = if (since < 0.11f) 1f - since / 0.11f else 0f
                flash = since < 0.055f
                val barrelZ = if (lowAim) 0.45f else 1.0f
                if (lowAim) kneel(x, foot)
                val reach = when (gunKind) { 1 -> 0.36f; 2 -> 0.3f; else -> 0.44f }
                gunX = k.neckX + (reach - recoil * 0.07f) * dir
                gunY = foot - barrelZ + 0.055f + if (lowAim) 0f else -pl.z * 0f
                if (!lowAim && airborne) gunY = k.neckY + 0.22f
                gunUp = aimUp + recoil * (if (gunKind == 1) 0.5f else if (gunKind == 2) 0.1f else 0.32f)
                k.ik(k.armF, gunX, gunY, false)
                // Support hand.
                when (gunKind) {
                    1 -> k.ik(k.armB, gunX + 0.2f * dir * cos(gunUp), gunY - 0.2f * sin(gunUp) + 0.01f, false)
                    2 -> k.ik(k.armB, gunX + 0.02f * dir, gunY - 0.02f, false)
                    else -> k.ik(k.armB, gunX + 0.015f * dir, gunY + 0.035f, false)
                }
                if (!lowAim) k.spine(k.lean - recoil * 0.06f, 0.06f)
                if (lower > 0f && !airborne) {
                    val sbx = k.armB.ex
                    val sby = k.armB.ey
                    gunX = Rig.mix(gunX, k.hipX + 0.2f * dir, lower)
                    gunY = Rig.mix(gunY, k.hipY + 0.02f, lower)
                    gunUp = Rig.mix(gunUp, -0.85f, lower)
                    k.ik(k.armB, Rig.mix(sbx, k.armB.ax + 0.015f * dir, lower), Rig.mix(sby, k.armB.ay + 0.5f * HS, lower), false)
                }
                k.ik(k.armF, gunX, gunY, false)
            }
            airborne || state == PlayerState.STAIRS -> {
                gunX = k.armF.ex; gunY = k.armF.ey
                gunUp = if (airborne) 0.3f else -0.5f
            }
            speed > 0.6f || pl.invuln > 1.0f -> {
                gunX = k.armF.ex; gunY = k.armF.ey
                val fa = atan2(-(k.armF.ey - k.armF.jy), (k.armF.ex - k.armF.jx) * dir)
                gunUp = (fa + 0.35f).coerceIn(-1.0f, 0.2f)
            }
            else -> {
                // Low ready: muzzle down and forward.
                gunX = k.hipX + 0.2f * dir
                gunY = k.hipY + 0.02f + breathe * 0.004f
                gunUp = -0.85f
                k.ik(k.armF, gunX, gunY, false)
            }
        }
        // Final head nod; keep both hands where the pose put them.
        val fx = k.armF.ex
        val fy = k.armF.ey
        val bx = k.armB.ex
        val by = k.armB.ey
        k.spine(k.lean, nod + if (shooting && lower < 1f) 0.06f else 0f)
        if (headX != k.headX || nod != 0f || shooting) {
            k.ik(k.armF, fx, fy, false)
            k.ik(k.armB, bx, by, false)
        }
    }

    /** Low kneel for shooting at a ducking guard (barrel at the low lane). */
    private fun kneel(x: Float, foot: Float) {
        val d = k.dir
        val gnd = foot - 0.045f
        k.hip(x, gnd - 0.4f * HS)
        k.ik(k.legF, x + 0.24f * d, gnd, true)
        k.legB.jx = x - 0.08f * d; k.legB.jy = gnd - 0.02f
        k.legB.ex = x - 0.43f * d; k.legB.ey = gnd - 0.09f
        k.legB.pitch = 1.3f
        k.legF.pitch = 0f
        k.spine(0.3f, 0.1f)
    }

    private fun airPose(x: Float, foot: Float, vz: Float) {
        val s = (vz / 8f).coerceIn(-1f, 1f)
        val h = foot - 0.045f * HS
        k.hip(x, h - 0.71f * HS)
        // Keys: rising, apex (tuck), falling (reaching for the floor).
        val tF: Float; val kF: Float; val tB: Float; val kB: Float
        val aF: Float; val eF: Float; val aB: Float; val eB: Float; val ln: Float
        if (s >= 0f) {
            tF = Rig.mix(1.35f, 1.2f, s); kF = Rig.mix(2.2f, 1.9f, s)
            tB = Rig.mix(0.55f, -0.35f, s); kB = Rig.mix(1.9f, 1.0f, s)
            aF = Rig.mix(1.2f, 1.5f, s); eF = Rig.mix(0.9f, 0.3f, s)
            aB = Rig.mix(-1.2f, -2.0f, s); eB = Rig.mix(0.9f, 0.4f, s)
            ln = Rig.mix(0.4f, 0.12f, s)
        } else {
            val u = -s
            tF = Rig.mix(1.35f, 0.5f, u); kF = Rig.mix(2.2f, 0.45f, u)
            tB = Rig.mix(0.55f, -0.05f, u); kB = Rig.mix(1.9f, 0.75f, u)
            aF = Rig.mix(1.2f, 1.9f, u); eF = Rig.mix(0.9f, 0.3f, u)
            aB = Rig.mix(-1.2f, -2.2f, u); eB = Rig.mix(0.9f, 0.25f, u)
            ln = Rig.mix(0.4f, -0.02f, u)
        }
        k.legFK(k.legF, tF, kF)
        k.legFK(k.legB, tB, kB)
        k.legF.pitch = 0.5f
        k.legB.pitch = 0.7f
        k.spine(ln, -0.1f)
        k.armFK(k.armF, aF, eF)
        k.armFK(k.armB, aB, eB)
    }

    // -------------------------------------------------------------- drawing

    private fun heroLook(ghost: Boolean) {
        look.torso = SUIT
        look.torsoLit = SUIT_LIT
        look.legs = SUIT
        look.legsFar = SUIT_DARK
        look.arms = SUIT
        look.armsFar = SUIT_DARK
        look.boots = ARMOR
        look.gloves = ARMOR
        look.skin = SUIT
        look.rim = if (ghost) 0 else RIM
        look.legW = 1f
        look.armW = 1f
    }

    private fun drawHero(ghost: Boolean, skipFrontArm: Boolean = false) {
        heroLook(ghost)
        val pl = f.w.player
        val dir = k.dir
        if (!ghost) scarfTail(dir, pl.vx, pl.vz, back = true)
        p.twoPass {
            body.arm(k.armB, look, far = true)
            if (magInHand && !p.ink) g.fillRect(k.armB.ex - 0.025f, k.armB.ey - 0.09f, k.armB.ex + 0.025f, k.armB.ey, p.c(0xFF2A2E3A.toInt()))
            body.leg(k.legB, look, far = true)
        }
        if (!ghost) scarfTail(dir, pl.vx, pl.vz, back = false)
        p.twoPass {
            body.leg(k.legF, look, far = false)
            body.torso(look)
            heroDetails(dir)
            body.neck(SUIT_DARK)
            heroHead(dir, ghost)
            scarfKnot(dir)
        }
        if (skipFrontArm) return
        p.twoPass {
            if (showGun) {
                body.gun(gunKind, gunX, gunY, gunUp, VISOR, spin = f.t * 60f)
            }
            body.arm(k.armF, look, far = false, hand = true)
            shoulderPad(dir)
        }
        if (flash && !ghost) body.muzzleFlash(gunUp, if (gunKind == 1) 0.2f else if (gunKind == 2) 0.17f else 0.15f, (f.t * 30f).toInt())
    }

    private fun shoulderPad(dir: Int) {
        val sx = k.armF.ax
        val sy = k.armF.ay
        val ax = k.armF.jx - sx
        val ay = k.armF.jy - sy
        p.begin()
            .add(sx - 0.07f * dir, sy - 0.05f).add(sx + 0.06f * dir, sy - 0.07f)
            .add(sx + ax * 0.36f + 0.045f * dir, sy + ay * 0.36f)
            .add(sx + ax * 0.36f - 0.05f * dir, sy + ay * 0.36f + 0.01f)
            .shape(ARMOR, sep = true)
        if (!p.ink) p.detail(sx - 0.05f * dir, sy - 0.045f, sx + 0.05f * dir, sy - 0.06f, 0.018f, SUIT_LIT)
    }

    private fun heroDetails(dir: Int) {
        if (p.ink) return
        val hs = k.hs
        // Belt with a cyan buckle light, harness strap, thigh holster.
        val bx1 = body.ptX(0.05f, -k.waistD * 0.5f); val by1 = body.ptY(0.05f, -k.waistD * 0.5f)
        val bx2 = body.ptX(0.05f, k.waistD * 0.5f); val by2 = body.ptY(0.05f, k.waistD * 0.5f)
        p.detail(bx1, by1, bx2, by2, 0.05f * hs, ARMOR)
        p.dot(body.ptX(0.05f, k.waistD * 0.42f), body.ptY(0.05f, k.waistD * 0.42f), 0.018f, VISOR)
        p.detail(body.ptX(0.95f, k.chestD * 0.2f), body.ptY(0.95f, k.chestD * 0.2f), body.ptX(0.12f, -k.waistD * 0.35f), body.ptY(0.12f, -k.waistD * 0.35f), 0.032f, 0xFF10131E.toInt())
        // Chest plate seam.
        p.detail(body.ptX(0.72f, k.chestD * 0.5f), body.ptY(0.72f, k.chestD * 0.5f), body.ptX(0.55f, -k.chestD * 0.05f), body.ptY(0.55f, -k.chestD * 0.05f), 0.016f, SUIT_DARK)
        // Emissive cyan light strips down the near shin and forearm: the agent glows.
        val lf = k.legF
        val af = k.armF
        g.blend(Gfx.Blend.ADD)
        val strip = p.c(Col.alpha(VISOR, 0.9f))
        g.line(Rig.mix(lf.jx, lf.ex, 0.2f) + 0.02f * dir, Rig.mix(lf.jy, lf.ey, 0.2f), Rig.mix(lf.jx, lf.ex, 0.7f) + 0.02f * dir, Rig.mix(lf.jy, lf.ey, 0.7f), 0.02f, strip)
        g.line(Rig.mix(af.jx, af.ex, 0.25f), Rig.mix(af.jy, af.ey, 0.25f) - 0.01f, Rig.mix(af.jx, af.ex, 0.7f), Rig.mix(af.jy, af.ey, 0.7f) - 0.01f, 0.018f, strip)
        g.blend(Gfx.Blend.NORMAL)
        // Knee pad on the near leg.
        p.dot(k.legF.jx + 0.01f * dir, k.legF.jy, 0.05f, ARMOR)
        p.dot(k.legF.jx + 0.018f * dir, k.legF.jy - 0.015f, 0.018f, SUIT_LIT)
    }

    private fun heroHead(dir: Int, ghost: Boolean) {
        val hx = k.headX
        val hy = k.headY
        val r = k.headR
        // Hood: skull, a swept cowl point at the back, a jaw guard.
        // Swept antenna fin: the silhouette's signature spike, lit at the tip.
        p.begin()
            .add(hx - r * 0.25f * dir, hy - r * 0.98f)
            .add(hx - r * 1.85f * dir, hy - r * 1.45f)
            .add(hx - r * 0.95f * dir, hy - r * 0.3f)
            .shape(SUIT_DARK)
        if (!p.ink && !ghost) p.dot(hx - r * 1.7f * dir, hy - r * 1.38f, 0.02f, VISOR)
        p.disc(hx, hy, r, SUIT)
        p.disc(hx + r * 0.35f * dir, hy + r * 0.45f, r * 0.62f, SUIT)
        if (p.ink) return
        // Key light on the crown.
        p.detail(hx - r * 0.5f * dir, hy - r * 0.72f, hx + r * 0.25f * dir, hy - r * 0.85f, 0.03f, SUIT_LIT)
        // Rim on the back of the hood.
        if (!ghost) p.detail(hx - r * 0.92f * dir, hy - r * 0.2f, hx - r * 0.55f * dir, hy - r * 0.78f, ActorPaint.RIM_W, RIM)
        // Visor: a band wrapping the front of the head.
        p.begin()
            .add(hx - r * 0.15f * dir, hy - r * 0.34f)
            .add(hx + r * 1.08f * dir, hy - r * 0.3f)
            .add(hx + r * 1.12f * dir, hy + r * 0.22f)
            .add(hx - r * 0.05f * dir, hy + r * 0.26f)
            .shapeDetail(VISOR)
        p.detail(hx + r * 0.2f * dir, hy - r * 0.18f, hx + r * 0.85f * dir, hy - r * 0.16f, 0.018f, 0xFFFFFFFF.toInt())
        if (!ghost) {
            val a = p.alphaMul * (1f - p.flatAmt) * (if (f.w.player.state == PlayerState.DEAD) 0.3f else 1f)
            g.blend(Gfx.Blend.ADD)
            g.glow(hx + r * 0.7f * dir, hy - r * 0.02f, r * 1.9f, Col.alpha(VISOR, 0.6f * a))
            g.glow(hx - r * 1.7f * dir, hy - r * 1.38f, r * 0.75f, Col.alpha(VISOR, 0.85f * a))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    private fun scarfKnot(dir: Int) {
        val nx = k.neckX
        val ny = k.neckY
        p.seg(nx - 0.08f * dir, ny + 0.0f, nx + 0.07f * dir, ny + 0.02f, 0.1f, SCARF)
        if (!p.ink) p.detail(nx - 0.06f * dir, ny - 0.025f, nx + 0.05f * dir, ny - 0.01f, 0.025f, SCARF_LIT)
    }

    private val scarfX = FloatArray(9)
    private val scarfY = FloatArray(9)

    /** The long red scarf: procedural ribbon trailing from the back of the neck. */
    private fun scarfTail(dir: Int, vx: Float, vz: Float, back: Boolean) {
        val pl = f.w.player
        val n = 7
        val air = pl.state == PlayerState.NORMAL && pl.z > 0.01f
        val speed = min(1f, abs(vx) / 4f + (if (air) 0.35f else 0f))
        val rise = if (pl.state == PlayerState.INTRO) 0.9f else (-vz * 0.07f).coerceIn(-0.35f, 0.9f)
        // Angle from straight down, positive = trailing behind.
        val baseA = Rig.mix(0.2f, 1.0f, speed) + rise
        val amp = Rig.mix(0.07f, 0.3f, speed)
        val freq = Rig.mix(3.0f, 12f, speed)
        val seg = if (back) 0.1f else 0.12f
        val phase = if (back) 2.1f else 0f
        val floor = k.ground + 0.02f
        var x = k.neckX - 0.07f * dir
        var y = k.neckY + 0.03f
        scarfX[0] = x; scarfY[0] = y
        for (i in 1..n) {
            val t = i / n.toFloat()
            val a = baseA + t * 0.35f + sin(f.t * freq - i * 0.75f + phase) * amp * (0.25f + t * 1.1f)
            x += sin(a) * seg * -dir
            y += cos(a) * seg
            if (y > floor) y = floor
            scarfX[i] = x; scarfY[i] = y
        }
        val len = if (back) n - 3 else n - 1
        val base = if (back) SCARF_DARK else SCARF
        p.twoPass {
            for (i in 0 until len) {
                val w = (0.1f - i * 0.01f) * (if (back) 0.85f else 1f)
                p.seg(scarfX[i], scarfY[i], scarfX[i + 1], scarfY[i + 1], w, base)
            }
            // Split end.
            val ex = scarfX[len]
            val ey = scarfY[len]
            p.seg(ex, ey, ex - dir * 0.1f, ey + 0.05f + sin(f.t * 13f) * 0.02f, 0.035f, base)
        }
        if (!back) {
            for (i in 0 until len - 1) {
                p.detail(scarfX[i], scarfY[i] - 0.025f, scarfX[i + 1], scarfY[i + 1] - 0.025f, 0.02f, SCARF_LIT)
            }
            // A faint bloom so the scarf carries as a colour accent at phone scale.
            if (!p.noInk) {
                g.blend(Gfx.Blend.ADD)
                val bc = p.c(Col.alpha(SCARF, 0.2f))
                for (i in 0 until len step 2) g.line(scarfX[i], scarfY[i], scarfX[min(len, i + 2)], scarfY[min(len, i + 2)], 0.2f, bc)
                g.blend(Gfx.Blend.NORMAL)
            }
        }
    }

    // ------------------------------------------------------------ hide, die

    private fun doorHide(x: Float, foot: Float, dir: Int) {
        // Flattened into the doorway: a near-black silhouette, visor glint and a whisper of rim.
        k.setup(dir, foot - 0.045f * HS, HS)
        val breathe = sin(f.t * 2f) * 0.005f
        k.stand(x, 0.02f + breathe, 0.06f, -0.06f)
        k.spine(-0.04f, 0.05f)
        k.armFK(k.armF, -0.1f, 0.15f)
        k.armFK(k.armB, -0.2f, 0.1f)
        heroLook(ghost = true)
        p.flat = 0xFF05060A.toInt()
        p.flatAmt = 0.93f
        p.noInk = true
        p.twoPass {
            body.arm(k.armB, look, true)
            body.leg(k.legB, look, true)
            body.leg(k.legF, look, false)
            body.torso(look)
            body.neck(SUIT)
            p.disc(k.headX, k.headY, k.headR, SUIT)
            body.arm(k.armF, look, false)
        }
        p.flatAmt = 0f
        p.noInk = false
        // Visor slit glinting out of the dark, blinking now and then.
        val blink = fract(f.t * 0.31f) > 0.96f
        val hx = k.headX
        val hy = k.headY
        if (!blink) {
            g.fillRoundRect(hx - 0.02f, hy - 0.02f, hx + 0.12f, hy + 0.015f, 0.015f, Col.alpha(VISOR, 0.85f))
            f.glowDot(hx + 0.07f * dir, hy - 0.003f, 0.025f, VISOR, 0.6f)
        }
        g.line(k.hipX - 0.14f * dir, k.hipY - 0.2f, k.neckX - 0.14f * dir, k.neckY + 0.05f, 0.02f, Col.alpha(RIM, 0.25f))
    }

    private fun deadHero(x: Float, gy: Float, dir: Int) {
        val pl = f.w.player
        val t = pl.stateTime
        val fall = if (abs(pl.vx) > 0.2f) (if (pl.vx > 0f) 1 else -1) else -dir
        p.flat = 0xFFFF2030.toInt()
        p.flatAmt = max(0f, 0.5f - t * 0.8f)
        heroLook(ghost = false)
        cast.ragdoll(x, gy, pl.z, dir, HS, fall, t, 0.55f, CastDeath.KNOCK, look, heroRagdollHead)
        p.flatAmt = 0f
    }

    private val heroRagdollHead: (Int) -> Unit = { d -> heroHeadAt(d) }

    private fun heroHeadAt(dir: Int) {
        heroHead(dir, false)
    }

    // --------------------------------------------------------------- box

    /** The cardboard box: printed markings, a peek slit with eyes, a waddle, the "!" moment. */
    private fun box(x: Float, gy: Float, dir: Int, vx: Float, t: Float) {
        val pl = f.w.player
        val moving = abs(vx) > 0.2f
        // Being watched?
        var watched = false
        val list = f.w.enemies
        for (i in list.indices) {
            val e = list[i]
            if (e.floor != pl.floor || !e.alive) continue
            val dx = pl.x - e.x
            if (abs(dx) < 4f && (dx > 0) == (e.facing > 0) && e.state != EnemyState.PATROL) {
                watched = true
                break
            }
        }
        if (watched) watchT += if (f.dt > 0f) f.dt else 0.4f else watchT = 0f

        val ph = f.t * 13f
        val bob = if (moving) abs(sin(ph)) * 0.045f else 0f
        val tilt = if (moving) sin(ph) * 3.2f else 0f
        val jolt = if (watched) Rig.backOut(min(1f, watchT / 0.16f)) else 0f
        val hop = if (watched && watchT < 0.3f) sin(watchT / 0.3f * PI.toFloat()) * 0.14f else 0f
        val shake = if (watched) sin(f.t * 70f) * 0.012f else 0f
        // Idle peek: every few seconds the box lifts and eyes glint underneath.
        val cyc = fract(f.t * 0.19f + 0.3f)
        val peek = if (!moving && !watched && cyc > 0.78f && cyc < 0.94f) sin((cyc - 0.78f) / 0.16f * PI.toFloat()) * 0.13f else 0f
        val h = Body.BOX_HEIGHT
        val w = 0.96f
        val hw = w / 2f
        val sqY = if (moving) 1f - abs(cos(ph)) * 0.03f else 1f

        g.save()
        g.translate(x + shake, gy - bob - hop - peek)
        g.rotate(tilt)
        g.scale(1f, sqY)
        if (moving || peek > 0.02f) {
            // Boots shuffling under the box.
            val s = if (moving) sin(ph) * 0.13f else 0f
            p.twoPass {
                p.seg(-0.2f + s, -0.02f + bob + peek, -0.08f + s, -0.02f + bob + peek, 0.09f, ARMOR)
                p.seg(0.12f - s, -0.02f + bob + peek, 0.24f - s, -0.02f + bob + peek, 0.09f, ARMOR)
            }
            if (peek > 0.02f) {
                g.fillRect(-hw + 0.04f, -0.02f, hw - 0.04f, peek * 0.8f, p.c(0xE0050308.toInt()))
                val ex = 0.18f * dir
                f.glowDot(ex - 0.05f, peek * 0.35f, 0.022f, VISOR, 0.9f * p.alphaMul)
                f.glowDot(ex + 0.05f, peek * 0.35f, 0.022f, VISOR, 0.9f * p.alphaMul)
            }
        }
        val cb = 0xFFC08A52.toInt()
        val cbDark = 0xFF8E6036.toInt()
        val cbLit = 0xFFDDA86C.toInt()
        val print = 0xFF5A3A1E.toInt()
        val d = 0.09f // depth offset of the receding top/side
        p.twoPass {
            // Side face (receding, away from the facing side), top face, flaps, front face.
            val sx = -dir.toFloat()
            p.begin().add(hw * sx, -h).add(hw * sx + d * 0.6f * sx, -h - d).add(hw * sx + d * 0.6f * sx, -d * 0.6f).add(hw * sx, 0f).shape(cbDark)
            p.begin().add(-hw, -h).add(hw, -h).add(hw + d * 0.6f * sx, -h - d).add(-hw + d * 0.6f * sx, -h - d).shape(cbLit)
            val fl = 0.06f * sin(f.t * 1.3f)
            p.begin().add(-hw, -h).add(-hw + 0.34f, -h).add(-hw + 0.24f, -h - 0.13f - fl).add(-hw - 0.08f, -h - 0.1f).shape(0xFFCC9660.toInt())
            p.begin().add(hw, -h).add(hw - 0.34f, -h).add(hw - 0.22f, -h - 0.12f + fl).add(hw + 0.08f, -h - 0.09f).shape(0xFFB07A46.toInt())
            p.begin().add(-hw, -h).add(hw, -h).add(hw, 0f).add(-hw, 0f).shape(cb)
        }
        // Shading and print.
        g.fillRect(-hw, -0.12f, hw, 0f, p.c(0x40000000))
        g.fillRect(-hw, -h, hw, -h + 0.05f, p.c(0x30FFFFFF))
        for (i in 1..4) g.line(-hw + 0.02f, -h + i * 0.16f, hw - 0.02f, -h + i * 0.16f, 0.006f, p.c(0x16000000))
        g.fillRect(-0.055f, -h, 0.055f, 0f, p.c(0xFFD9B77C.toInt()))
        g.line(-0.055f, -h, -0.055f, 0f, 0.008f, p.c(0x30000000))
        // "This side up" arrows and a fragile glass on the trailing half.
        val px = -0.27f * dir
        Glyphs.arrow(g, px - 0.06f, -0.3f, 0.09f, 0f, -1f, 0.028f, p.c(print))
        Glyphs.arrow(g, px + 0.06f, -0.3f, 0.09f, 0f, -1f, 0.028f, p.c(print))
        g.line(px - 0.12f, -0.18f, px + 0.12f, -0.18f, 0.02f, p.c(print))
        val gx = -0.27f * dir
        g.line(gx - 0.05f, -0.62f, gx + 0.05f, -0.62f, 0.022f, p.c(print))
        g.line(gx - 0.05f, -0.62f, gx - 0.01f, -0.53f, 0.02f, p.c(print))
        g.line(gx + 0.05f, -0.62f, gx + 0.01f, -0.53f, 0.02f, p.c(print))
        g.line(gx, -0.53f, gx, -0.45f, 0.018f, p.c(print))
        g.line(gx - 0.04f, -0.45f, gx + 0.04f, -0.45f, 0.018f, p.c(print))
        // Stamp and barcode on the leading half.
        val sx = 0.25f * dir
        g.strokeRect(sx - 0.13f, -0.3f, sx + 0.13f, -0.14f, 0.018f, p.c(0xB0A8321E.toInt()))
        for (i in 0 until 7) {
            val bx = sx - 0.1f + i * 0.033f
            g.line(bx, -0.27f, bx, -0.17f, if (i % 3 == 0) 0.014f else 0.007f, p.c(print))
        }
        // Handle slot: the peek hole.
        val hx = 0.24f * dir
        val sy = -h + 0.22f
        g.fillRoundRect(hx - 0.14f, sy - 0.06f, hx + 0.14f, sy + 0.06f, 0.06f, p.c(0xFF140A04.toInt()))
        g.line(hx - 0.12f, sy + 0.065f, hx + 0.12f, sy + 0.065f, 0.012f, p.c(0x40FFFFFF))
        val blink = !watched && fract(f.t * 0.37f) > 0.95f
        val look = dir * 0.03f
        if (watched) {
            val r = 0.034f * (0.8f + 0.4f * jolt)
            g.fillCircle(hx - 0.055f + look, sy, r, p.c(0xFFFFFFFF.toInt()))
            g.fillCircle(hx + 0.055f + look, sy, r, p.c(0xFFFFFFFF.toInt()))
            g.fillCircle(hx - 0.05f + look, sy, r * 0.4f, p.c(0xFF101018.toInt()))
            g.fillCircle(hx + 0.06f + look, sy, r * 0.4f, p.c(0xFF101018.toInt()))
        } else if (blink) {
            g.line(hx - 0.08f + look, sy, hx - 0.03f + look, sy, 0.014f, p.c(VISOR))
            g.line(hx + 0.03f + look, sy, hx + 0.08f + look, sy, 0.014f, p.c(VISOR))
        } else {
            g.fillCircle(hx - 0.055f + look, sy, 0.026f, p.c(VISOR))
            g.fillCircle(hx + 0.055f + look, sy, 0.026f, p.c(VISOR))
            g.fillCircle(hx - 0.048f + look, sy - 0.01f, 0.009f, p.c(0xFFFFFFFF.toInt()))
            g.fillCircle(hx + 0.062f + look, sy - 0.01f, 0.009f, p.c(0xFFFFFFFF.toInt()))
        }
        g.restore()
        if (watched) {
            val s = 0.55f * jolt
            exclaim(x, gy - h - 0.3f - hop, s * 0.72f, 0xFFFF2E3A.toInt(), ActorPaint.INK)
            // Sweat drops flicking off.
            val sw = fract(f.t * 2.2f)
            val dx = -dir * (0.4f + sw * 0.25f)
            val dy = -h - 0.1f + sw * sw * 0.4f
            drop(x + dx, gy + dy, 0.06f, Col.alpha(0xFFBFEFFF.toInt(), 1f - sw))
            drop(x + dx * 0.6f + dir * 0.9f, gy + dy - 0.1f, 0.05f, Col.alpha(0xFFBFEFFF.toInt(), 1f - fract(sw + 0.5f)))
        }
    }

    private fun drop(x: Float, y: Float, r: Float, color: Int) {
        g.fillCircle(x, y, r * 0.6f, color)
        poly.tri(g, x - r * 0.55f, y - r * 0.15f, x + r * 0.55f, y - r * 0.15f, x, y - r * 1.4f, color)
    }

    /** A crisp shape-drawn "!" (no font): outlined bar and dot, [s] = height. */
    fun exclaim(x: Float, y: Float, s: Float, color: Int, outline: Int) {
        if (s <= 0.01f) return
        val o = 0.05f * s / 0.55f + 0.02f
        val topW = 0.13f * s
        val botW = 0.08f * s
        val top = y - s
        val bot = y - s * 0.32f
        // Outline.
        poly.quad(g, x - topW - o, top - o, x + topW + o, top - o, x + botW + o, bot + o, x - botW - o, bot + o, outline)
        g.fillCircle(x, y - s * 0.08f, s * 0.12f + o, outline)
        poly.quad(g, x - topW, top, x + topW, top, x + botW, bot, x - botW, bot, color)
        g.fillCircle(x, y - s * 0.08f, s * 0.12f, color)
        g.line(x - topW * 0.4f, top + s * 0.08f, x - botW * 0.3f, bot - s * 0.12f, s * 0.05f, Col.alpha(0xFFFFFFFF.toInt(), 0.45f))
    }

    // =============================================================== overlays

    /** After the darkness overlay: shield bubble, armor ring, reload arc. */
    fun playerOverlay() {
        val pl = f.w.player
        if (pl.state == PlayerState.INTEL || pl.state == PlayerState.DEAD) return
        val gy = Geo.groundY(pl.floorF)
        val foot = gy - pl.z
        val boxed = pl.state == PlayerState.BOX
        val cy = if (boxed) foot - 0.45f else foot - 0.8f
        if (pl.shield) shieldBubble(pl.x, cy, if (boxed) 0.74f else 0.98f)
        if (pl.armorReady && pl.state != PlayerState.DOOR) {
            val a = 0.28f + 0.1f * sin(f.t * 3f)
            g.strokeCircle(pl.x, cy, 0.86f, 0.022f, Col.alpha(0xFFFFD24A.toInt(), a))
            for (i in 0 until 4) {
                val ang = f.t * 0.8f + i * PI.toFloat() / 2f
                val ax = pl.x + cos(ang) * 0.86f
                val ay = cy + sin(ang) * 0.86f
                g.fillCircle(ax, ay, 0.035f, Col.alpha(0xFFFFE08A.toInt(), a * 2f))
            }
        }
        if (pl.reloading && pl.state != PlayerState.DOOR) {
            val t = 1f - pl.reloadTime / max(0.01f, pl.reloadTotal)
            val ry = foot - (if (boxed) 1.15f else 1.95f)
            reloadArc(pl.x, ry, 0.2f, t)
        }
    }

    private fun shieldBubble(x: Float, cy: Float, r: Float) {
        val pulse = 0.75f + 0.25f * sin(f.t * 5f)
        val col = 0xFF3CC8FF.toInt()
        g.fillRadialGradient(x, cy, r, Col.alpha(col, 0.02f), Col.alpha(col, 0.2f * pulse))
        g.strokeCircle(x, cy, r, 0.03f, Col.alpha(0xFF8AEEFF.toInt(), 0.7f * pulse))
        // Rotating hex segments.
        val n = 6
        val step = 2f * PI.toFloat() / n
        for (i in 0 until n) {
            val a0 = f.t * 0.9f + i * step + 0.12f
            val a1 = a0 + step - 0.24f
            g.line(x + cos(a0) * r * 0.9f, cy + sin(a0) * r * 0.9f, x + cos(a1) * r * 0.9f, cy + sin(a1) * r * 0.9f, 0.022f, Col.alpha(0xFFBFF6FF.toInt(), 0.45f * pulse))
        }
        // Specular highlight up-left (the ceiling light).
        val h0 = -2.5f
        for (i in 0 until 4) {
            val a0 = h0 + i * 0.16f
            val a1 = a0 + 0.16f
            g.line(x + cos(a0) * r * 0.82f, cy + sin(a0) * r * 0.82f, x + cos(a1) * r * 0.82f, cy + sin(a1) * r * 0.82f, 0.05f - i * 0.008f, Col.alpha(0xFFFFFFFF.toInt(), 0.5f * pulse))
        }
        // Energy ripple.
        val rip = fract(f.t * 0.8f)
        g.strokeCircle(x, cy, r * (0.4f + rip * 0.6f), 0.015f, Col.alpha(0xFFBFF6FF.toInt(), 0.25f * (1f - rip)))
    }

    private fun reloadArc(x: Float, y: Float, r: Float, t: Float) {
        g.fillCircle(x, y, r + 0.06f, 0xB0000000.toInt())
        g.strokeCircle(x, y, r, 0.05f, 0x40FFFFFF)
        val n = 20
        val lit = (n * t).toInt()
        for (i in 0 until lit) {
            val a0 = -PI.toFloat() / 2f + i * 2f * PI.toFloat() / n
            val a1 = a0 + 2f * PI.toFloat() / n
            g.line(x + cos(a0) * r, y + sin(a0) * r, x + cos(a1) * r, y + sin(a1) * r, 0.06f, 0xFFFFD24A.toInt())
        }
        f.worldText("R", x, y + 0.08f, 0.2f, 0xFFFFD24A.toInt(), Gfx.Font.TITLE)
    }

    // ================================================================ enemies

    fun floorActors(fi: Int, fs: FloorState) {
        val gy = Geo.groundY(fi)
        if (!f.visibleY(gy - Geo.FLOOR_H, gy + 0.5f)) return
        val w = f.w
        for (pk in w.pickups) if (pk.floor == fi) pickup(pk.kind, pk.x, gy, pk.z, pk.age, pk.life)
        val pl = w.player
        val grappling = pl.state == PlayerState.TAKEDOWN
        val list = w.enemies
        for (i in list.indices) {
            val e = list[i]
            if (e.floor != fi) continue
            // The victim of a takedown is drawn inside the player's grapple.
            if (grappling && e.state == EnemyState.CHOKED && e.id == pl.takedownTarget) continue
            cast.enemy(e, i, gy, fs)
        }
        for (gr in w.grenades) if (gr.floor == fi) grenade(gr.x, gy - gr.z, gr.fuse)
    }

    /** In the dark, alive enemies are silhouettes with glowing eyes. */
    fun darkEyes(fi: Int, fs: FloorState) = cast.darkEyes(fi, fs)

    // ============================================================= helicopter

    private fun helicopter() {
        val w = f.w
        val gy0 = Geo.groundY(0)
        val drift = max(0f, w.time - 1.2f)
        val hx = 2.3f + drift * drift * 3.2f + drift * 1.2f
        val hy = gy0 - 9.35f - drift * 1.5f + sin(f.t * 1.7f) * 0.08f
        if (hx > Geo.FLOOR_W + 4f || hy < f.camY - 3f) return
        val pl = w.player
        // Searchlight onto the roof.
        if (drift < 1.5f) {
            val a = (1f - drift / 1.5f)
            poly.quad(g, hx - 0.2f, hy + 0.5f, hx + 0.2f, hy + 0.5f, hx + 1.6f, gy0, hx - 1.2f, gy0, Col.alpha(0xFFE8F8FF.toInt(), 0.07f * a))
            g.save()
            g.translate(hx + 0.2f, gy0)
            g.scale(1f, 0.15f)
            g.fillRadialGradient(0f, 0f, 1.6f, Col.alpha(0xFFE8F8FF.toInt(), 0.35f * a), 0x00E8F8FF)
            g.restore()
        }
        // Rope to the player while they rappel in.
        if (pl.state == PlayerState.INTRO && pl.floorF == 0f) {
            val py = gy0 - pl.z - 1.9f
            g.line(hx - 0.3f, hy + 0.4f, pl.x + 0.04f * pl.facing, py, 0.035f, 0xFF1A1622.toInt())
        } else if (drift < 2f) {
            val sway = sin(f.t * 2f) * 0.3f
            g.line(hx - 0.3f, hy + 0.4f, hx - 0.3f + sway, hy + 2.2f - drift, 0.035f, 0xFF1A1622.toInt())
        }
        val body = 0xFF1A1830.toInt()
        val bodyHi = 0xFF2E2A4E.toInt()
        poly.quad(g, hx - 0.8f, hy - 0.05f, hx - 3.1f, hy - 0.25f, hx - 3.1f, hy - 0.08f, hx - 0.8f, hy + 0.3f, body)
        poly.quad(g, hx - 2.9f, hy - 0.2f, hx - 3.25f, hy - 0.75f, hx - 3.35f, hy - 0.72f, hx - 3.15f, hy - 0.1f, body)
        val tr = f.t * 60f
        g.line(hx - 3.2f + cos(tr) * 0.35f, hy - 0.4f + sin(tr) * 0.35f, hx - 3.2f - cos(tr) * 0.35f, hy - 0.4f - sin(tr) * 0.35f, 0.04f, 0x90A0A0C0.toInt())
        g.strokeCircle(hx - 3.2f, hy - 0.4f, 0.35f, 0.015f, 0x30A0A0C0)
        poly.begin()
            .add(hx - 1.0f, hy - 0.45f).add(hx + 0.5f, hy - 0.5f).add(hx + 1.25f, hy - 0.1f)
            .add(hx + 1.3f, hy + 0.2f).add(hx + 0.8f, hy + 0.5f).add(hx - 0.8f, hy + 0.5f).add(hx - 1.15f, hy + 0.1f)
            .fill(g, body)
        poly.begin().add(hx - 1.0f, hy - 0.45f).add(hx + 0.5f, hy - 0.5f).add(hx + 0.9f, hy - 0.3f).add(hx - 1.05f, hy - 0.3f).fill(g, bodyHi)
        poly.begin().add(hx + 0.45f, hy - 0.42f).add(hx + 1.15f, hy - 0.08f).add(hx + 1.18f, hy + 0.12f).add(hx + 0.45f, hy + 0.1f).fill(g, 0xFF2CD8FF.toInt())
        poly.begin().add(hx + 0.55f, hy - 0.36f).add(hx + 0.8f, hy - 0.24f).add(hx + 0.6f, hy + 0.05f).add(hx + 0.5f, hy + 0.05f).fill(g, 0x80FFFFFF.toInt())
        g.fillRect(hx - 0.55f, hy - 0.3f, hx + 0.05f, hy + 0.4f, 0xFF07060C.toInt())
        g.fillRect(hx - 0.55f, hy - 0.3f, hx + 0.05f, hy - 0.25f, 0x60FF3D9A)
        g.fillRect(hx - 1.1f, hy + 0.22f, hx + 1.25f, hy + 0.27f, 0xFFFF2E88.toInt())
        g.line(hx - 0.6f, hy + 0.5f, hx - 0.7f, hy + 0.75f, 0.04f, body)
        g.line(hx + 0.6f, hy + 0.5f, hx + 0.7f, hy + 0.75f, 0.04f, body)
        g.line(hx - 1.1f, hy + 0.75f, hx + 1.2f, hy + 0.75f, 0.05f, body)
        g.fillRect(hx - 0.05f, hy - 0.72f, hx + 0.05f, hy - 0.48f, body)
        val blade = abs(sin(f.t * 40f))
        g.fillRoundRect(hx - 2.6f, hy - 0.8f, hx + 2.6f, hy - 0.7f, 0.05f, 0x40B0B0D0)
        g.line(hx - 2.6f * blade, hy - 0.75f, hx + 2.6f * blade, hy - 0.75f, 0.05f, 0xA0303048.toInt())
        if (sin(f.t * 5f) > 0f) f.glowDot(hx - 3.3f, hy - 0.75f, 0.05f, 0xFFFF3040.toInt())
        if (sin(f.t * 5f + 2f) > 0f) f.glowDot(hx + 1.2f, hy + 0.25f, 0.045f, 0xFF40FF80.toInt())
    }

    private fun hatch(x: Float, floorF: Float) {
        val fi = floorF.toInt()
        val rt = fi * Geo.FLOOR_H + Building.SLAB
        val pl = f.w.player
        val a = min(1f, pl.z / 1.2f)
        g.fillRect(x - 0.45f, rt - 0.35f, x + 0.45f, rt + 0.02f, 0xFF050308.toInt())
        poly.quad(g, x - 0.45f, rt, x + 0.45f, rt, x + 0.8f, rt + 3.2f, x - 0.8f, rt + 3.2f, Col.alpha(0xFFE8F8FF.toInt(), 0.1f * a))
        poly.quad(g, x + 0.45f, rt, x + 0.5f, rt, x + 0.62f, rt + 0.85f, x + 0.56f, rt + 0.85f, 0xFF4A4858.toInt())
    }

    // ================================================================= loot

    private fun pickup(kind: PickupKind, x: Float, gy: Float, z: Float, age: Float, life: Float) {
        val y = gy - z - 0.15f
        if (life < 3f && (f.t * 8f).toInt() % 2 == 0) return
        val bob = sin(age * 4f) * 0.06f
        val cy = y + bob
        val col = when (kind) {
            PickupKind.MEDKIT -> 0xFFFF4A5E.toInt()
            PickupKind.SHOTGUN -> 0xFFFFA020.toInt()
            PickupKind.MINIGUN -> 0xFFFF6A20.toInt()
            PickupKind.SHIELD -> 0xFF3CC8FF.toInt()
            PickupKind.SLOWMO -> 0xFFB080FF.toInt()
            PickupKind.GRENADE -> 0xFF9AE040.toInt()
            PickupKind.CASH -> 0xFF40FF90.toInt()
        }
        p.alphaMul = 1f
        p.contactShadow(x, gy, 0.26f, max(0f, gy - cy - 0.3f))
        g.fillCircle(x, cy, 0.42f, Col.alpha(col, 0.14f))
        g.fillRoundRect(x - 0.25f, cy - 0.25f, x + 0.25f, cy + 0.25f, 0.09f, ActorPaint.INK)
        g.fillRoundRect(x - 0.22f, cy - 0.22f, x + 0.22f, cy + 0.22f, 0.07f, 0xF0141420.toInt())
        g.strokeRoundRect(x - 0.22f, cy - 0.22f, x + 0.22f, cy + 0.22f, 0.07f, 0.035f, col)
        g.line(x - 0.14f, cy - 0.17f, x + 0.1f, cy - 0.17f, 0.02f, Col.alpha(0xFFFFFFFF.toInt(), 0.25f))
        pickupIcon(kind, x, cy, col)
        g.fillRect(x - 0.015f, cy - 0.9f, x + 0.015f, cy - 0.28f, Col.alpha(col, 0.35f))
    }

    fun pickupIcon(kind: PickupKind, x: Float, y: Float, col: Int, s: Float = 1f) {
        when (kind) {
            PickupKind.MEDKIT -> {
                g.fillRect(x - 0.05f * s, y - 0.14f * s, x + 0.05f * s, y + 0.14f * s, col)
                g.fillRect(x - 0.14f * s, y - 0.05f * s, x + 0.14f * s, y + 0.05f * s, col)
            }
            PickupKind.SHOTGUN -> {
                g.line(x - 0.14f * s, y + 0.03f * s, x + 0.15f * s, y - 0.02f * s, 0.06f * s, col)
                g.line(x - 0.14f * s, y + 0.03f * s, x - 0.16f * s, y + 0.12f * s, 0.06f * s, col)
                g.line(x - 0.02f * s, y + 0.06f * s, x + 0.1f * s, y + 0.05f * s, 0.04f * s, col)
            }
            PickupKind.MINIGUN -> {
                for (i in -1..1) g.line(x - 0.08f * s, y + i * 0.05f * s, x + 0.16f * s, y + i * 0.05f * s, 0.03f * s, col)
                g.fillRect(x - 0.16f * s, y - 0.09f * s, x - 0.06f * s, y + 0.09f * s, col)
            }
            PickupKind.SHIELD -> {
                poly.begin().add(x - 0.13f * s, y - 0.12f * s).add(x + 0.13f * s, y - 0.12f * s).add(x + 0.12f * s, y + 0.02f * s).add(x, y + 0.15f * s).add(x - 0.12f * s, y + 0.02f * s).fill(g, col)
            }
            PickupKind.SLOWMO -> {
                g.strokeCircle(x, y, 0.13f * s, 0.035f * s, col)
                g.line(x, y, x, y - 0.09f * s, 0.03f * s, col)
                g.line(x, y, x + 0.07f * s, y + 0.03f * s, 0.03f * s, col)
            }
            PickupKind.GRENADE -> {
                g.fillCircle(x, y + 0.03f * s, 0.11f * s, col)
                g.fillRect(x - 0.04f * s, y - 0.13f * s, x + 0.04f * s, y - 0.06f * s, col)
                g.strokeCircle(x + 0.07f * s, y - 0.12f * s, 0.035f * s, 0.02f * s, col)
            }
            PickupKind.CASH -> {
                f.worldText("$", x, y + 0.11f * s, 0.32f * s, col, Gfx.Font.TITLE)
            }
        }
    }

    private fun grenade(x: Float, y: Float, fuse: Float) {
        val yy = y - 0.08f
        g.fillCircle(x, yy, 0.09f + ActorPaint.OUT, ActorPaint.INK)
        g.fillCircle(x, yy, 0.09f, 0xFF3A4A2A.toInt())
        g.fillCircle(x - 0.03f, yy - 0.03f, 0.03f, 0xFF6A7A4A.toInt())
        val blink = sin(f.t * (20f + (1f - fuse) * 40f)) > 0f
        if (blink) f.glowDot(x, yy - 0.1f, 0.03f, 0xFFFF3030.toInt())
        if (fuse < 0.4f) g.strokeCircle(x, yy, 2.3f * (1f - fuse / 0.4f) * 0.4f + 0.2f, 0.02f, Col.alpha(0xFFFF3030.toInt(), 0.4f))
    }
}
