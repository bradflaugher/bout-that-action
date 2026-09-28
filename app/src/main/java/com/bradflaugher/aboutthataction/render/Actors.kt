package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Body
import com.bradflaugher.aboutthataction.engine.Enemy
import com.bradflaugher.aboutthataction.engine.EnemyKind
import com.bradflaugher.aboutthataction.engine.EnemyState
import com.bradflaugher.aboutthataction.engine.HallState
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
 * brightest thing on screen: a fitted sneaking suit, a swept helmet with a cyan visor prow, a few exact lit seams.
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
        /** The sneaking suit: a cool gunmetal slate, so he reads off every zone's purples and reds. */
        const val SUIT = 0xFF1C2A4A.toInt()
        const val SUIT_LIT = 0xFF9EC4E6.toInt()
        const val SUIT_DARK = 0xFF111830.toInt()
        const val ARMOR = 0xFF0F121C.toInt()
        const val BOOT = 0xFF141925.toInt()
        const val GLOVE = 0xFF161B28.toInt()
        const val HELMET = 0xFF36527E.toInt()
        /** The few hard pieces (helmet, knees, gauntlets): lacquered, a step brighter than the suit. */
        const val PLATE = 0xFF33507C.toInt()
        const val PLATE_FAR = 0xFF1C2A46.toInt()
        const val GLASS = 0xFF08323E.toInt()
        const val VISOR = 0xFF3CF4FF.toInt()
        const val RIM = 0xFF7CF4FF.toInt()
        /** The agent reads larger than the guards: the hero scale (visual only; hitboxes are the engine's). */
        const val HS = 1.12f
        const val HEAD = 0.92f
    }

    // ================================================================= player

    fun player(force: Boolean = false) {
        val pl = f.w.player
        if (pl.state == PlayerState.ELEVATOR && !force) return
        if (pl.state == PlayerState.STASH) return
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

        // SILENT: once through a passage you stay in the far doorway's shadow (you arrive
        // hidden), rather than walking out lit and then vanishing into it.
        val hiddenArrival = pl.state == PlayerState.PASSAGE && f.w.silent && pl.stateTime >= World.PASSAGE_TIME * 0.5f
        val hidden = pl.state == PlayerState.DOOR || hiddenArrival
        // Stepping through a passage: into the dark doorway, then out of the far one.
        val passing = if (pl.state == PlayerState.PASSAGE) {
            val half = World.PASSAGE_TIME * 0.5f
            Rig.smooth(if (pl.stateTime < half) pl.stateTime / half else (World.PASSAGE_TIME - pl.stateTime) / half)
        } else 0f
        if (!hidden && pl.state != PlayerState.ELEVATOR) {
            val hw = if (pl.state == PlayerState.BOX) 0.62f else 0.42f
            val keepA = p.alphaMul
            p.alphaMul = keepA * (1f - passing)
            p.contactShadow(pl.x, gy, hw, pl.z)
            p.alphaMul = keepA
        }
        if (!hidden && pl.state != PlayerState.DEAD) signature(pl.x, gy, foot, pl.z, pl.state == PlayerState.BOX || pl.carBox, 1f - passing)

        if (pl.state == PlayerState.ELEVATOR && pl.carBox) {
            // Boxed up in the lift.
            box(pl.x, gy, dir, 0f, pl.stateTime)
            p.reset()
            return
        }
        when (pl.state) {
            PlayerState.BOX -> box(pl.x, gy, dir, pl.vx, pl.stateTime)
            PlayerState.DOOR -> doorHide(pl.x, foot, dir)
            PlayerState.PASSAGE -> if (hiddenArrival) doorHide(pl.x, foot, dir) else {
                // Walking into the doorway's dark (a touch smaller: deeper in), then out.
                poseHero(pl.x, foot, dir)
                p.flat = 0xFF04050A.toInt()
                p.flatAmt = 0.92f * passing
                val sc = 1f - 0.07f * passing
                g.save()
                g.translate(pl.x, foot)
                g.scale(sc, sc)
                g.translate(-pl.x, -foot)
                drawHero(ghost = false)
                g.restore()
            }
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

    /**
     * The agent's beacon, so he's found in half a second on any floor: a cyan backlight hugging
     * the silhouette and a crisp cyan ring on the floor under him. Three calls, all additive.
     */
    private fun signature(x: Float, gy: Float, foot: Float, z: Float, boxed: Boolean, k: Float) {
        val a = p.alphaMul * k
        if (a <= 0.01f) return
        val cy = if (boxed) foot - 0.4f else foot - 0.85f
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(x, cy)
        g.scale(if (boxed) 1f else 0.62f, 1f)
        g.glow(0f, 0f, if (boxed) 1.15f else 1.6f, Col.alpha(VISOR, 0.46f * a))
        g.restore()
        val fz = (1f - z / 2.5f).coerceIn(0f, 1f)
        if (fz > 0f) {
            val pulse = 0.85f + 0.15f * sin(f.t * 3.2f)
            val r = if (boxed) 0.66f else 0.5f
            g.save()
            g.translate(x, gy - 0.02f)
            g.scale(1f, 0.24f)
            g.glow(0f, 0f, r * 1.7f, Col.alpha(VISOR, 0.42f * a * fz))
            g.strokeCircle(0f, 0f, r, 0.1f, Col.alpha(RIM, 0.55f * a * fz * pulse))
            g.restore()
        }
        g.blend(Gfx.Blend.NORMAL)
    }

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

    /** What the auto-aim would pick, to aim the gun at it: the engine's own choice. */
    private fun aimTarget(): Enemy? = f.w.aimTarget()

    private fun poseHero(x: Float, foot: Float, dir: Int) {
        val pl = f.w.player
        val state = pl.state
        k.setup(dir, foot - 0.045f * HS, HS)
        // Heroic proportions: a touch smaller head than the guards.
        k.headR *= HEAD
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
        // SILENT holsters the gun: no firing in that mode, so the hands are free for takedowns.
        val silent = f.w.silent && !pl.reloading && (state == PlayerState.NORMAL || state == PlayerState.ELEVATOR || state == PlayerState.PASSAGE)
        if (silent) showGun = false
        var lean: Float
        var nod = 0f
        when {
            state == PlayerState.PASSAGE -> {
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
            speed > 0.6f && state == PlayerState.NORMAL && silent -> {
                // SILENT: the sneak. Low, leaning in, hands up and ready for a grab.
                val runBlend = ((speed - 1f) / 2.8f).coerceIn(0f, 1f)
                val u = pl.runTime * 2.05f
                k.locomote(x, u, runBlend * 0.8f, 0.5f)
                lean = 0.34f + 0.16f * runBlend
                k.spine(lean, -0.25f)
                val sw = sin(u * Rig.TAU) * 0.12f
                k.armFK(k.armF, 0.75f + sw, 1.35f)
                k.armFK(k.armB, 0.35f - sw, 1.55f)
            }
            speed > 0.6f && state == PlayerState.NORMAL -> {
                val runBlend = ((speed - 1f) / 2.8f).coerceIn(0f, 1f)
                val u = pl.runTime * 2.05f
                k.locomote(x, u, runBlend)
                lean = 0.1f + 0.22f * runBlend
                k.spine(lean, -0.05f)
                k.swingArms(u, runBlend)
            }
            silent && state == PlayerState.NORMAL -> {
                // SILENT idle: a low CQC guard, weight forward, open hands.
                k.stand(x, 0.1f + breathe * 0.006f, 0.17f, -0.2f)
                lean = 0.2f + breathe * 0.01f
                k.spine(lean, -0.12f)
                k.armFK(k.armF, 0.85f, 1.2f)
                k.armFK(k.armB, 0.45f, 1.5f)
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
            pl.reloading && state != PlayerState.PASSAGE -> {
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
            airborne || state == PlayerState.PASSAGE -> {
                gunX = k.armF.ex; gunY = k.armF.ey
                gunUp = if (airborne) 0.3f else -0.5f
            }
            speed > 0.6f || pl.invuln > 1.0f -> {
                gunX = k.armF.ex; gunY = k.armF.ey
                val fa = atan2(-(k.armF.ey - k.armF.jy), (k.armF.ex - k.armF.jx) * dir)
                gunUp = (fa + 0.35f).coerceIn(-1.0f, 0.2f)
            }
            silent -> Unit
            else -> {
                // Low ready: muzzle down and forward.
                gunX = k.hipX + 0.2f * dir
                gunY = k.hipY + 0.02f + breathe * 0.004f
                gunUp = -0.85f
                k.ik(k.armF, gunX, gunY, false)
            }
        }
        // Jump-swat at a lamp: the near arm flicks straight up (no gun in that hand).
        if (pl.swatTime > 0f) {
            val q = Rig.easeOut(1f - pl.swatTime / World.SWAT_TIME)
            k.armFK(k.armF, 2.2f + 0.65f * q, 0.35f - 0.25f * q)
            showGun = false
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
        look.boots = BOOT
        look.gloves = GLOVE
        look.skin = SUIT
        look.rim = if (ghost) 0 else Col.alpha(RIM, 0.45f)
        look.legW = 0.94f
        look.armW = 0.92f
    }

    /**
     * A strand of the suit's emissive piping: a hairline core with a soft bloom around it and
     * a white-hot centre. Thin and exact, like tailoring, not a glow stick.
     */
    private fun pipe(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, a: Float = 1f) {
        g.blend(Gfx.Blend.ADD)
        if (p.hi) g.line(x1, y1, x2, y2, w * 3.4f, p.c(Col.alpha(VISOR, 0.16f * a)))
        g.line(x1, y1, x2, y2, w, p.c(Col.alpha(VISOR, 0.9f * a)))
        if (p.hi) g.line(x1, y1, x2, y2, w * 0.4f, p.c(Col.alpha(0xFFE8FFFF.toInt(), 0.6f * a)))
        g.blend(Gfx.Blend.NORMAL)
    }

    private fun drawHero(ghost: Boolean, skipFrontArm: Boolean = false) {
        heroLook(ghost)
        val dir = k.dir
        p.twoPass {
            body.arm(k.armB, look, far = true)
            bracer(k.armB, far = true)
            shoulderCap(k.armB, far = true)
            if (magInHand && !p.ink) g.fillRect(k.armB.ex - 0.025f, k.armB.ey - 0.09f, k.armB.ex + 0.025f, k.armB.ey, p.c(0xFF2A2E3A.toInt()))
            body.leg(k.legB, look, far = true)
            shinGuard(k.legB, far = true)
        }
        p.twoPass {
            body.leg(k.legF, look, far = false)
            shinGuard(k.legF, far = false)
            heroTorso()
            heroDetails(dir, ghost)
            heroCollar()
            heroHead(dir, ghost)
        }
        if (skipFrontArm) return
        p.twoPass {
            if (showGun) {
                // A size up on the agent: GUNS HOT has to read against SILENT's empty hands.
                body.gun(gunKind, gunX, gunY, gunUp, VISOR, spin = f.t * 60f, scale = if (gunKind == 0) 1.3f else 1.1f)
            }
            body.arm(k.armF, look, far = false, hand = true)
            bracer(k.armF, far = false)
            shoulderCap(k.armF, far = false)
        }
        if (!ghost && !p.ink) heroStrips()
        if (flash && !ghost) body.muzzleFlash(gunUp, if (gunKind == 1) 0.2f else if (gunKind == 2) 0.17f else 0.15f, (f.t * 30f).toInt())
    }

    private val nrm = FloatArray(2)

    /** The side of a limb segment facing forward (the way he looks), as a unit normal in [nrm]. */
    private fun frontOf(x1: Float, y1: Float, x2: Float, y2: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        var nx = -dy / len
        var ny = dx / len
        if (nx * k.dir < 0f) {
            nx = -nx; ny = -ny
        }
        nrm[0] = nx; nrm[1] = ny
    }

    /**
     * A low-profile knee pad moulded into the suit: a lacquered sleeve over the knee, inside
     * the leg's own contour (its ink hides under the leg), so it reads as tone, not a joint.
     */
    private fun shinGuard(l: Limb, far: Boolean) {
        frontOf(l.jx, l.jy, l.ex, l.ey)
        val lw = k.limbW * look.legW
        val ox = nrm[0] * lw * 0.12f
        val oy = nrm[1] * lw * 0.12f
        val x1 = Rig.mix(l.jx, l.ax, 0.12f) + ox
        val y1 = Rig.mix(l.jy, l.ay, 0.12f) + oy
        val x2 = Rig.mix(l.jx, l.ex, 0.3f) + ox
        val y2 = Rig.mix(l.jy, l.ey, 0.3f) + oy
        p.bone(x1, y1, x2, y2, lw * 0.72f, lw * 0.62f, if (far) PLATE_FAR else PLATE, lit = !far)
    }

    /** A slim gauntlet over the forearm: a sleeve that hugs the wrist, tone rather than a plate. */
    private fun bracer(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        val x1 = Rig.mix(l.jx, l.ex, 0.4f)
        val y1 = Rig.mix(l.jy, l.ey, 0.4f)
        val x2 = Rig.mix(l.jx, l.ex, 0.92f)
        val y2 = Rig.mix(l.jy, l.ey, 0.92f)
        p.bone(x1, y1, x2, y2, aw * 0.8f, aw * 0.64f, if (far) PLATE_FAR else PLATE, lit = !far)
    }

    /** A lacquered cap moulded over the shoulder and down the upper arm, inside its contour. */
    private fun shoulderCap(l: Limb, far: Boolean) {
        val aw = k.limbW * look.armW
        val x2 = Rig.mix(l.ax, l.jx, 0.42f)
        val y2 = Rig.mix(l.ay, l.jy, 0.42f)
        p.bone(l.ax, l.ay, x2, y2, aw * 1.0f, aw * 0.84f, if (far) PLATE_FAR else PLATE, lit = !far)
    }

    /**
     * The agent's own torso: an athlete's V, broad through the chest and lats, pinched at the
     * waist, painted lamp-lit across the chest into shadow down the back, with the neon rim.
     */
    private fun heroTorso() {
        p.lightFrom(k.dir)
        val w = k.waistD * 0.9f
        val c = k.chestD * 1.18f
        fun x(a: Float, s: Float) = body.ptX(a, s)
        fun y(a: Float, s: Float) = body.ptY(a, s)
        p.begin()
            .add(x(-0.12f, -w * 0.52f), y(-0.12f, -w * 0.52f))
            .add(x(-0.14f, w * 0.44f), y(-0.14f, w * 0.44f))
            .add(x(0.1f, w * 0.42f), y(0.1f, w * 0.42f))
            .add(x(0.3f, w * 0.41f), y(0.3f, w * 0.41f))
            .add(x(0.48f, c * 0.41f), y(0.48f, c * 0.41f))
            .add(x(0.64f, c * 0.48f), y(0.64f, c * 0.48f))
            .add(x(0.8f, c * 0.51f), y(0.8f, c * 0.51f))
            .add(x(0.92f, c * 0.44f), y(0.92f, c * 0.44f))
            .add(x(1.0f, c * 0.28f), y(1.0f, c * 0.28f))
            .add(x(1.04f, c * 0.04f), y(1.04f, c * 0.04f))
            .add(x(1.03f, -c * 0.22f), y(1.03f, -c * 0.22f))
            .add(x(0.96f, -c * 0.44f), y(0.96f, -c * 0.44f))
            .add(x(0.84f, -c * 0.53f), y(0.84f, -c * 0.53f))
            .add(x(0.66f, -c * 0.5f), y(0.66f, -c * 0.5f))
            .add(x(0.46f, -w * 0.5f), y(0.46f, -w * 0.5f))
            .add(x(0.28f, -w * 0.44f), y(0.28f, -w * 0.44f))
            .add(x(0.08f, -w * 0.5f), y(0.08f, -w * 0.5f))
            .shapeLit(SUIT, x(1.0f, c * 0.5f), y(1.0f, c * 0.5f), x(0.05f, -w * 0.6f), y(0.05f, -w * 0.6f))
        if (p.ink) return
        if (p.shading) {
            // The chest plane catching the lamp: one soft wedge across the pecs.
            p.begin()
                .add(x(0.96f, c * 0.3f), y(0.96f, c * 0.3f))
                .add(x(0.83f, c * 0.46f), y(0.83f, c * 0.46f))
                .add(x(0.6f, c * 0.42f), y(0.6f, c * 0.42f))
                .add(x(0.72f, -c * 0.05f), y(0.72f, -c * 0.05f))
                .add(x(0.98f, -c * 0.1f), y(0.98f, -c * 0.1f))
                .shapeGradDetail(Col.alpha(SUIT_LIT, 0.55f), Col.alpha(SUIT_LIT, 0f), x(0.97f, c * 0.3f), y(0.97f, c * 0.3f), x(0.66f, c * 0.1f), y(0.66f, c * 0.1f))
        }
        if (look.rim != 0) {
            g.blend(Gfx.Blend.ADD)
            val i = ActorPaint.RIM_W * 0.5f
            p.detail(x(0.5f, -w * 0.5f + i), y(0.5f, -w * 0.5f + i), x(0.68f, -c * 0.5f + i), y(0.68f, -c * 0.5f + i), ActorPaint.RIM_W * 0.8f, Col.fade(look.rim, 0.6f))
            p.detail(x(0.68f, -c * 0.5f + i), y(0.68f, -c * 0.5f + i), x(0.92f, -c * 0.47f + i), y(0.92f, -c * 0.47f + i), ActorPaint.RIM_W * 0.8f, Col.fade(look.rim, 0.6f))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    private fun heroDetails(dir: Int, ghost: Boolean) {
        if (p.ink) return
        val hs = k.hs
        val c = k.chestD * 1.18f
        val w = k.waistD * 0.9f
        // A slim belt riding the hips, one brushed-steel buckle.
        p.detail(body.ptX(0.02f, -w * 0.5f), body.ptY(0.02f, -w * 0.5f), body.ptX(0.02f, w * 0.44f), body.ptY(0.02f, w * 0.44f), 0.045f * hs, ARMOR)
        if (p.shading) {
            // One tailored panel line: under the pec and down the flank, a crease, not a stripe.
            p.detail(body.ptX(0.66f, c * 0.44f), body.ptY(0.66f, c * 0.44f), body.ptX(0.56f, c * 0.02f), body.ptY(0.56f, c * 0.02f), 0.012f, SUIT_DARK)
            p.detail(body.ptX(0.56f, c * 0.02f), body.ptY(0.56f, c * 0.02f), body.ptX(0.1f, -w * 0.18f), body.ptY(0.1f, -w * 0.18f), 0.012f, SUIT_DARK)
        }
        if (!showGun && !ghost && f.w.silent) {
            // SILENT: the pistol holstered on the thigh, hands free for CQC.
            val l = k.legF
            p.detail(Rig.mix(l.ax, l.jx, 0.2f) - 0.03f * dir, Rig.mix(l.ay, l.jy, 0.2f), Rig.mix(l.ax, l.jx, 0.62f) - 0.035f * dir, Rig.mix(l.ay, l.jy, 0.62f), 0.1f, ARMOR)
            if (p.shading) p.detail(Rig.mix(l.ax, l.jx, 0.24f) - 0.01f * dir, Rig.mix(l.ay, l.jy, 0.24f), Rig.mix(l.ax, l.jx, 0.56f) - 0.015f * dir, Rig.mix(l.ay, l.jy, 0.56f), 0.02f, 0xFF3A4260.toInt())
        }
    }

    /** The agent's light lines, down the gauntlet and the outer thigh: few, exact. */
    private fun heroStrips() {
        val af = k.armF
        frontOf(af.jx, af.jy, af.ex, af.ey)
        val o = k.limbW * 0.14f
        pipe(Rig.mix(af.jx, af.ex, 0.45f) - nrm[0] * o, Rig.mix(af.jy, af.ey, 0.45f) - nrm[1] * o, Rig.mix(af.jx, af.ex, 0.84f) - nrm[0] * o, Rig.mix(af.jy, af.ey, 0.84f) - nrm[1] * o, 0.016f)
        val lf = k.legF
        frontOf(lf.ax, lf.ay, lf.jx, lf.jy)
        val q = k.limbW * 0.1f
        pipe(Rig.mix(lf.ax, lf.jx, 0.22f) - nrm[0] * q, Rig.mix(lf.ay, lf.jy, 0.22f) - nrm[1] * q, Rig.mix(lf.ax, lf.jx, 0.78f) - nrm[0] * q, Rig.mix(lf.ay, lf.jy, 0.78f) - nrm[1] * q, 0.016f, 0.85f)
    }

    /**
     * The suit's high neck: one tapered cowl from the shoulders up under the helmet, so the
     * head grows out of the body instead of sitting on a stalk. A thin lit ring at its lip.
     */
    private fun heroCollar() {
        val c = k.chestD * 1.18f
        p.begin()
            .add(body.ptX(0.9f, -c * 0.38f), body.ptY(0.9f, -c * 0.38f))
            .add(hpX(-0.6f), hpY(0.5f))
            .add(hpX(-0.1f), hpY(0.78f))
            .add(hpX(0.38f), hpY(0.8f))
            .add(body.ptX(0.98f, c * 0.24f), body.ptY(0.98f, c * 0.24f))
            .shapeLit(SUIT, hpX(0.4f), hpY(0.6f), body.ptX(0.9f, -c * 0.4f), body.ptY(0.9f, -c * 0.4f))
        if (p.ink) return
        if (p.shading) {
            // The fold where the cowl meets the chest.
            p.detail(body.ptX(0.95f, -c * 0.3f), body.ptY(0.95f, -c * 0.3f), body.ptX(1.0f, c * 0.2f), body.ptY(1.0f, c * 0.2f), 0.014f, SUIT_DARK)
        }
        if (look.rim != 0) pipe(hpX(-0.56f), hpY(0.56f), hpX(0.34f), hpY(0.84f), 0.015f, 0.85f)
    }

    // Head-space helpers: [u] forward (the facing), [v] down, in head radii.
    private fun hpX(u: Float) = k.headX + u * k.headR * k.dir
    private fun hpY(v: Float) = k.headY + v * k.headR

    private fun heroHead(dir: Int, ghost: Boolean) {
        val r = k.headR
        // A sleek sculpted helmet: a smooth egg of a skull closing to a chin, one painted form
        // lit from the crown.
        p.begin()
            .add(hpX(-0.86f), hpY(0.34f))
            .add(hpX(-1.08f), hpY(0.0f))
            .add(hpX(-1.22f), hpY(-0.3f))
            .add(hpX(-0.96f), hpY(-0.66f))
            .add(hpX(-0.52f), hpY(-0.96f))
            .add(hpX(0.0f), hpY(-1.03f))
            .add(hpX(0.48f), hpY(-0.92f))
            .add(hpX(0.82f), hpY(-0.62f))
            .add(hpX(0.98f), hpY(-0.3f))
            .add(hpX(1.0f), hpY(0.2f))
            .add(hpX(0.86f), hpY(0.6f))
            .add(hpX(0.45f), hpY(0.86f))
            .add(hpX(-0.2f), hpY(0.86f))
            .add(hpX(-0.62f), hpY(0.66f))
            .shapeLit(HELMET, hpX(0.3f), hpY(-1.0f), hpX(-0.4f), hpY(0.9f))
        if (!p.ink && p.shading) {
            // The jaw in shadow, and a soft gloss on the crown.
            p.begin()
                .add(hpX(1.0f), hpY(0.2f))
                .add(hpX(0.86f), hpY(0.6f))
                .add(hpX(0.45f), hpY(0.86f))
                .add(hpX(-0.2f), hpY(0.86f))
                .add(hpX(0.2f), hpY(0.46f))
                .shapeShade(HELMET)
            g.blend(Gfx.Blend.ADD)
            g.glow(hpX(0.05f), hpY(-0.6f), r * 0.62f, p.c(Col.alpha(0xFFFFFFFF.toInt(), 0.2f * (1f - p.flatAmt))))
            g.blend(Gfx.Blend.NORMAL)
        }
        // The visor: a smoked-glass prow jutting past the face, part of the silhouette, so the
        // way he looks reads at a glance.
        p.begin()
            .add(hpX(-0.12f), hpY(-0.4f))
            .add(hpX(0.5f), hpY(-0.56f))
            .add(hpX(1.2f), hpY(-0.42f))
            .add(hpX(1.32f), hpY(-0.18f))
            .add(hpX(1.22f), hpY(0.1f))
            .add(hpX(0.5f), hpY(0.18f))
            .add(hpX(-0.06f), hpY(0.08f))
            .shape(if (p.shading) GLASS else VISOR)
        if (p.ink) return
        if (p.shading) {
            // Lit from inside: the lens, white-hot at the front edge.
            p.begin()
                .add(hpX(0.08f), hpY(-0.28f))
                .add(hpX(0.54f), hpY(-0.4f))
                .add(hpX(1.14f), hpY(-0.3f))
                .add(hpX(1.2f), hpY(-0.16f))
                .add(hpX(1.13f), hpY(0.0f))
                .add(hpX(0.52f), hpY(0.05f))
                .add(hpX(0.1f), hpY(-0.03f))
                .shapeGradDetail(Col.mul(VISOR, 0.7f), 0xFFE8FFFF.toInt(), hpX(0.1f), hpY(-0.1f), hpX(1.18f), hpY(-0.18f))
            // One specular glint across the glass's top edge.
            p.detail(hpX(0.4f), hpY(-0.47f), hpX(1.1f), hpY(-0.36f), 0.01f, 0xA0FFFFFF.toInt())
            // A hairline seam from the visor back round the helmet.
            p.detail(hpX(-0.1f), hpY(-0.18f), hpX(-1.0f), hpY(-0.26f), 0.012f, Col.alpha(VISOR, if (ghost) 0.25f else 0.7f))
        }
        if (!ghost) {
            val a = p.alphaMul * (1f - p.flatAmt) * (if (f.w.player.state == PlayerState.DEAD) 0.3f else 1f)
            g.blend(Gfx.Blend.ADD)
            // The neon rim along the back of the helmet's sweep.
            if (look.rim != 0 && p.hi) {
                val i = ActorPaint.RIM_W * 0.5f
                g.line(hpX(-0.5f) + i * dir, hpY(-0.92f) + i, hpX(-0.94f) + i * dir, hpY(-0.64f) + i, ActorPaint.RIM_W, p.c(look.rim))
                g.line(hpX(-0.94f) + i * dir, hpY(-0.64f) + i, hpX(-1.16f) + i * dir, hpY(-0.3f), ActorPaint.RIM_W, p.c(look.rim))
            }
            g.glow(hpX(0.9f), hpY(-0.16f), r * 2.3f, Col.alpha(VISOR, 0.6f * a))
            g.blend(Gfx.Blend.NORMAL)
        }
    }

    // ------------------------------------------------------------ hide, die

    private fun doorHide(x: Float, foot: Float, dir: Int) {
        // Flattened into the doorway: a near-black silhouette, visor glint and a whisper of rim.
        k.setup(dir, foot - 0.045f * HS, HS)
        k.headR *= HEAD
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
            heroTorso()
            heroCollar()
            heroHead(dir, ghost = true)
            body.arm(k.armF, look, false)
        }
        p.flatAmt = 0f
        p.noInk = false
        // The visor's slit glinting out of the dark, blinking now and then.
        val blink = fract(f.t * 0.31f) > 0.96f
        if (!blink) {
            p.begin()
                .add(hpX(0.1f), hpY(-0.26f))
                .add(hpX(1.16f), hpY(-0.26f))
                .add(hpX(1.2f), hpY(-0.14f))
                .add(hpX(1.12f), hpY(-0.02f))
                .add(hpX(0.14f), hpY(-0.06f))
                .shapeDetail(Col.alpha(VISOR, 0.85f))
            f.glowDot(hpX(0.9f), hpY(-0.14f), 0.025f, VISOR, 0.6f)
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
        // Being watched? A guard who's onto you is an alarm; one creeping over to check the
        // box that moved ("HUH?") is a slow, sweaty freeze that gets worse as he closes in.
        var watched = false
        var nervous = 0f
        var lookDir = dir
        val list = f.w.enemies
        for (i in list.indices) {
            val e = list[i]
            if (e.floor != pl.floor || e.hall != pl.hall || !e.alive || e.asleep) continue
            val dx = pl.x - e.x
            val facingBox = (dx > 0) == (e.facing > 0)
            if (!facingBox) continue
            if (e.state == EnemyState.SEARCH) {
                if (abs(dx) < 6f) {
                    val n = 1f - abs(dx) / 6f
                    if (n > nervous) { nervous = n; lookDir = if (dx > 0) -1 else 1 }
                }
            } else if (abs(dx) < 4f && e.state != EnemyState.PATROL) {
                watched = true
                lookDir = if (dx > 0) -1 else 1
                break
            }
        }
        if (watched) nervous = 0f
        if (watched) watchT += if (f.dt > 0f) f.dt else 0.4f else watchT = 0f

        val ph = f.t * 13f
        val bob = if (moving) abs(sin(ph)) * 0.045f else 0f
        val tilt = if (moving) sin(ph) * 3.2f else 0f
        val jolt = if (watched) Rig.backOut(min(1f, watchT / 0.16f)) else 0f
        val hop = if (watched && watchT < 0.3f) sin(watchT / 0.3f * PI.toFloat()) * 0.14f else 0f
        val shake = if (watched) sin(f.t * 70f) * 0.012f else sin(f.t * 55f) * 0.016f * nervous * nervous
        // Idle peek: every few seconds the box lifts and eyes glint underneath.
        val cyc = fract(f.t * 0.19f + 0.3f)
        val peek = if (!moving && !watched && nervous == 0f && cyc > 0.78f && cyc < 0.94f) sin((cyc - 0.78f) / 0.16f * PI.toFloat()) * 0.13f else 0f
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
        val d = 0.09f // depth offset of the receding top/side
        val rs = -dir.toFloat() // the receding side face is on the trailing side
        val fl = 0.06f * sin(f.t * 1.3f)
        p.twoPass {
            // Side face, lid, the two flaps, the front face: each painted like lamp-lit board.
            p.begin().add(hw * rs, -h).add(hw * rs + d * 0.6f * rs, -h - d).add(hw * rs + d * 0.6f * rs, -d * 0.6f).add(hw * rs, 0f)
            p.shape(BoxArt.BOX_SIDE)
            p.shapeGradDetail(BoxArt.BOX_SIDE, ActorPaint.shade(BoxArt.BOX_SIDE), 0f, -h, 0f, 0f)
            p.begin().add(-hw, -h).add(hw, -h).add(hw + d * 0.6f * rs, -h - d).add(-hw + d * 0.6f * rs, -h - d)
            p.shape(BoxArt.BOX_TOP)
            p.begin().add(-hw, -h).add(-hw + 0.34f, -h).add(-hw + 0.24f, -h - 0.13f - fl).add(-hw - 0.08f, -h - 0.1f)
            p.shape(BoxArt.BOX_FLAP)
            p.shapeGradDetail(ActorPaint.light(BoxArt.BOX_FLAP), BoxArt.BOX_FLAP, 0f, -h - 0.12f, 0f, -h)
            p.begin().add(hw, -h).add(hw - 0.34f, -h).add(hw - 0.22f, -h - 0.12f + fl).add(hw + 0.08f, -h - 0.09f)
            p.shape(BoxArt.BOX_FLAP_FAR)
            p.shapeGradDetail(BoxArt.BOX_FLAP_FAR, ActorPaint.shade(BoxArt.BOX_FLAP_FAR), 0f, -h - 0.12f, 0f, -h)
            p.begin().add(-hw, -h).add(hw, -h).add(hw, 0f).add(-hw, 0f)
            p.shape(BoxArt.BOX_FRONT)
            // Warm where the ceiling lamp falls on the upper face, cooling toward the floor.
            p.shapeGradDetail(ActorPaint.light(BoxArt.BOX_FRONT), Col.lerp(BoxArt.BOX_FRONT, ActorPaint.shade(BoxArt.BOX_FRONT), 0.45f), 0f, -h, 0f, 0f)
        }
        if (p.shading) {
            // Cut edges: the pale corrugated core showing along every raw edge of board.
            p.detail(-hw - 0.08f, -h - 0.1f, -hw + 0.24f, -h - 0.13f - fl, 0.02f, BoxArt.BOX_CUT)
            p.detail(hw + 0.08f, -h - 0.09f, hw - 0.22f, -h - 0.12f + fl, 0.018f, Col.lerp(BoxArt.BOX_CUT, BoxArt.BOX_FLAP_FAR, 0.4f))
            // The lid's fold: a crisp shadow line under the lip, a lit crease above it.
            p.detail(-hw + 0.01f, -h + 0.018f, hw - 0.01f, -h + 0.018f, 0.018f, Col.alpha(ActorPaint.shade(BoxArt.BOX_FRONT), 0.8f))
            p.detail(-hw, -h - 0.004f, hw, -h - 0.004f, 0.012f, BoxArt.BOX_CUT)
            // The front corner, rounded over into the side face.
            p.detail(hw * rs - 0.014f * rs, -h + 0.02f, hw * rs - 0.014f * rs, -0.02f, 0.016f, Col.alpha(ActorPaint.light(BoxArt.BOX_FRONT), 0.7f))
            // A soft dent near the leading bottom corner: board that's been bumped around.
            p.begin().add(-rs * 0.3f, -0.1f).add(-rs * 0.46f, -0.2f).add(-rs * 0.44f, -0.05f)
            p.shapeGradDetail(Col.alpha(ActorPaint.shade(BoxArt.BOX_FRONT), 0.0f), Col.alpha(ActorPaint.shade(BoxArt.BOX_FRONT), 0.55f), -rs * 0.3f, -0.1f, -rs * 0.45f, -0.12f)
            // The board bows out a little over the agent inside: a broad soft sheen on the lamp side.
            for (side in 0..1) {
                val edge = if (side == 0) 0.02f else 0.36f
                p.begin().add(-rs * edge, -h + 0.04f).add(-rs * 0.19f, -h + 0.04f).add(-rs * 0.19f, -0.05f).add(-rs * edge, -0.05f)
                p.shapeGradDetail(0x26FFF0DC, 0x00FFF0DC, -rs * 0.19f, 0f, -rs * edge, 0f)
            }
            // Contact: the board darkens where it meets the floor.
            p.begin().add(-hw, -0.16f).add(hw, -0.16f).add(hw, 0f).add(-hw, 0f)
            p.shapeGradDetail(0x00140A18, 0x70140A18, 0f, -0.16f, 0f, 0f)
            // The zone's neon catching the back edge.
            g.blend(Gfx.Blend.ADD)
            val bx = hw * rs + d * 0.6f * rs
            g.line(bx, -h - d + 0.02f, bx, -d * 0.6f - 0.02f, 0.02f, p.c(Col.alpha(RIM, 0.35f)))
            g.blend(Gfx.Blend.NORMAL)
        }
        // Packing tape: over the lid seam and down the front, a glossy strip with a torn end.
        val tw = 0.065f
        val te = -h + 0.3f
        p.begin().add(-tw + d * 0.6f * rs, -h - d).add(tw + d * 0.6f * rs, -h - d).add(tw, -h).add(tw, te).add(tw * 0.5f, te + 0.025f)
            .add(0f, te - 0.004f).add(-tw * 0.5f, te + 0.028f).add(-tw, te).add(-tw, -h)
        p.shapeDetail(BoxArt.BOX_TAPE)
        if (p.shading) {
            p.detail(-tw + 0.012f, -h - d + 0.01f, -tw + 0.012f, te - 0.01f, 0.01f, Col.alpha(ActorPaint.shade(BoxArt.BOX_FRONT), 0.35f))
            g.blend(Gfx.Blend.ADD)
            g.line(tw - 0.02f, -h + 0.02f, tw - 0.02f, te - 0.03f, 0.016f, p.c(0x50FFFFFF))
            g.line(-tw * 0.3f, -h - d + 0.015f, tw * 0.7f, -h - d + 0.015f, 0.012f, p.c(0x40FFFFFF))
            g.blend(Gfx.Blend.NORMAL)
        }
        // "This side up" arrows in a printed frame on the trailing half.
        val px = 0.27f * rs
        Glyphs.arrow(g, px - 0.07f, -0.28f, 0.11f, 0f, -1f, 0.036f, p.c(BoxArt.BOX_PRINT))
        Glyphs.arrow(g, px + 0.07f, -0.28f, 0.11f, 0f, -1f, 0.036f, p.c(BoxArt.BOX_PRINT))
        p.detail(px - 0.14f, -0.13f, px + 0.14f, -0.13f, 0.014f, BoxArt.BOX_PRINT)
        // A red FRAGILE stamp, inked on a slant, on the leading half.
        val stx = -0.25f * rs
        g.save()
        g.translate(stx, -0.22f)
        g.rotate(-7f * dir)
        g.strokeRoundRect(-0.14f, -0.075f, 0.14f, 0.075f, 0.02f, 0.022f, p.c(BoxArt.BOX_STAMP))
        p.detail(-0.085f, -0.018f, 0.085f, -0.018f, 0.028f, BoxArt.BOX_STAMP)
        p.detail(-0.085f, 0.03f, 0.04f, 0.03f, 0.016f, BoxArt.BOX_STAMP)
        g.restore()
        // Handle slot: the peek hole, cut through the board, the agent's cyan eyes glowing in it.
        val hx = -0.24f * rs
        val sy = -h + 0.22f
        if (p.shading) g.fillRoundRect(hx - 0.162f, sy - 0.072f, hx + 0.162f, sy + 0.08f, 0.074f, p.c(Col.alpha(ActorPaint.shade(BoxArt.BOX_FRONT), 0.9f)))
        g.fillRoundRect(hx - 0.15f, sy - 0.065f, hx + 0.15f, sy + 0.065f, 0.065f, p.c(0xFF120806.toInt()))
        if (p.shading) p.detail(hx - 0.11f, sy + 0.068f, hx + 0.11f, sy + 0.068f, 0.014f, BoxArt.BOX_CUT)
        val blink = !watched && nervous == 0f && fract(f.t * 0.37f) > 0.95f
        val look = if (watched || nervous > 0f) lookDir * 0.035f else dir * 0.03f
        if (nervous > 0f) {
            // Wide eyes locked on the guard, pupils shrinking as he gets closer.
            val r = 0.042f
            g.fillCircle(hx - 0.058f + look * 0.4f, sy, r, p.c(0xFFFFFFFF.toInt()))
            g.fillCircle(hx + 0.058f + look * 0.4f, sy, r, p.c(0xFFFFFFFF.toInt()))
            val pr = r * (0.55f - 0.25f * nervous)
            g.fillCircle(hx - 0.058f + look, sy, pr, p.c(0xFF101018.toInt()))
            g.fillCircle(hx + 0.058f + look, sy, pr, p.c(0xFF101018.toInt()))
        } else if (watched) {
            val r = 0.042f * (0.8f + 0.4f * jolt)
            g.fillCircle(hx - 0.058f + look, sy, r, p.c(0xFFFFFFFF.toInt()))
            g.fillCircle(hx + 0.058f + look, sy, r, p.c(0xFFFFFFFF.toInt()))
            g.fillCircle(hx - 0.052f + look, sy, r * 0.45f, p.c(0xFF101018.toInt()))
            g.fillCircle(hx + 0.064f + look, sy, r * 0.45f, p.c(0xFF101018.toInt()))
        } else if (blink) {
            g.line(hx - 0.09f + look, sy, hx - 0.03f + look, sy, 0.02f, p.c(VISOR))
            g.line(hx + 0.03f + look, sy, hx + 0.09f + look, sy, 0.02f, p.c(VISOR))
        } else {
            g.fillCircle(hx - 0.058f + look, sy, 0.036f, p.c(VISOR))
            g.fillCircle(hx + 0.058f + look, sy, 0.036f, p.c(VISOR))
            g.blend(Gfx.Blend.ADD)
            g.glow(hx + look, sy, 0.2f, p.c(Col.alpha(VISOR, 0.6f)))
            g.blend(Gfx.Blend.NORMAL)
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
        } else if (nervous > 0.05f) {
            // One fat bead of sweat rolling down the cardboard, then another.
            val sw = fract(f.t * (0.8f + nervous))
            val sx = x - lookDir * 0.3f
            drop(sx, gy - h - 0.05f + sw * 0.45f, 0.05f + 0.03f * nervous, Col.alpha(0xFFBFEFFF.toInt(), min(1f, nervous * 2f) * (1f - sw * 0.6f)))
            if (nervous > 0.5f) {
                // Stress marks over the lid.
                val a = (nervous - 0.5f) * 2f
                for (k in -1..1) {
                    val bx = x + k * 0.14f
                    g.line(bx - 0.03f * k, gy - h - 0.16f, bx - 0.07f * k, gy - h - 0.32f, 0.035f, Col.alpha(0xFFFFE8C0.toInt(), a))
                }
            }
        }
    }

    /** The cardboard: board tones, the pale corrugated cut edge, tape and printer's inks. */
    private object BoxArt {
        const val BOX_FRONT = 0xFFC48E56.toInt()
        const val BOX_SIDE = 0xFF94643A.toInt()
        const val BOX_TOP = 0xFFDCAA6C.toInt()
        const val BOX_FLAP = 0xFFD09A62.toInt()
        const val BOX_FLAP_FAR = 0xFFB07A46.toInt()
        const val BOX_CUT = 0xFFEED2A0.toInt()
        const val BOX_TAPE = 0xC8E6CC98.toInt()
        const val BOX_PRINT = 0xD04E3018.toInt()
        const val BOX_STAMP = 0xC0B0301E.toInt()
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
        if (pl.state == PlayerState.STASH || pl.state == PlayerState.DEAD || pl.state == PlayerState.PASSAGE) return
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

    fun floorActors(fi: Int, fs: HallState) {
        val gy = Geo.groundY(fi)
        if (!f.visibleY(gy - Geo.FLOOR_H, gy + 0.5f)) return
        val w = f.w
        val hall = fs.plan.hall
        val payday = w.floors[fi]?.plan?.event == com.bradflaugher.aboutthataction.engine.FloorEvent.PAYDAY
        for (pk in w.pickups) if (pk.floor == fi && pk.hall == hall) pickup(pk.kind, pk.x, gy, pk.z, pk.age, pk.life, payday && pk.life > 60f)
        val pl = w.player
        val grappling = pl.state == PlayerState.TAKEDOWN
        val list = w.enemies
        for (i in list.indices) {
            val e = list[i]
            if (e.floor != fi || e.hall != hall) continue
            // The victim of a takedown is drawn inside the player's grapple.
            if (grappling && e.state == EnemyState.CHOKED && e.id == pl.takedownTarget) continue
            cast.enemy(e, i, gy, fs)
        }
        for (gr in w.grenades) if (gr.floor == fi && gr.hall == hall) grenade(gr.x, gy - gr.z, gr.fuse)
    }

    /** In the dark, alive enemies are silhouettes with glowing eyes. */
    fun darkEyes(fi: Int, fs: HallState) = cast.darkEyes(fi, fs)

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

    private fun pickup(kind: PickupKind, x: Float, gy: Float, z: Float, age: Float, life: Float, jackpot: Boolean = false) {
        if (jackpot) jackpotUnder(kind, x, gy)
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
        if (jackpot) jackpotOver(x, cy)
    }

    /**
     * PAYDAY loot: a heap of bills and coins on the floor under each pickup, lit gold, so the
     * hallway reads as a jackpot before you've read a single icon.
     */
    private fun jackpotUnder(kind: PickupKind, x: Float, gy: Float) {
        val gold = 0xFFFFC83A.toInt()
        g.blend(Gfx.Blend.ADD)
        val pulse = 0.85f + 0.15f * sin(f.t * 3f + x)
        g.glow(x, gy - 0.5f, 1.2f, Col.alpha(gold, 0.26f * pulse))
        g.save()
        g.translate(x, gy - 0.02f)
        g.scale(1f, 0.18f)
        g.glow(0f, 0f, 1.1f, Col.alpha(gold, 0.6f * pulse))
        g.restore()
        g.blend(Gfx.Blend.NORMAL)
        // Bill stacks (cash) or a spill of coins (the bonus).
        val seed = (x * 13f).toInt()
        if (kind == PickupKind.CASH) {
            for (i in 0 until 3) {
                val bx = x + (i - 1) * 0.2f + (hash(seed, i) - 0.5f) * 0.06f
                val h = 0.1f + 0.05f * ((i + 1) % 3)
                g.fillRect(bx - 0.11f - ActorPaint.OUT, gy - h - ActorPaint.OUT, bx + 0.11f + ActorPaint.OUT, gy, ActorPaint.INK)
                g.fillRect(bx - 0.11f, gy - h, bx + 0.11f, gy, 0xFF2E9A58.toInt())
                g.fillRect(bx - 0.11f, gy - h, bx + 0.11f, gy - h + 0.025f, 0xFF7CF0A8.toInt())
                g.fillRect(bx - 0.025f, gy - h, bx + 0.025f, gy, 0xFFE8D8A0.toInt())
            }
        } else {
            for (i in 0 until 5) {
                val cx = x + (i - 2) * 0.13f
                val cy = gy - 0.04f - (if (i % 2 == 0) 0f else 0.06f)
                g.fillCircle(cx, cy, 0.07f + ActorPaint.OUT * 0.7f, ActorPaint.INK)
                g.fillCircle(cx, cy, 0.07f, 0xFFE8A824.toInt())
                g.fillCircle(cx - 0.02f, cy - 0.02f, 0.03f, 0xFFFFE890.toInt())
            }
        }
    }

    /** Cash glints: four-point sparkles popping around the loot, and a lazy "$" floating up. */
    private fun jackpotOver(x: Float, cy: Float) {
        val seed = (x * 7f).toInt()
        g.blend(Gfx.Blend.ADD)
        for (i in 0 until 3) {
            val ph = fract(f.t * 0.9f + i / 3f + hash(seed, i + 40))
            val s = sin(ph * PI.toFloat()) * 0.16f
            if (s < 0.02f) continue
            val sx = x + (hash(seed + (f.t * 0.9f + i / 3f).toInt(), i) - 0.5f) * 0.8f
            val sy = cy - 0.1f + (hash(seed + i, 9) - 0.5f) * 0.7f
            g.glow(sx, sy, s * 2.2f, Col.alpha(0xFFFFE070.toInt(), 0.3f))
            poly.begin().add(sx, sy - s).add(sx + s * 0.2f, sy - s * 0.2f).add(sx + s, sy).add(sx + s * 0.2f, sy + s * 0.2f)
                .add(sx, sy + s).add(sx - s * 0.2f, sy + s * 0.2f).add(sx - s, sy).add(sx - s * 0.2f, sy - s * 0.2f).fill(g, 0xFFFFFAE0.toInt())
        }
        g.blend(Gfx.Blend.NORMAL)
        val ph = fract(f.t * 0.45f + hash(seed, 3))
        val a = min(1f, ph / 0.15f) * (1f - ph)
        f.worldText("$", x + 0.3f + sin(ph * 6f) * 0.08f, cy - 0.35f - ph * 0.9f, 0.26f, Col.alpha(0xFFFFD24A.toInt(), a), Gfx.Font.TITLE)
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
