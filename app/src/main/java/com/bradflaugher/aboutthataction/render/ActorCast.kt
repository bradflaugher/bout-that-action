package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Body
import com.bradflaugher.aboutthataction.engine.Enemy
import com.bradflaugher.aboutthataction.engine.EnemyKind
import com.bradflaugher.aboutthataction.engine.EnemyState
import com.bradflaugher.aboutthataction.engine.HallState
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.Heat
import com.bradflaugher.aboutthataction.engine.KillMethod
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** How a body goes down. */
internal enum class CastDeath { KNOCK, SQUASH, FLING, CRUMPLE }

/**
 * The opposition: one silhouette per archetype, reskinned per zone, and the
 * telegraphs that make them fair (aim lasers at the exact lane, crouches for
 * low shots, the melee windup, "!" and "?").
 */
internal class ActorCast(
    private val f: Frame,
    private val p: ActorPaint,
    private val k: Rig,
    private val body: ActorBody,
) {
    private val g get() = f.g
    private val look = Look()

    private companion object {
        /** Status bubble body height and the tail below it, per unit of pop. */
        const val BUBBLE_H = 0.52f
        const val BUBBLE_TAIL = 0.08f
        /** The "!" / "?" inside it: 0.36 of 0.52 tall, so 0.08 of breathing room above and below. */
        const val GLYPH_H = 0.36f
        /** Drones are drawn a size up so they read at the zoomed-out camera. */
        const val DRONE_S = 1.15f
        /** The wake-up leap lasts this long. */
        const val WAKE_TIME = 0.5f
        /** The "HUH?" head-snap lasts this long; then he creeps in. */
        const val DOUBLE_TAKE = 0.45f
    }

    /** Where each enemy's eyes were drawn this frame (by list index), for the darkness pass. */
    private var eyeX = FloatArray(32)
    private var eyeY = FloatArray(32)
    private var eyeOk = BooleanArray(32)
    private var eyeWX = 0f
    private var eyeWY = 0f

    // Weapon pose chosen by the posers.
    private var gunX = 0f
    private var gunY = 0f
    private var gunUp = 0f
    private var showGun = true
    private var bladeA = 0f

    // ================================================================ entry

    fun enemy(e: Enemy, idx: Int, gy: Float, fs: HallState) {
        ensureEyes(idx)
        eyeOk[idx] = false
        p.reset()
        p.hi = f.lod(e.floor) == 0
        val dir = if (e.facing >= 0) 1 else -1
        val zone = zoneOf(fs)
        val pal = f.palette(fs)
        if (e.state == EnemyState.EMERGING) {
            // Stepping out of the dark doorway: from silhouette to full colour.
            p.flat = 0xFF030308.toInt()
            p.flatAmt = 1f - (e.stateTime / 0.45f).coerceIn(0f, 1f)
        }
        var recoilX = 0f
        if (e.hurtFlash > 0f && e.state != EnemyState.DEAD) {
            val h = (e.hurtFlash / 0.12f).coerceIn(0f, 1f)
            p.flat = 0xFFFFFFFF.toInt()
            p.flatAmt = 0.55f + 0.45f * h
            recoilX = -dir * 0.07f * h
        }
        val humanoid = e.kind != EnemyKind.DRONE && e.kind != EnemyKind.TURRET
        if (humanoid && e.state != EnemyState.DEAD) {
            p.contactShadow(e.x, gy, e.halfWidth + 0.08f, e.z)
        } else if (e.kind == EnemyKind.DRONE && e.state != EnemyState.DEAD) {
            p.contactShadow(e.x, gy, 0.3f, e.z * 0.6f)
        }
        if (fs.plan.isVoid && hash((f.t * 10f).toInt(), e.id) > 0.85f) {
            // Glitch: RGB-split echoes.
            val keepA = p.alphaMul
            val keepAmt = p.flatAmt
            val keepFlat = p.flat
            p.noInk = true
            p.alphaMul = keepA * 0.5f
            p.flat = 0xFF2BFFE0.toInt(); p.flatAmt = 1f
            enemyBody(e, e.x + 0.08f, gy, dir, zone, pal, fs)
            p.flat = 0xFFFF2BD6.toInt()
            enemyBody(e, e.x - 0.08f, gy, dir, zone, pal, fs)
            p.noInk = false
            p.alphaMul = keepA; p.flatAmt = keepAmt; p.flat = keepFlat
        }
        if (e.state != EnemyState.DEAD && e.kind != EnemyKind.TURRET) backlight(e, gy, pal)
        if (e.state == EnemyState.AIM) aimTelegraph(e, gy, dir, fs)
        if (e.state == EnemyState.WINDUP) windupTelegraph(e, gy, dir)
        enemyBody(e, e.x + recoilX, gy, dir, zone, pal, fs)
        val napping = e.asleep && humanoid && e.alive
        val zx = k.headX
        val zy = k.headY
        val zr = k.headR
        if (e.alive && humanoid && e.state != EnemyState.DEAD) {
            eyeX[idx] = eyeWX
            eyeY[idx] = eyeWY
            eyeOk[idx] = true
        }
        p.reset()
        if (napping) f.moments.zzz(e, zx, zy, dir, zr, 1f - f.recede(e.floor) * 0.6f)
        if (e.alive && !napping) {
            statusMarks(e, gy)
            if (e.maxHp > 1 && e.hp < e.maxHp) hpPips(e, e.x, gy - e.z - e.height - 0.22f)
        }
    }

    /** Soft additive back-light so every silhouette lifts off the wall behind it. */
    private fun backlight(e: Enemy, gy: Float, pal: Palette) {
        val h = e.height
        val c = Col.lerp(pal.lamp, pal.neon, 0.35f)
        val a = 0.24f * p.alphaMul * (1f - p.flatAmt * 0.7f)
        g.blend(Gfx.Blend.ADD)
        if (e.kind == EnemyKind.DRONE) {
            g.glow(e.x, gy - e.z - 0.2f, 0.7f, Col.alpha(c, a))
        } else {
            g.glow(e.x, gy - e.z - h * 0.52f, h * 0.68f, Col.alpha(c, a))
        }
        g.blend(Gfx.Blend.NORMAL)
    }

    /** Rim light colour for a zone: the neon, lifted toward white. */
    private fun rimOf(pal: Palette, a: Float) = Col.alpha(Col.lerp(pal.neon, 0xFFFFFFFF.toInt(), 0.3f), a)

    private fun ensureEyes(idx: Int) {
        if (idx < eyeX.size) return
        val n = max(idx + 1, eyeX.size * 2)
        eyeX = eyeX.copyOf(n)
        eyeY = eyeY.copyOf(n)
        eyeOk = eyeOk.copyOf(n)
    }

    private fun zoneOf(fs: HallState) = if (fs.plan.index == 0) Zone.TOWER else fs.plan.zone

    private fun scaleOf(kind: EnemyKind) = when (kind) {
        EnemyKind.HEAVY -> 1.14f
        EnemyKind.DEMON -> 1.1f
        EnemyKind.NINJA -> 0.97f
        else -> 1f
    }

    private fun bulkOf(kind: EnemyKind) = when (kind) {
        EnemyKind.HEAVY -> 1.5f
        EnemyKind.DEMON -> 1.2f
        EnemyKind.NINJA -> 0.9f
        else -> 1f
    }

    private fun enemyBody(e: Enemy, x: Float, gy: Float, dir: Int, zone: Zone, pal: Palette, fs: HallState) {
        when (e.kind) {
            EnemyKind.DRONE -> drone(e, x, gy, dir, pal)
            EnemyKind.TURRET -> turret(e, x, gy, pal)
            else -> {
                if (e.state == EnemyState.DEAD) {
                    val t = e.stateTime
                    val keepA = p.alphaMul
                    val keepF = p.flatAmt
                    val keepC = p.flat
                    val mode = when (e.killedBy) {
                        KillMethod.STOMP, KillMethod.LIGHT -> CastDeath.SQUASH
                        KillMethod.EXPLOSION -> CastDeath.FLING
                        KillMethod.TAKEDOWN -> CastDeath.CRUMPLE
                        else -> CastDeath.KNOCK
                    }
                    p.flat = 0xFF000000.toInt()
                    p.flatAmt = if (mode == CastDeath.FLING) min(0.65f, t * 1.5f) else min(0.45f, t * 0.6f)
                    if (t > 1.05f) {
                        p.alphaMul = keepA * max(0f, 1f - (t - 1.05f) / 0.5f)
                        p.noInk = true
                    }
                    val fall = when (mode) {
                        CastDeath.CRUMPLE -> dir
                        else -> if (e.deathVx > 0.01f) 1 else if (e.deathVx < -0.01f) -1 else -dir
                    }
                    val lively = ragdollBegin(x, gy, e.z, dir, scaleOf(e.kind), bulkOf(e.kind), fall, t, mode)
                    drawKind(e, dir, zone, pal, lively)
                    g.restore()
                    p.alphaMul = keepA; p.flatAmt = keepF; p.flat = keepC; p.noInk = false
                } else {
                    poseEnemy(e, x, gy - e.z, dir)
                    drawKind(e, dir, zone, pal, true)
                }
            }
        }
    }

    private fun drawKind(e: Enemy, dir: Int, zone: Zone, pal: Palette, armed: Boolean) {
        when (e.kind) {
            EnemyKind.HEAVY -> heavy(e, dir, zone, pal, armed)
            EnemyKind.NINJA -> ninja(e, dir, zone, pal, armed)
            EnemyKind.DEMON -> demon(e, dir, zone, pal)
            else -> guard(e, dir, zone, pal, armed)
        }
    }

    // ============================================================== posing

    private fun poseEnemy(e: Enemy, x: Float, foot: Float, dir: Int) {
        val hs = scaleOf(e.kind)
        k.setup(dir, foot - 0.045f * hs, hs, bulkOf(e.kind))
        if (e.kind == EnemyKind.DEMON) {
            k.armF.len1 *= 1.15f; k.armF.len2 *= 1.2f
            k.armB.len1 *= 1.15f; k.armB.len2 *= 1.2f
        }
        showGun = e.kind == EnemyKind.AGENT || e.kind == EnemyKind.HEAVY
        gunUp = -0.8f
        bladeA = 0f
        val speed = abs(e.vx)
        val moving = speed > 0.1f
        val u = e.walkPhase * 1.6f / Rig.TAU
        val runBlend = ((speed - 1.2f) / 1.6f).coerceIn(0f, 1f)
        val breathe = sin(f.t * 2.1f + e.id * 1.7f)
        val ninja = e.kind == EnemyKind.NINJA
        val demon = e.kind == EnemyKind.DEMON
        val heavy = e.kind == EnemyKind.HEAVY
        val hunch = if (demon) 0.55f else 0f
        if (e.asleep && e.state != EnemyState.CHOKED && e.state != EnemyState.DEAD) {
            sleepPose(e, x, dir)
            return
        }
        val woke = f.moments.wokeAge(e)
        if (woke >= 0f && woke < WAKE_TIME) {
            wakePose(e, x, dir, woke)
            return
        }
        val kick = f.moments.kickAge(e)
        if (kick >= 0f) {
            kickPose(e, x, dir, kick)
            return
        }
        when (e.state) {
            EnemyState.AIM -> aimPose(e, x, foot, dir)
            EnemyState.WINDUP -> windupPose(e, x, dir)
            EnemyState.CHOKED -> chokePose(e, x, dir, e.x - dir * 0.45f, foot - 1.25f, e.stateTime)
            EnemyState.STUNNED -> {
                val sway = sin(f.t * 3.2f + e.id)
                k.stand(x, 0.08f * hs + abs(sway) * 0.02f, 0.12f, -0.12f)
                k.spine(0.15f + sway * 0.12f + hunch, 0.45f)
                k.armFK(k.armF, -0.05f + sway * 0.1f, 0.15f)
                k.armFK(k.armB, -0.15f - sway * 0.1f, 0.2f)
                holdLow(-1.3f)
            }
            else -> {
                val alertRun = e.state == EnemyState.ALERT && moving
                val startle = e.state == EnemyState.ALERT && e.stateTime < 0.22f && !ninja && !demon
                if (moving) {
                    val crouch = if (ninja && alertRun) 0.45f else if (demon) 0.25f else 0f
                    k.locomote(x, u, if (ninja || demon) max(runBlend, if (alertRun) 1f else 0f) else runBlend, crouch)
                    val lean = when {
                        ninja && alertRun -> 0.85f
                        demon -> 0.6f + runBlend * 0.25f
                        else -> 0.06f + runBlend * 0.22f
                    }
                    k.spine(lean, if (ninja && alertRun) -0.4f else if (demon) -0.2f else 0f)
                    k.swingArms(u, if (ninja || demon) max(runBlend, 0.6f) else runBlend, if (demon) 1.3f else 1f)
                } else {
                    val drop = if (demon) 0.14f else if (ninja) 0.05f else 0.03f
                    k.stand(x, drop * hs + breathe * 0.006f, if (heavy) 0.16f else 0.1f, if (heavy) -0.18f else -0.13f)
                    k.spine(0.04f + hunch + breathe * 0.012f, if (demon) -0.2f else 0f)
                    k.armFK(k.armF, 0.1f + breathe * 0.02f, 0.35f)
                    k.armFK(k.armB, -0.1f - breathe * 0.02f, 0.3f)
                }
                if (startle) {
                    // The jolt of spotting you: hop, lean back, gun snapping up.
                    val s = sin(e.stateTime / 0.22f * PI.toFloat())
                    k.shiftAll(-dir * 0.05f * s, -0.08f * s)
                    k.spine(k.lean - 0.25f * s, -0.2f * s)
                    k.ik(k.armB, k.neckX - 0.05f * dir, k.neckY - 0.1f * s, false)
                }
                when {
                    heavy -> holdCannon(0f)
                    ninja -> {
                        bladeA = if (alertRun) -2.3f else -1.0f
                        if (alertRun) k.armFK(k.armF, -0.9f, 0.4f)
                    }
                    demon -> Unit
                    e.state == EnemyState.ALERT || e.state == EnemyState.SEARCH -> {
                        // Gun up and forward; sweeping while searching.
                        val sweep = if (e.state == EnemyState.SEARCH) sin(f.t * 2.2f + e.id) * 0.25f else 0f
                        gunX = k.neckX + 0.36f * dir
                        gunY = k.neckY + 0.3f
                        gunUp = sweep
                        k.ik(k.armF, gunX, gunY, false)
                        k.ik(k.armB, gunX + 0.01f * dir, gunY + 0.035f, false)
                        if (e.state == EnemyState.SEARCH) k.spine(k.lean, -sweep * 0.5f)
                    }
                    else -> holdLow(-0.95f)
                }
                if (e.state == EnemyState.SEARCH && boxWatch(e)) doubleTake(e, dir)
            }
        }
    }

    /** Is [e] a guard coming over to check out the player's box (the MGS double-take)? */
    private fun boxWatch(e: Enemy): Boolean {
        val pl = f.w.player
        if (pl.state != com.bradflaugher.aboutthataction.engine.PlayerState.BOX) return false
        if (pl.floor != e.floor || pl.hall != e.hall) return false
        val dx = pl.x - e.x
        return abs(dx) < 7f && (dx > 0f) == (e.facing > 0)
    }

    /**
     * "HUH?": the head snaps to the box and back and he rocks onto his heels; then he creeps
     * in, craning his neck at it, gun forgotten at his side.
     */
    private fun doubleTake(e: Enemy, dir: Int) {
        val t = e.stateTime
        if (t < DOUBLE_TAKE) {
            val q = t / DOUBLE_TAKE
            val snap = sin(q * PI.toFloat() * 3f) * (1f - q)
            k.shiftAll(-dir * 0.06f * sin(q * PI.toFloat()), -0.05f * sin(q * PI.toFloat()))
            k.spine(k.lean - 0.2f * (1f - q), 0.5f * snap - 0.15f)
        } else {
            val bob = sin(f.t * 5f + e.id) * 0.04f
            k.spine(0.32f + bob, 0.28f)
        }
        // The spine moved: hang the arms back on it.
        when (e.kind) {
            EnemyKind.HEAVY -> holdCannon(0f)
            EnemyKind.AGENT -> {
                k.armFK(k.armB, -0.15f, 0.3f)
                holdLow(-1.2f)
            }
            else -> {
                k.armFK(k.armF, 0.15f, 0.4f)
                k.armFK(k.armB, -0.15f, 0.3f)
            }
        }
    }

    /**
     * Dozing at his post: sat on the floor, one knee up with an arm draped over it, chin
     * sinking to his chest and jerking back up. The gun (or blade) rests in his lap.
     */
    private fun sleepPose(e: Enemy, x: Float, dir: Int) {
        val hs = k.hs
        val br = sin(f.t * 1.7f + e.id)
        val cyc = fract(f.t * 0.21f + e.id * 0.37f)
        // The nod: the head sinks slowly, then snaps back up with a start.
        val sink = if (cyc < 0.88f) Rig.smooth(cyc / 0.88f) else 1f - Rig.easeOut((cyc - 0.88f) / 0.12f)
        val hx = x - 0.1f * dir * hs
        k.hip(hx, k.ground - 0.15f * hs)
        k.ik(k.legF, hx + 0.36f * dir * hs, k.ground, true)
        k.ik(k.legB, hx + 0.64f * dir * hs, k.ground, true)
        k.legF.pitch = -0.1f
        k.legB.pitch = -0.6f
        val slump = if (e.kind == EnemyKind.DEMON) 0.55f else 0.3f
        k.spine(slump + br * 0.03f + 0.1f * sink, 0.25f + 0.6f * sink)
        k.ik(k.armF, k.legF.jx + 0.06f * dir * hs, k.legF.jy + 0.06f * hs, false)
        k.ik(k.armB, hx + 0.24f * dir * hs, k.hipY - 0.05f * hs, false)
        gunX = k.armF.ex
        gunY = k.armF.ey
        gunUp = -1.35f
        if (e.kind == EnemyKind.HEAVY) {
            // The cannon across his lap, both hands on it.
            gunX = hx + 0.08f * dir * hs
            gunY = k.hipY - 0.1f * hs
            gunUp = 0.1f
            k.ik(k.armF, gunX, gunY, false)
            k.ik(k.armB, gunX + 0.24f * dir, gunY - 0.04f, false)
        }
        bladeA = -0.1f
    }

    /**
     * Rudely awoken: he leaps off the floor, limbs everywhere, and lands facing you with his
     * hair (hat, hood) standing on end.
     */
    private fun wakePose(e: Enemy, x: Float, dir: Int, a: Float) {
        val q = (a / WAKE_TIME).coerceIn(0f, 1f)
        val hop = sin(q * PI.toFloat()) * 0.42f
        val flail = sin(f.t * 38f + e.id)
        k.stand(x, 0.02f * k.hs, 0.16f, -0.18f)
        k.ik(k.legF, x + (0.18f + 0.1f * flail) * dir * k.hs, k.ground - 0.12f * (1f - q), true)
        k.ik(k.legB, x - (0.2f - 0.08f * flail) * dir * k.hs, k.ground - 0.18f * (1f - q), true)
        k.spine(-0.3f * (1f - q), -0.35f * (1f - q))
        k.armFK(k.armF, 2.5f + 0.35f * flail, 0.5f)
        k.armFK(k.armB, 2.2f - 0.35f * flail, 0.6f)
        k.shiftAll(0f, -hop)
        gunX = k.armF.ex
        gunY = k.armF.ey
        gunUp = 1.2f + flail * 0.4f
        bladeA = 1.4f
    }

    /** The box kick: plant, front boot high and out, arms thrown back for balance. */
    private fun kickPose(e: Enemy, x: Float, dir: Int, a: Float) {
        val hs = k.hs
        val q = a / Moments.KICK_POSE
        val ext = if (q < 0.35f) Rig.easeOut(q / 0.35f) else 1f - Rig.smooth((q - 0.35f) / 0.65f)
        k.stand(x, 0.05f * hs, 0.1f, -0.16f)
        k.ik(k.legB, x - 0.16f * dir * hs, k.ground, true)
        val fx = x + (0.2f + 0.45f * ext) * dir * hs
        val fy = k.ground - (0.05f + 0.42f * ext) * hs
        k.ik(k.legF, fx, fy, true)
        k.legF.pitch = -0.6f * ext
        k.spine(-0.28f * ext, 0.1f)
        k.armFK(k.armF, -0.9f * ext + 0.1f, 0.4f)
        k.armFK(k.armB, 0.9f * ext, 0.5f)
        if (e.kind == EnemyKind.HEAVY) {
            gunX = k.hipX - 0.05f * dir
            gunY = k.hipY - 0.2f * hs
            gunUp = 0.9f * ext
            k.ik(k.armF, gunX, gunY, false)
            k.ik(k.armB, gunX + 0.22f * dir, gunY - 0.12f, false)
        } else {
            bladeA = -2.2f
        }
    }

    /** Pistol at low ready in the near hand. */
    private fun holdLow(up: Float) {
        gunX = k.hipX + 0.16f * k.dir * k.hs + (k.armF.ex - k.hipX) * 0.2f
        gunY = k.hipY + 0.04f * k.hs
        gunUp = up
        k.ik(k.armF, gunX, gunY, false)
    }

    /** The heavy's cannon at the hip, both hands on it. */
    private fun holdCannon(up: Float) {
        gunX = k.hipX + 0.1f * k.dir
        gunY = k.hipY - 0.12f * k.hs
        gunUp = up
        k.ik(k.armF, gunX, gunY, false)
        k.ik(k.armB, gunX + 0.26f * k.dir, gunY - 0.1f, false)
    }

    private fun aimPose(e: Enemy, x: Float, foot: Float, dir: Int) {
        val hs = k.hs
        val fs = f.w.floors[e.floor]
        val aimT = Heat.aimTime(fs?.plan?.heat ?: 1f)
        val raise = Rig.easeOut((e.stateTime / min(0.12f, aimT * 0.3f)).coerceIn(0f, 1f))
        val mx = e.x + dir * (e.halfWidth + 0.2f)
        when (e.kind) {
            EnemyKind.DEMON -> {
                // Fireball cupped at the exact launch point.
                k.stand(x, 0.12f * hs, 0.2f, -0.2f)
                k.spine(0.35f, -0.25f)
                k.ik(k.armF, mx - 0.06f * dir, foot - 1.3f, false)
                k.ik(k.armB, k.neckX - 0.2f * dir, k.neckY - 0.2f, false)
                return
            }
            EnemyKind.NINJA -> {
                k.stand(x, 0.12f, 0.2f, -0.2f)
                k.spine(0.3f)
                k.armFK(k.armF, 0.8f, 0.4f)
                k.armFK(k.armB, -0.3f, 0.4f)
                bladeA = 0.3f
                return
            }
            else -> Unit
        }
        val laserZ = if (e.ducking) Body.LOW else Body.HIGH
        val my = foot - laserZ
        if (e.ducking) {
            // Drop into the kneel fast, but not in a single frame.
            val d = Rig.easeOut(e.stateTime / 0.09f)
            val gnd = foot - 0.045f * hs
            val legLen = k.legF.len1 + k.legF.len2
            k.hip(x, gnd - Rig.mix(legLen * 0.95f, 0.42f * hs, d))
            k.ik(k.legF, x + Rig.mix(0.1f, 0.24f, d) * dir * hs, gnd, true)
            k.ik(k.legB, x + Rig.mix(-0.13f, -0.44f, d) * dir * hs, gnd - 0.08f * d, true)
            k.legB.pitch = 1.3f * d
            k.spine(0.5f * d, 0.2f * d)
        } else {
            k.stand(x, 0.05f * hs, 0.18f, -0.2f)
            k.spine(-0.03f, 0.05f)
        }
        if (e.kind == EnemyKind.HEAVY) {
            val sc = 0.85f
            val hx = mx - 0.62f * sc * dir
            val hy = my + 0.02f * sc
            gunX = Rig.mix(k.hipX + 0.1f * dir, hx, raise)
            gunY = Rig.mix(k.hipY - 0.1f, hy, raise)
            gunUp = 0f
            k.ik(k.armF, gunX, gunY, false)
            k.ik(k.armB, gunX + 0.26f * dir, gunY - 0.1f, false)
        } else {
            val hx = mx - 0.21f * dir
            val hy = my + 0.055f
            gunX = Rig.mix(k.hipX + 0.16f * dir, hx, raise)
            gunY = Rig.mix(k.hipY + 0.04f, hy, raise)
            gunUp = Rig.mix(-0.9f, 0f, raise)
            k.ik(k.armF, gunX, gunY, false)
            k.ik(k.armB, gunX + 0.015f * dir, gunY + 0.035f, false)
        }
    }

    private fun windupPose(e: Enemy, x: Float, dir: Int) {
        val q = Rig.smooth(e.stateTime / 0.3f)
        val hs = k.hs
        if (e.kind == EnemyKind.DEMON) {
            // Rear up, claw high behind the head.
            k.stand(x, (0.14f - 0.06f * q) * hs, 0.22f, -0.2f)
            k.spine(0.4f - 0.55f * q, -0.3f * q)
            k.ik(k.armF, k.neckX - (0.05f + 0.15f * q) * dir, k.neckY - 0.25f - 0.3f * q, false)
            k.ik(k.armB, k.neckX + 0.35f * dir, k.neckY + 0.15f, false)
        } else {
            // Coil low, blade drawn back over the shoulder.
            k.stand(x, (0.1f + 0.1f * q) * hs, 0.28f, -0.26f)
            k.spine(0.2f + 0.2f * q, 0.1f)
            k.ik(k.armF, k.neckX - 0.08f * dir, k.neckY - 0.1f - 0.12f * q, false)
            k.ik(k.armB, k.neckX + 0.3f * dir, k.neckY + 0.2f, false)
            bladeA = -2.5f - 0.5f * q
        }
    }

    /** Held from behind: leaning back, feet scrabbling, hands clawing at the arm on the throat. */
    private fun chokePose(e: Enemy, x: Float, dir: Int, heroX: Float, heroNeckY: Float, t: Float) {
        val q = (t / 0.36f).coerceIn(0f, 1f)
        val hs = k.hs
        val lift = 0.07f * q
        k.stand(x, 0.0f - lift, 0.2f, 0.02f)
        // Feet kicking out in front, heels dragging.
        val kick = sin(f.t * 24f + e.id) * 0.1f
        k.ik(k.legF, x + (0.3f + kick) * dir * hs, k.ground - lift - 0.05f - abs(kick) * 0.6f, true)
        k.ik(k.legB, x + 0.08f * dir * hs, k.ground - lift * 0.5f, true)
        k.legF.pitch = -0.4f
        k.legB.pitch = 0.3f
        // Bent back over the agent's hip, head pulled into the crook of the arm.
        k.spine(-0.6f - 0.12f * q, -0.35f)
        val tx = k.neckX + 0.07f * dir
        val ty = k.neckY + 0.04f
        k.ik(k.armF, tx + 0.06f * dir, ty + sin(f.t * 30f) * 0.02f, false)
        k.ik(k.armB, tx + 0.0f * dir, ty - 0.04f, false)
        showGun = false
        bladeA = 99f
    }

    /** Called by the player's grapple so the victim sits between the agent's body and choking arm. */
    fun chokedVictim(e: Enemy, gy: Float, heroNeckX: Float, heroNeckY: Float, t: Float) {
        p.reset()
        val fs = f.w.hall(e.floor, e.hall) ?: return
        val dir = if (e.facing >= 0) 1 else -1
        val grab = Rig.easeOut(min(1f, t / 0.08f))
        val x = e.x - 0.1f * grab * dir
        val hs = scaleOf(e.kind)
        k.setup(dir, gy - e.z - 0.045f * hs, hs, bulkOf(e.kind))
        chokePose(e, x, dir, heroNeckX, heroNeckY, t)
        // Pull the head back into the agent's chest.
        drawKind(e, dir, zoneOf(fs), f.palette(fs), false)
        p.reset()
    }

    // ============================================================= ragdolls

    /**
     * Poses the rig as a falling body and pushes a transform (caller restores).
     * Returns true while the body still holds its weapon.
     */
    fun ragdollBegin(x: Float, gy: Float, z: Float, dir: Int, hs: Float, bulk: Float, fall: Int, t: Float, mode: CastDeath): Boolean {
        g.save()
        showGun = false
        bladeA = 99f
        when (mode) {
            CastDeath.SQUASH -> {
                // Crushed straight down onto the knees with a springy squash, then pitches face-first.
                val q = Rig.backOut(t / 0.09f)
                val relax = Rig.smooth((t - 0.25f) / 0.2f)
                val sy = 1f - 0.4f * q * (1f - relax)
                val sx = 1f + 0.18f * q * (1f - relax)
                g.translate(x, gy - z)
                g.scale(sx, sy)
                g.translate(-x, -(gy - z))
                return ragdollBegin2(x, gy, z, dir, hs, bulk, dir, 0.2f + max(0f, t - 0.25f), CastDeath.CRUMPLE)
            }
            else -> Unit
        }
        return ragdollBegin2(x, gy, z, dir, hs, bulk, fall, t, mode)
    }

    private fun ragdollBegin2(x: Float, gy: Float, z: Float, dir: Int, hs: Float, bulk: Float, fall: Int, t: Float, mode: CastDeath): Boolean {
        val fr = (fall * dir).toFloat()
        val dur = when (mode) {
            CastDeath.FLING -> 0.75f
            CastDeath.CRUMPLE -> 0.5f
            else -> 0.42f
        }
        val kneel = Rig.smooth(t / 0.2f)
        val u = if (mode == CastDeath.CRUMPLE) Rig.smooth((t - 0.15f) / 0.4f) else Rig.smooth(t / dur)
        val ang = when (mode) {
            CastDeath.FLING -> min(t * 600f, 450f) * fall
            else -> u * 88f * fall
        }
        // A small settling bounce once the body hits the floor.
        val b = ((t - dur) / 0.3f).coerceIn(0f, 1f)
        val bounce = if (mode == CastDeath.KNOCK && t > dur) 0.06f * sin(b * PI.toFloat()) * (1f - b) else 0f
        val hipH = (when (mode) {
            // To the knees, then face down.
            CastDeath.CRUMPLE -> Rig.mix(Rig.mix(0.7f, 0.45f, kneel), 0.15f, u)
            else -> Rig.mix(0.7f, 0.15f, u)
        } + bounce) * hs
        g.translate(x, gy - z - hipH)
        g.rotate(ang)
        k.setup(dir, 10f, hs, bulk)
        k.hip(0f, 0f)
        // Limb lag: flying limbs trail the fall, then settle flat.
        val spread = if (mode == CastDeath.FLING) 1f else 0f
        val lyF = -fr * 0.35f
        // Lying: one knee drawn up, the other leg out; asymmetric so it doesn't read as a plank.
        k.legFK(k.legF, Rig.mix(-fr * 0.8f + spread * 0.5f, -fr * 0.62f, u), Rig.mix(0.7f, 1.15f, u))
        k.legFK(k.legB, Rig.mix(-fr * 0.3f - spread * 0.6f, -fr * 0.1f, u), Rig.mix(1.0f, 0.25f, u))
        k.legF.pitch = 0.4f; k.legB.pitch = 0.5f
        if (mode == CastDeath.CRUMPLE) {
            k.legFK(k.legF, 0.25f * (1f - u) + lyF * u, 1.65f * kneel * (1f - u) + 0.35f * u)
            k.legFK(k.legB, 0.05f * (1f - u), 1.5f * kneel * (1f - u) + 0.2f * u)
            k.legF.pitch = 1.4f * (1f - u); k.legB.pitch = 1.4f * (1f - u)
        }
        if (mode == CastDeath.CRUMPLE) k.spine(0.35f * kneel * (1f - u), 0.6f * kneel)
        else k.spine(Rig.mix(fr * 0.3f, -fr * 0.08f, u), Rig.mix(fr * 0.4f, -fr * 0.4f, u))
        val flop = bounce * 3f
        k.armFK(k.armF, Rig.mix(-fr * 2.3f + spread, -fr * (2.75f + flop), u), Rig.mix(0.3f, 0.95f, u))
        k.armFK(k.armB, Rig.mix(-fr * 1.5f - spread, -fr * (0.55f + flop), u), Rig.mix(0.6f, 0.6f, u))
        if (mode == CastDeath.CRUMPLE) {
            // Arms go limp, then flop out ahead.
            k.armFK(k.armF, Rig.mix(0.15f, 2.6f, u), 0.3f)
            k.armFK(k.armB, Rig.mix(-0.1f, 2.2f, u), 0.4f)
        }
        return false
    }

    /** Public ragdoll for the agent. */
    fun ragdoll(x: Float, gy: Float, z: Float, dir: Int, hs: Float, fall: Int, t: Float, dur: Float, mode: CastDeath, look: Look, head: (Int) -> Unit) {
        ragdollBegin(x, gy, z, dir, hs, 1f, fall, t * 0.42f / dur, mode)
        p.twoPass {
            body.arm(k.armB, look, true)
            body.leg(k.legB, look, true)
            body.leg(k.legF, look, false)
            body.torso(look)
            body.neck(look.armsFar)
        }
        head(dir)
        p.twoPass { body.arm(k.armF, look, false) }
        g.restore()
    }

    // ============================================================ costumes

    private fun eyesAt(x: Float, y: Float) {
        eyeWX = x
        eyeWY = y
    }

    // Head-space pen for costume heads: points in head radii, [a] toward the facing, [b] down.
    private var hcx = 0f
    private var hcy = 0f
    private var hcr = 0f
    private var hcd = 1

    private fun headPen(x: Float, y: Float, r: Float, dir: Int) {
        hcx = x; hcy = y; hcr = r; hcd = dir
    }

    private fun hpx(a: Float) = hcx + hcr * a * hcd
    private fun hpy(b: Float) = hcy + hcr * b
    private fun hp(a: Float, b: Float) {
        p.add(hpx(a), hpy(b))
    }

    /** A torso point: [a] along the spine, [s] toward the chest, in world units. */
    private fun tp(a: Float, s: Float) {
        p.add(body.ptX(a, s), body.ptY(a, s))
    }

    /**
     * A face in profile on the head pen: skull, brow, nose, lips and a square, jutting chin as
     * ONE silhouette, painted like the armour: a lamp-lit gradient from the crown-front into the
     * base tone, and one crisp cool shadow plane wrapping the back of the skull and under the
     * jaw. At 120 px a face is two tones; any more marks read as scars.
     */
    private fun face(skin: Int) {
        p.begin()
        hp(-1.02f, -0.1f); hp(-0.8f, -0.74f); hp(-0.36f, -1.02f); hp(0.16f, -1.05f); hp(0.62f, -0.84f)
        hp(0.9f, -0.46f); hp(0.97f, -0.08f); hp(1.24f, 0.3f); hp(1.0f, 0.42f); hp(1.04f, 0.64f)
        hp(0.88f, 0.94f); hp(0.3f, 1.02f); hp(-0.22f, 0.8f); hp(-0.56f, 0.52f); hp(-0.94f, 0.26f)
        p.shapeLit(skin, hpx(0.55f), hpy(-0.95f), hpx(-0.5f), hpy(0.9f), mid = 0.45f)
        if (p.shading) {
            p.begin()
            hp(-1.02f, -0.1f); hp(-0.94f, 0.26f); hp(-0.56f, 0.52f); hp(-0.22f, 0.8f); hp(0.3f, 1.02f)
            hp(0.88f, 0.94f); hp(0.82f, 0.74f); hp(0.4f, 0.56f); hp(0.04f, 0.3f); hp(-0.22f, -0.1f); hp(-0.48f, -0.5f)
            p.shapeShade(skin)
        }
    }

    /** An eye and a heavy brow, for the heads that show one. */
    private fun eye(brow: Int) {
        if (p.ink || !p.hi) return
        p.detail(hpx(0.42f), hpy(-0.3f), hpx(0.94f), hpy(-0.26f), hcr * 0.18f, brow)
        p.dot(hpx(0.7f), hpy(-0.06f), hcr * 0.13f, 0xFF0C0A12.toInt())
    }

    /** Quilted channels on a limb: one stitched line across each bone. */
    private fun quilt(l: Limb, color: Int, wMul: Float) {
        if (!p.shading) return
        val q = Col.alpha(ActorPaint.shade(color), 0.85f)
        val w = k.limbW * wMul
        ring(l.ax, l.ay, l.jx, l.jy, 0.5f, 0.56f, w * 1.05f, q)
        ring(l.jx, l.jy, l.ex, l.ey, 0.42f, 0.48f, w * 0.9f, q)
    }

    /**
     * The key read at the collar, above where the front arm swings: a crisp white shirt collar
     * and the tie knot for the suits, a pale shirt collar for the cops.
     */
    private fun collar(z: Zone, acc: Int) {
        if (p.ink || (z != Zone.TOWER && z != Zone.METRO)) return
        val c = k.chestD
        val white = if (z == Zone.TOWER) 0xFFF4F2FA.toInt() else 0xFFB8C8E0.toInt()
        p.begin()
        tp(1.0f, -c * 0.16f); tp(1.1f, -c * 0.08f); tp(1.1f, c * 0.2f); tp(1.0f, c * 0.4f); tp(0.9f, c * 0.3f); tp(0.97f, c * 0.1f)
        p.shapeGradDetail(white, Col.lerp(white, ActorPaint.shade(white), 0.5f), body.ptX(1.1f, c * 0.1f), body.ptY(1.1f, c * 0.1f), body.ptX(0.9f, c * 0.2f), body.ptY(0.9f, c * 0.2f))
        if (z == Zone.TOWER) {
            p.begin()
            tp(1.0f, c * 0.24f); tp(1.0f, c * 0.38f); tp(0.9f, c * 0.4f); tp(0.9f, c * 0.26f)
            p.shapeGradDetail(ActorPaint.light(acc), ActorPaint.shade(acc), body.ptX(1f, c * 0.3f), body.ptY(1f, c * 0.3f), body.ptX(0.9f, c * 0.33f), body.ptY(0.9f, c * 0.33f))
        }
    }

    /** One gloss shape on glass: a soft curved sliver of the ceiling lamp. */
    private fun gloss(a0: Float, b0: Float, a1: Float, b1: Float, a2: Float, b2: Float, a3: Float, b3: Float, alpha: Float) {
        if (!p.shading) return
        g.blend(Gfx.Blend.ADD)
        p.begin()
        hp(a0, b0); hp(a1, b1); hp(a2, b2); hp(a3, b3)
        p.shapeGradDetail(Col.alpha(0xFFFFFFFF.toInt(), alpha * (1f - p.flatAmt)), 0x00FFFFFF, hpx(a0), hpy(b0), hpx(a3), hpy(b3))
        g.blend(Gfx.Blend.NORMAL)
    }

    /** A glint on glass: one crisp additive streak. */
    private fun glint(a0: Float, b0: Float, a1: Float, b1: Float, w: Float, alpha: Float) {
        if (!p.shading) return
        g.blend(Gfx.Blend.ADD)
        p.detail(hpx(a0), hpy(b0), hpx(a1), hpy(b1), hcr * w, Col.alpha(0xFFFFFFFF.toInt(), alpha * (1f - p.flatAmt)))
        g.blend(Gfx.Blend.NORMAL)
    }

    /** A thin stripe down a limb's centre line (trouser stripes, piping). */
    private fun stripe(l: Limb, w: Float, color: Int) {
        if (p.ink || !p.hi) return
        p.detail(l.ax, l.ay, l.jx, l.jy, w, color)
        p.detail(l.jx, l.jy, l.ex + (l.jx - l.ex) * 0.12f, l.ey + (l.jy - l.ey) * 0.12f, w, color)
    }

    /** A band around a limb at [t] of the way down its lower bone (cuffs, knee plates, hi-vis). */
    private fun band(l: Limb, t: Float, len: Float, w: Float, color: Int) {
        ring(l.jx, l.jy, l.ex, l.ey, t, t + len, w, color)
    }

    /** A square-cut band across the segment (x1, y1)-(x2, y2) from [t0] to [t1]; round caps would make short bands dots. */
    private fun ring(x1: Float, y1: Float, x2: Float, y2: Float, t0: Float, t1: Float, w: Float, color: Int) {
        if (p.ink) return
        val dx = x2 - x1
        val dy = y2 - y1
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val nx = -dy / d * w * 0.5f
        val ny = dx / d * w * 0.5f
        val ax = x1 + dx * t0
        val ay = y1 + dy * t0
        val bx = x1 + dx * t1
        val by = y1 + dy * t1
        p.begin().add(ax + nx, ay + ny).add(bx + nx, by + ny).add(bx - nx, by - ny).add(ax - nx, ay - ny).shapeDetail(color)
    }

    /** Bevels a plate just built: lit along the top edge from [ax, ay] to [bx, by], shadowed under it. */
    private fun bevel(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float, color: Int) {
        if (!p.shading) return
        p.detail(ax, ay, bx, by, 0.02f, ActorPaint.light(ActorPaint.light(color)))
        p.detail(cx, cy, dx, dy, 0.024f, ActorPaint.shade(color))
    }

    private fun weapon(armed: Boolean, trim: Int) {
        if (armed && showGun) body.gun(0, gunX, gunY, gunUp, trim)
    }

    /** The rank and file, reskinned per zone. */
    private fun guard(e: Enemy, dir: Int, zone: Zone, pal: Palette, armed: Boolean) {
        val main = pal.enemyMain
        val acc = pal.enemyAccent
        val skin = pal.enemySkin
        val rim = rimOf(pal, 0.6f)
        val z = if (zone == Zone.ROOFTOP || zone == Zone.VOID) Zone.TOWER else zone
        val L = look
        L.legW = 1f; L.armW = 1f; L.rim = rim
        when (z) {
            Zone.LABS -> {
                // A baggy white hazmat suit, sea-green rubber gloves and boots.
                L.torso = main; L.torsoLit = 0xFFFFFFFF.toInt(); L.legs = Col.mul(main, 0.94f); L.legsFar = Col.mul(main, 0.66f)
                L.arms = main; L.armsFar = Col.mul(main, 0.68f); L.boots = 0xFF1E3A34.toInt(); L.gloves = 0xFF2E6A58.toInt(); L.skin = skin
                L.legW = 1.24f; L.armW = 1.2f
            }
            Zone.METRO -> {
                // Steel-blue uniform shirt under a navy duty vest, navy trousers.
                val navy = Col.lerp(main, 0xFF40598A.toInt(), 0.45f)
                val shirt = 0xFF55709C.toInt()
                L.torso = shirt; L.torsoLit = Col.lerp(shirt, 0xFFFFFFFF.toInt(), 0.3f); L.legs = Col.mul(navy, 0.8f); L.legsFar = Col.mul(navy, 0.56f)
                L.arms = shirt; L.armsFar = Col.mul(shirt, 0.6f); L.boots = 0xFF0A0A0E.toInt(); L.gloves = 0xFF141418.toInt(); L.skin = skin
                L.legW = 1.04f; L.armW = 1.04f
            }
            Zone.MINES -> {
                // Denim overalls over a rust work shirt, rolled to the elbow.
                L.torso = main; L.torsoLit = Col.lerp(main, 0xFFFFFFFF.toInt(), 0.3f); L.legs = main; L.legsFar = Col.mul(main, 0.6f)
                L.arms = 0xFF8A4A30.toInt(); L.armsFar = 0xFF55301F.toInt(); L.boots = 0xFF2E1C10.toInt(); L.gloves = 0xFF6E4E30.toInt(); L.skin = skin
                L.legW = 1.06f
            }
            Zone.MAGMA -> {
                // Aluminised proximity suit: bulky, mirror-bright, charcoal gauntlets and boots.
                L.torso = Col.lerp(main, 0xFFDCD8D2.toInt(), 0.35f); L.torsoLit = 0xFFFFFCF4.toInt()
                L.legs = Col.mul(L.torso, 0.9f); L.legsFar = Col.mul(L.torso, 0.62f)
                L.arms = L.torso; L.armsFar = Col.mul(L.torso, 0.64f); L.boots = 0xFF26221E.toInt(); L.gloves = 0xFF3A322C.toInt(); L.skin = skin
                L.legW = 1.3f; L.armW = 1.26f
            }
            Zone.HELL -> {
                val robe = Col.lerp(main, 0xFF9A2030.toInt(), 0.45f)
                L.torso = robe; L.torsoLit = Col.lerp(robe, 0xFFFF6040.toInt(), 0.35f); L.legs = Col.mul(main, 0.6f); L.legsFar = Col.mul(main, 0.45f)
                L.arms = robe; L.armsFar = Col.mul(robe, 0.6f); L.boots = 0xFF140406.toInt(); L.gloves = 0xFF857472.toInt(); L.skin = 0xFF857472.toInt()
                L.armW = 1.3f
            }
            else -> {
                // A sharp charcoal-violet suit.
                val suit = Col.lerp(main, 0xFF4C4A66.toInt(), 0.58f)
                L.torso = suit; L.torsoLit = Col.lerp(suit, 0xFFFFFFFF.toInt(), 0.22f); L.legs = Col.mul(suit, 0.92f); L.legsFar = Col.mul(suit, 0.6f)
                L.arms = suit; L.armsFar = Col.mul(suit, 0.6f); L.boots = 0xFF050508.toInt(); L.gloves = skin; L.skin = skin
                L.legW = 0.92f; L.armW = 0.95f
            }
        }
        val robe = z == Zone.HELL
        val gunTrim = if (robe) acc else if (z == Zone.TOWER) 0xFF30303C.toInt() else 0xFF3A3E4A.toInt()
        p.twoPass {
            body.arm(k.armB, L, far = true)
            if (!robe) body.leg(k.legB, L, far = true) else robeFeet(L)
            backKit(z, acc)
            if (!robe) {
                body.leg(k.legF, L, far = false)
                when (z) {
                    Zone.METRO -> stripe(k.legF, 0.024f * k.hs, Col.alpha(acc, 0.85f))
                    Zone.MAGMA -> quilt(k.legF, L.legs, L.legW)
                    else -> Unit
                }
                if (z == Zone.TOWER) body.hem(L.torso, 0.2f, 0.035f)
                body.torso(L, chest = when (z) { Zone.TOWER -> 1.08f; Zone.LABS -> 1.1f; Zone.MAGMA -> 1.14f; Zone.METRO -> 1.04f; else -> 1f })
            } else {
                robeBody(L, Col.lerp(acc, ActorPaint.shade(L.torso), 0.5f))
            }
            guardDetails(z, dir, L, acc)
            body.neck(if (z == Zone.LABS || z == Zone.MAGMA || robe) L.torso else skin)
            collar(z, acc)
            guardHead(e, z, dir, L, pal)
        }
        p.twoPass {
            weapon(armed, gunTrim)
            if (robe) {
                sleeveArm(L)
            } else {
                body.arm(k.armF, L, far = false)
                when (z) {
                    // Crisp white shirt cuff, the one spark of white on the arm.
                    Zone.TOWER -> band(k.armF, 0.84f, 0.1f, k.limbW * 0.62f, 0xFFF0F0F8.toInt())
                    Zone.LABS -> band(k.armF, 0.8f, 0.14f, k.limbW * 0.78f, L.gloves)
                    Zone.MINES -> band(k.armF, -0.05f, 0.12f, k.limbW * 0.9f, Col.mul(L.arms, 0.72f))
                    Zone.MAGMA -> {
                        quilt(k.armF, L.arms, L.armW)
                        band(k.armF, 0.74f, 0.2f, k.limbW * 0.95f, L.gloves)
                    }
                    else -> Unit
                }
            }
        }
        if (e.state == EnemyState.AIM && armed && showGun) {
            f.glowDot(body.muzzleX, body.muzzleY, 0.035f, pal.laser, p.alphaMul)
        }
    }

    /** Kit worn on the back, drawn behind the torso: air tanks, the radio. */
    private fun backKit(z: Zone, acc: Int) {
        when (z) {
            Zone.LABS, Zone.MAGMA -> {
                val tx1 = body.ptX(0.3f, -k.waistD * 0.66f); val ty1 = body.ptY(0.3f, -k.waistD * 0.66f)
                val tx2 = body.ptX(0.86f, -k.chestD * 0.66f); val ty2 = body.ptY(0.86f, -k.chestD * 0.66f)
                val metal = if (z == Zone.LABS) 0xFFA8B4B4.toInt() else 0xFFD8702A.toInt()
                val tw = (if (z == Zone.LABS) 0.17f else 0.2f) * k.hs
                p.lightFrom(k.dir)
                p.bone(tx1, ty1, tx2, ty2, tw, tw, metal)
                // Valve cap.
                p.disc(tx2 + (tx2 - tx1) * 0.12f, ty2 + (ty2 - ty1) * 0.12f, 0.035f * k.hs, 0xFF2A2E34.toInt())
                if (!p.ink) {
                    // One label band.
                    ring(tx1, ty1, tx2, ty2, 0.36f, 0.6f, tw * 0.98f, if (z == Zone.LABS) acc else 0xFF2A2420.toInt())
                }
            }
            Zone.METRO -> {
                // A radio on the belt at the back.
                val rx = body.ptX(0.1f, -k.waistD * 0.56f); val ry = body.ptY(0.1f, -k.waistD * 0.56f)
                p.seg(rx, ry, rx, ry - 0.1f * k.hs, 0.08f * k.hs, 0xFF15161C.toInt())
            }
            else -> Unit
        }
    }

    private fun robeFeet(L: Look) {
        body.boot(k.legB, Col.mul(L.boots, 0.8f), false)
        body.boot(k.legF, L.boots, false)
    }

    private fun robeBody(L: Look, trim: Int) {
        // Hem at the feet, in the rig's own frame. The death ragdoll poses from the hip with a
        // dummy ground far below, and anchoring to k.ground alone gave dying robes a 10 u skirt
        // that swept through the floor below. Standing, the planted foot is at k.ground anyway.
        // Kicking legs (the ragdoll) must not drag the hem into a flat sheet: the hem hangs no
        // further than the legs reach and no wider than a stride from the hip.
        val gnd = min(min(k.ground, max(k.legF.ey, k.legB.ey) + 0.03f * k.hs), k.hipY + 0.8f * k.hs)
        val span = 0.36f * k.hs
        val fx = k.legF.ex.coerceIn(k.hipX - span, k.hipX + span)
        val bx = k.legB.ex.coerceIn(k.hipX - span, k.hipX + span)
        val front = if ((fx - bx) * k.dir > 0f) fx else bx
        val back = if ((fx - bx) * k.dir > 0f) bx else fx
        val c = k.chestD
        p.lightFrom(k.dir)
        // One flowing shape from the shoulders, flaring into a wide hem that trails behind.
        p.begin()
        tp(1.02f, -c * 0.3f); tp(1.0f, c * 0.32f); tp(0.6f, c * 0.52f)
        p.add(front + 0.14f * k.dir, gnd - 0.03f)
        p.add(back - 0.2f * k.dir, gnd - 0.03f)
        p.add(back - 0.14f * k.dir, gnd - 0.2f * k.hs)
        tp(0.4f, -c * 0.64f)
        p.shapeLit(L.torso, body.ptX(1.0f, c * 0.5f), body.ptY(1.0f, c * 0.5f), back - 0.1f * k.dir, gnd, mid = 0.4f)
        if (p.shading) {
            // Two long folds falling from the belt.
            val sh = Col.alpha(ActorPaint.shade(L.torso), 0.8f)
            p.begin()
            tp(0.05f, -c * 0.05f); tp(0.05f, c * 0.1f)
            p.add((front + back) * 0.5f + 0.02f * k.dir, gnd - 0.05f)
            p.add((front + back) * 0.5f - 0.1f * k.dir, gnd - 0.05f)
            p.shapeDetail(sh)
            p.begin()
            tp(0.05f, -c * 0.45f); tp(0.05f, -c * 0.3f)
            p.add(back - 0.06f * k.dir, gnd - 0.05f)
            p.add(back - 0.16f * k.dir, gnd - 0.05f)
            p.shapeDetail(sh)
        }
        if (!p.ink) {
            // The robe's front opening: a gold-edged band from the collar to the hem.
            p.detail(body.ptX(0.98f, c * 0.3f), body.ptY(0.98f, c * 0.3f), front + 0.1f * k.dir, gnd - 0.06f, 0.028f * k.hs, trim)
            p.detail(front + 0.12f * k.dir, gnd - 0.06f, back - 0.18f * k.dir, gnd - 0.06f, 0.05f * k.hs, trim)
            // A rope belt with a tassel.
            p.detail(body.ptX(0.1f, -k.waistD * 0.58f), body.ptY(0.1f, -k.waistD * 0.58f), body.ptX(0.1f, k.waistD * 0.56f), body.ptY(0.1f, k.waistD * 0.56f), 0.04f * k.hs, trim)
            val tx = body.ptX(0.1f, k.waistD * 0.35f)
            val ty = body.ptY(0.1f, k.waistD * 0.35f)
            p.detail(tx, ty, tx + 0.02f * k.dir, ty + 0.22f * k.hs + sin(f.t * 4f) * 0.01f, 0.022f * k.hs, trim)
            if (L.rim != 0) {
                g.blend(Gfx.Blend.ADD)
                p.detail(back - 0.14f * k.dir, gnd - 0.24f * k.hs, body.ptX(0.45f, -c * 0.6f), body.ptY(0.45f, -c * 0.6f), ActorPaint.RIM_W, L.rim)
                p.detail(body.ptX(0.45f, -c * 0.6f), body.ptY(0.45f, -c * 0.6f), body.ptX(0.98f, -c * 0.28f), body.ptY(0.98f, -c * 0.28f), ActorPaint.RIM_W, L.rim)
                g.blend(Gfx.Blend.NORMAL)
            }
        }
    }

    private fun sleeveArm(L: Look) {
        val l = k.armF
        val aw = k.limbW * L.armW
        p.lightFrom(k.dir)
        p.bone(l.ax, l.ay, l.jx, l.jy, aw, aw * 0.95f, L.arms, true)
        // Bell sleeve widening toward the wrist and stopping short of it, so an ashen hand
        // grips the gun out of the sleeve's dark mouth.
        val mx = l.jx + (l.ex - l.jx) * 0.72f
        val my = l.jy + (l.ey - l.jy) * 0.72f
        p.bone(l.jx, l.jy, mx, my, aw * 0.95f, aw * 1.4f, L.arms, true)
        if (!p.ink) p.dot(mx, my, aw * 0.5f, 0xFF140204.toInt())
        p.seg(mx, my, l.ex, l.ey, aw * 0.5f, Col.mul(L.gloves, 0.8f))
        body.glove(l, L.gloves)
    }

    private fun guardDetails(z: Zone, dir: Int, L: Look, acc: Int) {
        if (p.ink) return
        val c = k.chestD * (if (z == Zone.TOWER) 1.08f else 1f)
        val w = k.waistD
        val s = k.hs
        when (z) {
            Zone.TOWER -> {
                // White shirt front, a knotted tie, and a notched lapel rolling to the button.
                val shirt = 0xFFF0EEF6.toInt()
                p.begin()
                tp(1.01f, c * 0.36f); tp(0.99f, -c * 0.02f); tp(0.54f, c * 0.5f); tp(0.76f, c * 0.56f)
                p.shapeGradDetail(shirt, 0xFFA8A4C0.toInt(), body.ptX(1f, c * 0.3f), body.ptY(1f, c * 0.3f), body.ptX(0.6f, c * 0.5f), body.ptY(0.6f, c * 0.5f))
                p.begin()
                tp(0.99f, c * 0.2f); tp(0.99f, c * 0.34f); tp(0.9f, c * 0.36f); tp(0.9f, c * 0.24f)
                p.shapeDetail(ActorPaint.shade(acc))
                p.begin()
                tp(0.91f, c * 0.24f); tp(0.91f, c * 0.36f); tp(0.62f, c * 0.56f); tp(0.56f, c * 0.5f)
                p.shapeGradDetail(ActorPaint.light(acc), ActorPaint.shade(acc), body.ptX(0.9f, c * 0.3f), body.ptY(0.9f, c * 0.3f), body.ptX(0.58f, c * 0.53f), body.ptY(0.58f, c * 0.53f))
                p.begin()
                tp(0.99f, -c * 0.02f); tp(0.54f, c * 0.5f); tp(0.6f, c * 0.3f); tp(0.78f, -c * 0.04f)
                tp(0.84f, -c * 0.16f); tp(0.97f, -c * 0.16f)
                p.shapeGradDetail(ActorPaint.light(ActorPaint.light(L.torso)), L.torso, body.ptX(1f, 0f), body.ptY(1f, 0f), body.ptX(0.6f, c * 0.4f), body.ptY(0.6f, c * 0.4f))
                if (p.shading) {
                    p.detail(body.ptX(0.78f, -c * 0.04f), body.ptY(0.78f, -c * 0.04f), body.ptX(0.55f, c * 0.46f), body.ptY(0.55f, c * 0.46f), 0.014f * s, ActorPaint.shade(L.torso))
                    // Pocket square.
                    p.begin()
                    tp(0.74f, c * 0.3f); tp(0.78f, c * 0.36f); tp(0.74f, c * 0.42f)
                    p.shapeDetail(0xFFE8E6F0.toInt())
                }
            }
            Zone.LABS -> {
                // Zip seam, a taped belt: a suit, not a snowman.
                if (p.shading) p.detail(body.ptX(0.95f, c * 0.36f), body.ptY(0.95f, c * 0.36f), body.ptX(0.15f, w * 0.44f), body.ptY(0.15f, w * 0.44f), 0.014f * s, ActorPaint.shade(L.torso))
                p.detail(body.ptX(0.12f, -w * 0.56f), body.ptY(0.12f, -w * 0.56f), body.ptX(0.12f, w * 0.56f), body.ptY(0.12f, w * 0.56f), 0.05f * s, 0xFF3A4A48.toInt())
                // The tank's one strap, over the shoulder.
                p.detail(body.ptX(0.98f, -c * 0.3f), body.ptY(0.98f, -c * 0.3f), body.ptX(0.55f, c * 0.2f), body.ptY(0.55f, c * 0.2f), 0.05f * s, 0xFF3A4A48.toInt())
                // A hazard patch on the chest in the zone accent.
                p.begin()
                tp(0.74f, c * 0.1f); tp(0.74f, c * 0.34f); tp(0.6f, c * 0.36f); tp(0.6f, c * 0.12f)
                p.shapeGradDetail(ActorPaint.light(acc), acc, body.ptX(0.74f, c * 0.2f), body.ptY(0.74f, c * 0.2f), body.ptX(0.6f, c * 0.2f), body.ptY(0.6f, c * 0.2f))
            }
            Zone.METRO -> {
                // The duty vest: one dark shape over the shirt, lit across the chest.
                val vest = 0xFF18223A.toInt()
                p.begin()
                tp(0.1f, -w * 0.53f); tp(0.1f, w * 0.52f); tp(0.4f, w * 0.52f); tp(0.76f, c * 0.54f); tp(0.9f, c * 0.46f)
                tp(0.88f, c * 0.2f); tp(0.92f, -c * 0.1f); tp(0.9f, -c * 0.4f); tp(0.8f, -c * 0.52f); tp(0.34f, -w * 0.54f)
                p.shapeGradDetail(ActorPaint.light(ActorPaint.light(vest)), vest, body.ptX(0.9f, c * 0.4f), body.ptY(0.9f, c * 0.4f), body.ptX(0.3f, -w * 0.4f), body.ptY(0.3f, -w * 0.4f))
                // Duty belt with a brass buckle, a gold shield on the chest, a shoulder radio.
                p.detail(body.ptX(0.08f, -w * 0.56f), body.ptY(0.08f, -w * 0.56f), body.ptX(0.08f, w * 0.54f), body.ptY(0.08f, w * 0.54f), 0.07f * s, 0xFF0C0C10.toInt())
                p.detail(body.ptX(0.08f, w * 0.34f), body.ptY(0.08f, w * 0.34f), body.ptX(0.08f, w * 0.46f), body.ptY(0.08f, w * 0.46f), 0.05f * s, 0xFFE0B040.toInt())
                val gold = 0xFFFFD060.toInt()
                p.begin()
                tp(0.9f, c * 0.24f); tp(0.92f, c * 0.37f); tp(0.9f, c * 0.5f); tp(0.72f, c * 0.47f); tp(0.66f, c * 0.37f); tp(0.72f, c * 0.27f)
                p.shapeGradDetail(0xFFFFF0B0.toInt(), 0xFFB07818.toInt(), body.ptX(0.92f, c * 0.37f), body.ptY(0.92f, c * 0.37f), body.ptX(0.66f, c * 0.37f), body.ptY(0.66f, c * 0.37f))
                if (p.shading) p.dot(body.ptX(0.8f, c * 0.38f), body.ptY(0.8f, c * 0.38f), 0.018f * s, ActorPaint.shade(gold))
                // Epaulette, and the radio clipped at the collar with its stubby antenna.
                p.detail(body.ptX(1.0f, -c * 0.2f), body.ptY(1.0f, -c * 0.2f), body.ptX(0.98f, c * 0.14f), body.ptY(0.98f, c * 0.14f), 0.04f * s, Col.mul(L.torso, 0.6f))
                val rx = body.ptX(0.92f, -c * 0.34f); val ry = body.ptY(0.92f, -c * 0.34f)
                p.detail(rx, ry - 0.02f * s, rx, ry + 0.06f * s, 0.055f * s, 0xFF101116.toInt())
                p.detail(rx - 0.012f * dir * s, ry - 0.02f * s, rx - 0.012f * dir * s, ry - 0.1f * s, 0.012f * s, 0xFF101116.toInt())
                if (p.shading) p.dot(rx + 0.012f * dir * s, ry, 0.009f * s, acc)
            }
            Zone.MINES -> {
                // Overall bib and a strap over the shoulder, a brass button, a hi-vis chest band.
                val bib = Col.lerp(L.torso, 0xFFFFFFFF.toInt(), 0.08f)
                p.begin()
                tp(0.2f, w * 0.5f); tp(0.4f, w * 0.52f); tp(0.76f, c * 0.54f); tp(0.82f, c * 0.52f); tp(0.82f, c * 0.02f); tp(0.2f, -w * 0.1f)
                p.shapeGradDetail(ActorPaint.light(bib), bib, body.ptX(0.82f, c * 0.3f), body.ptY(0.82f, c * 0.3f), body.ptX(0.2f, 0f), body.ptY(0.2f, 0f))
                // The shirt shows above the bib.
                p.begin()
                tp(1.0f, c * 0.3f); tp(1.03f, -c * 0.28f); tp(0.83f, -c * 0.44f); tp(0.83f, c * 0.53f)
                p.shapeGradDetail(ActorPaint.light(L.arms), L.arms, body.ptX(1f, c * 0.2f), body.ptY(1f, c * 0.2f), body.ptX(0.84f, -c * 0.3f), body.ptY(0.84f, -c * 0.3f))
                p.detail(body.ptX(0.84f, c * 0.1f), body.ptY(0.84f, c * 0.1f), body.ptX(1.0f, -c * 0.12f), body.ptY(1.0f, -c * 0.12f), 0.05f * s, Col.mul(L.torso, 0.8f))
                p.dot(body.ptX(0.8f, c * 0.14f), body.ptY(0.8f, c * 0.14f), 0.022f * s, 0xFFD8A040.toInt())
                p.detail(body.ptX(0.56f, -w * 0.5f), body.ptY(0.56f, -w * 0.5f), body.ptX(0.56f, c * 0.53f), body.ptY(0.56f, c * 0.53f), 0.07f * s, acc)
                p.detail(body.ptX(0.56f, -w * 0.5f), body.ptY(0.56f, -w * 0.5f), body.ptX(0.56f, c * 0.53f), body.ptY(0.56f, c * 0.53f), 0.022f * s, Col.alpha(0xFFFFFFFF.toInt(), 0.55f))
            }
            Zone.MAGMA -> {
                // A mirror streak down the chest, a charcoal belt with a hazard buckle.
                if (p.shading) {
                    g.blend(Gfx.Blend.ADD)
                    p.detail(body.ptX(0.72f, c * 0.3f), body.ptY(0.72f, c * 0.3f), body.ptX(0.2f, w * 0.3f), body.ptY(0.2f, w * 0.3f), 0.035f * s, Col.alpha(0xFFFFFFFF.toInt(), 0.45f))
                    g.blend(Gfx.Blend.NORMAL)
                    // Quilted: two stitched channels across the jacket.
                    val q = Col.alpha(ActorPaint.shade(L.torso), 0.85f)
                    p.detail(body.ptX(0.34f, -w * 0.52f), body.ptY(0.34f, -w * 0.52f), body.ptX(0.36f, w * 0.52f), body.ptY(0.36f, w * 0.52f), 0.02f * s, q)
                    p.detail(body.ptX(0.56f, -c * 0.52f), body.ptY(0.56f, -c * 0.52f), body.ptX(0.58f, c * 0.54f), body.ptY(0.58f, c * 0.54f), 0.02f * s, q)
                }
                // The tank's one strap.
                p.detail(body.ptX(0.72f, -c * 0.62f), body.ptY(0.72f, -c * 0.62f), body.ptX(0.72f, -c * 0.2f), body.ptY(0.72f, -c * 0.2f), 0.06f * s, 0xFF2A2420.toInt())
                p.detail(body.ptX(0.06f, -w * 0.56f), body.ptY(0.06f, -w * 0.56f), body.ptX(0.06f, w * 0.56f), body.ptY(0.06f, w * 0.56f), 0.07f * s, 0xFF2A2420.toInt())
                p.detail(body.ptX(0.06f, w * 0.2f), body.ptY(0.06f, w * 0.2f), body.ptX(0.06f, w * 0.44f), body.ptY(0.06f, w * 0.44f), 0.05f * s, acc)
            }
            else -> Unit
        }
    }

    private fun guardHead(e: Enemy, z: Zone, dir: Int, L: Look, pal: Palette) {
        headPen(k.headX, k.headY, k.headR * 1.06f, dir)
        val acc = pal.enemyAccent
        p.lightFrom(dir)
        when (z) {
            Zone.TOWER -> towerHead(L, pal)
            Zone.LABS -> labsHead(L, acc)
            Zone.METRO -> metroHead(L, acc)
            Zone.MINES -> minesHead(e, L, acc)
            Zone.MAGMA -> magmaHead(L)
            else -> hellHead(L)
        }
    }

    /** Slicked-back hair, wraparound shades with the neon in them, an earpiece. */
    private fun towerHead(L: Look, pal: Palette) {
        face(L.skin)
        val hair = 0xFF1C1A28.toInt()
        p.begin()
        hp(-0.85f, 0.42f); hp(-1.08f, -0.2f); hp(-0.88f, -0.8f); hp(-0.25f, -1.18f); hp(0.45f, -1.14f)
        hp(0.95f, -0.86f); hp(0.98f, -0.62f); hp(0.72f, -0.66f); hp(0.2f, -0.6f); hp(-0.12f, -0.34f); hp(-0.2f, 0.2f)
        p.shapeLit(hair, hpx(0.5f), hpy(-1.2f), hpx(-0.9f), hpy(0.3f), mid = 0.35f)
        if (p.shading) {
            // Comb lines of pomade swept back from the quiff.
            val sheen = Col.alpha(0xFF8A88B0.toInt(), 0.7f)
            p.detail(hpx(0.7f), hpy(-0.92f), hpx(-0.5f), hpy(-0.9f), hcr * 0.09f, sheen)
            p.detail(hpx(0.3f), hpy(-0.72f), hpx(-0.7f), hpy(-0.5f), hcr * 0.06f, Col.alpha(sheen, 0.45f))
        }
        // Wraparound shades: dark glass with the hall's neon slid across it.
        p.begin()
        hp(0.12f, -0.36f); hp(1.2f, -0.4f); hp(1.16f, -0.04f); hp(0.9f, 0.12f); hp(0.46f, 0.1f); hp(0.26f, -0.08f)
        p.shape(0xFF08080E.toInt(), sep = true)
        p.begin()
        hp(0.2f, -0.3f); hp(1.13f, -0.34f); hp(1.1f, -0.06f); hp(0.88f, 0.06f); hp(0.48f, 0.04f); hp(0.32f, -0.08f)
        p.shapeGradDetail(0xFF0A0A12.toInt(), Col.lerp(pal.neon, 0xFF0A0A12.toInt(), 0.62f), hpx(0.6f), hpy(-0.2f), hpx(0.6f), hpy(0.1f))
        glint(0.52f, -0.24f, 0.72f, 0.0f, 0.1f, 0.8f)
        if (!p.ink) {
            p.detail(hpx(0.16f), hpy(-0.26f), hpx(-0.22f), hpy(-0.06f), hcr * 0.1f, 0xFF08080E.toInt())
            // Earpiece: a clear coil from the ear into the collar.
            if (p.shading) p.detail(hpx(-0.26f), hpy(0.24f), hpx(-0.46f), hpy(1.4f), hcr * 0.07f, Col.alpha(0xFFD8E4F4.toInt(), 0.55f))
        }
        body.headRim(hcx, hcy, hcr, look.rim)
        eyesAt(hpx(0.66f), hpy(-0.12f))
    }

    /** A bulbous hood, a wide glass faceplate, a respirator canister. */
    private fun labsHead(L: Look, acc: Int) {
        val c = k.chestD
        // The hood's skirt draping over the shoulders.
        p.begin()
        hp(-1.2f, 0.3f); hp(1.0f, 0.6f)
        tp(0.84f, c * 0.62f); tp(0.82f, -c * 0.68f)
        p.shapeLit(L.torso, hpx(0.3f), hpy(0f), body.ptX(0.82f, -c * 0.68f), body.ptY(0.82f, -c * 0.68f))
        if (p.shading) p.detail(body.ptX(0.83f, c * 0.58f), body.ptY(0.83f, c * 0.58f), body.ptX(0.81f, -c * 0.64f), body.ptY(0.81f, -c * 0.64f), 0.03f * k.hs, ActorPaint.shade(L.torso))
        p.ball(hpx(-0.1f), hpy(0.0f), hcr * 1.36f, L.torso, gloss = 0.22f)
        // The faceplate: a black-glass rounded window with a frame.
        p.begin()
        hp(0.05f, -0.62f); hp(0.7f, -0.74f); hp(1.12f, -0.52f); hp(1.3f, -0.05f); hp(1.2f, 0.42f); hp(0.8f, 0.58f); hp(0.18f, 0.5f); hp(-0.02f, -0.05f)
        p.shape(0xFF3A4848.toInt(), sep = true)
        p.begin()
        hp(0.14f, -0.52f); hp(0.7f, -0.62f); hp(1.04f, -0.44f); hp(1.19f, -0.04f); hp(1.1f, 0.34f); hp(0.78f, 0.47f); hp(0.24f, 0.4f); hp(0.08f, -0.05f)
        p.shapeGradDetail(Col.lerp(acc, 0xFF061412.toInt(), 0.55f), 0xFF040A0A.toInt(), hpx(0.6f), hpy(-0.6f), hpx(0.6f), hpy(0.45f))
        gloss(0.2f, -0.44f, 0.72f, -0.56f, 0.8f, -0.4f, 0.26f, -0.26f, 0.7f)
        // Respirator: one round filter cartridge on the jaw, capped in the accent.
        p.ball(hpx(0.86f), hpy(0.68f), hcr * 0.36f, 0xFF3A4646.toInt())
        if (!p.ink) {
            p.dot(hpx(0.9f), hpy(0.68f), hcr * 0.22f, acc)
        }
        body.headRim(hpx(-0.1f), hpy(0.0f), hcr * 1.36f, look.rim)
        eyesAt(hpx(0.72f), hpy(-0.1f))
    }

    /** A peaked cap with a glossy brim, the badge, the cap band in the accent. */
    private fun metroHead(L: Look, acc: Int) {
        face(L.skin)
        if (!p.ink && p.hi) p.dot(hpx(0.7f), hpy(-0.04f), hcr * 0.13f, 0xFF0C0A12.toInt())
        val cap = 0xFF182238.toInt()
        // Crown flaring up and forward, like a real peaked cap.
        p.begin()
        hp(-1.0f, -0.32f); hp(-1.1f, -0.9f); hp(-0.7f, -1.3f); hp(0.5f, -1.6f); hp(1.22f, -1.5f); hp(0.98f, -0.46f)
        p.shapeLit(cap, hpx(0.6f), hpy(-1.5f), hpx(-0.8f), hpy(-0.4f))
        if (!p.ink) {
            p.begin()
            hp(-1.0f, -0.32f); hp(-1.02f, -0.62f); hp(0.99f, -0.72f); hp(0.96f, -0.44f)
            p.shapeGradDetail(acc, ActorPaint.shade(acc), hpx(0.5f), hpy(-0.7f), hpx(-0.8f), hpy(-0.4f))
            // Cap badge.
            p.begin()
            hp(0.62f, -1.18f); hp(0.82f, -1.08f); hp(0.78f, -0.8f); hp(0.62f, -0.76f); hp(0.5f, -0.9f)
            p.shapeGradDetail(0xFFFFF0B0.toInt(), 0xFFB07818.toInt(), hpx(0.6f), hpy(-1.2f), hpx(0.6f), hpy(-0.76f))
        }
        // The brim: patent-leather black, angled over the eyes.
        p.begin()
        hp(0.3f, -0.5f); hp(1.0f, -0.54f); hp(1.46f, -0.3f); hp(1.38f, -0.2f); hp(0.86f, -0.3f)
        p.shape(0xFF08080C.toInt(), sep = true)
        glint(0.9f, -0.46f, 1.34f, -0.3f, 0.07f, 0.55f)
        body.headRim(hcx, hcy, hcr, look.rim)
        eyesAt(hpx(0.7f), hpy(-0.06f))
    }

    /** A hard hat with its lamp, a knotted bandana up over the nose. */
    private fun minesHead(e: Enemy, L: Look, acc: Int) {
        face(L.skin)
        eye(0xFF3A2416.toInt())
        // Bandana: a faded-denim cloth triangle over the nose, its point on the chest, knotted
        // behind. Cool and pale against warm skin, so it reads as cloth, not as a face.
        val cloth = 0xFF6A7EA6.toInt()
        p.begin()
        hp(-0.6f, 0.28f); hp(0.55f, 0.22f); hp(1.29f, 0.3f); hp(1.18f, 0.76f); hp(0.72f, 1.36f); hp(0.1f, 1.1f); hp(-0.46f, 0.72f)
        p.shapeLit(cloth, hpx(1.0f), hpy(0.1f), hpx(0.1f), hpy(1.2f), sep = true)
        p.disc(hpx(-0.82f), hpy(0.4f), hcr * 0.2f, Col.mul(cloth, 0.8f))
        p.seg(hpx(-0.88f), hpy(0.46f), hpx(-1.3f), hpy(0.9f), hcr * 0.18f, Col.mul(cloth, 0.72f))
        if (p.shading) {
            // One fold from the nose bridge down to the point.
            p.detail(hpx(0.95f), hpy(0.44f), hpx(0.64f), hpy(1.18f), hcr * 0.1f, ActorPaint.shade(cloth))
        }
        // Hard hat: a lit dome, a ridge down the crown, a full brim.
        p.begin()
        hp(-1.02f, -0.38f); hp(-0.95f, -0.86f); hp(-0.5f, -1.28f); hp(0.15f, -1.4f); hp(0.72f, -1.2f); hp(1.02f, -0.74f); hp(1.06f, -0.4f)
        p.shapeLit(acc, hpx(0.4f), hpy(-1.4f), hpx(-0.8f), hpy(-0.4f), mid = 0.4f)
        if (p.shading) p.detail(hpx(-0.6f), hpy(-1.12f), hpx(0.5f), hpy(-1.3f), hcr * 0.13f, ActorPaint.light(ActorPaint.light(acc)))
        p.seg(hpx(-1.24f), hpy(-0.38f), hpx(1.4f), hpy(-0.36f), hcr * 0.2f, ActorPaint.shade(acc))
        // Lamp housing on the front of the dome.
        val lx = hpx(0.98f)
        val ly = hpy(-0.8f)
        p.disc(lx, ly, hcr * 0.26f, 0xFF2A2A30.toInt())
        if (!p.ink) {
            if (e.alive) {
                f.glowDot(lx + hcr * 0.08f * hcd, ly, hcr * 0.17f, 0xFFFFF4C0.toInt(), p.alphaMul * (1f - p.flatAmt))
                p.quad(lx, ly - 0.03f, lx, ly + 0.03f, lx + 2.2f * hcd, ly + 0.9f, lx + 2.2f * hcd, ly - 0.35f, p.c(0x1AFFF4C0))
            } else {
                p.dot(lx + hcr * 0.08f * hcd, ly, hcr * 0.15f, 0xFF6A6450.toInt())
            }
        }
        body.headRim(hcx, hcy, hcr, look.rim)
        eyesAt(hpx(0.7f), hpy(-0.06f))
    }

    /** A proximity hood draping onto the shoulders, and a big gold mirror visor. */
    private fun magmaHead(L: Look) {
        val c = k.chestD
        p.begin()
        // A boxy, flat-crowned proximity hood whose cape squares off over the shoulders.
        p.begin()
        hp(-0.9f, -1.3f); hp(0.8f, -1.32f); hp(1.3f, -0.9f); hp(1.34f, 0.8f)
        tp(0.74f, c * 0.8f); tp(0.66f, c * 0.84f); tp(0.64f, -c * 0.86f); tp(0.72f, -c * 0.82f)
        hp(-1.36f, 0.4f); hp(-1.34f, -0.95f)
        p.shapeLit(L.torso, hpx(0.4f), hpy(-1.3f), body.ptX(0.66f, -c * 0.8f), body.ptY(0.66f, -c * 0.8f), mid = 0.35f)
        if (p.shading) {
            // One quilted seam where the hood meets the cape.
            p.detail(hpx(-1.3f), hpy(0.75f), hpx(1.3f), hpy(1.1f), hcr * 0.1f, ActorPaint.shade(L.torso))
        }
        // The visor: a framed pane of gold glass.
        p.begin()
        hp(-0.05f, -0.86f); hp(1.0f, -0.84f); hp(1.36f, -0.4f); hp(1.36f, 0.46f); hp(1.1f, 0.66f); hp(0.06f, 0.62f)
        p.shape(0xFF3A322C.toInt(), sep = true)
        p.begin()
        hp(0.05f, -0.74f); hp(0.95f, -0.72f); hp(1.25f, -0.34f); hp(1.25f, 0.4f); hp(1.04f, 0.54f); hp(0.14f, 0.52f)
        p.shapeGradDetail(0xFFFFE890.toInt(), 0xFFA04E06.toInt(), hpx(0.6f), hpy(-0.66f), hpx(0.6f), hpy(0.5f))
        if (p.shading) {
            // The lava below, caught in the bottom of the visor.
            p.begin()
            hp(0.14f, 0.22f); hp(1.25f, 0.16f); hp(1.25f, 0.4f); hp(1.04f, 0.54f); hp(0.14f, 0.52f)
            p.shapeDetail(Col.alpha(0xFFFF6A10.toInt(), 0.55f))
        }
        gloss(0.2f, -0.64f, 0.56f, -0.66f, 0.4f, 0.4f, 0.2f, 0.42f, 0.55f)
        body.headRim(hcx, hcy, hcr * 1.3f, look.rim)
        eyesAt(hpx(0.7f), hpy(-0.1f))
    }

    /** A tall pointed hood whose tip sweeps back, the face lost in its shadow but for the eyes. */
    private fun hellHead(L: Look) {
        val c = k.chestD
        // A mantle over the shoulders under the hood.
        p.begin()
        hp(-1.1f, 0.4f); hp(0.9f, 0.7f)
        tp(0.64f, c * 0.62f); tp(0.6f, -c * 0.72f)
        p.shapeLit(Col.mul(L.torso, 0.85f), hpx(0.5f), hpy(0.5f), body.ptX(0.6f, -c * 0.7f), body.ptY(0.6f, -c * 0.7f), sep = true)
        if (p.shading) p.detail(body.ptX(0.65f, c * 0.58f), body.ptY(0.65f, c * 0.58f), body.ptX(0.62f, -c * 0.4f), body.ptY(0.62f, -c * 0.4f), 0.022f * k.hs, ActorPaint.light(L.torso))
        p.begin()
        hp(-1.18f, 1.02f); hp(-1.22f, -0.3f); hp(-1.1f, -1.2f); hp(-1.6f, -2.0f); hp(-2.3f, -2.3f); hp(-1.1f, -2.02f); hp(-0.2f, -1.5f); hp(0.5f, -1.08f)
        hp(1.05f, -0.52f); hp(1.3f, 0.2f); hp(1.12f, 0.92f); hp(0.6f, 1.2f)
        p.shapeLit(L.torso, hpx(0.4f), hpy(-1.4f), hpx(-1.0f), hpy(1.0f), sep = true, mid = 0.4f)
        if (!p.ink) {
            // The opening: a deep shadow, lipped where the lamp catches the fold.
            p.begin()
            hp(0.22f, -0.6f); hp(0.92f, -0.46f); hp(1.14f, 0.18f); hp(1.0f, 0.78f); hp(0.4f, 0.9f); hp(0.1f, 0.2f)
            p.shapeGradDetail(0xFF240608.toInt(), 0xFF050002.toInt(), hpx(0.8f), hpy(-0.5f), hpx(0.3f), hpy(0.8f))
            p.detail(hpx(0.2f), hpy(-0.62f), hpx(0.94f), hpy(-0.48f), hcr * 0.1f, ActorPaint.light(L.torso))
            p.detail(hpx(0.94f), hpy(-0.48f), hpx(1.16f), hpy(0.18f), hcr * 0.1f, ActorPaint.light(L.torso))
            if (p.shading) p.detail(hpx(-1.0f), hpy(-1.1f), hpx(-0.2f), hpy(-0.3f), hcr * 0.1f, ActorPaint.shade(L.torso))
            val eye = 0xFFFFC040.toInt()
            val k2 = p.alphaMul * (1f - p.flatAmt)
            f.glowDot(hpx(0.78f), hpy(-0.05f), hcr * 0.13f, eye, k2)
            f.glowDot(hpx(0.44f), hpy(-0.03f), hcr * 0.11f, eye, k2 * 0.8f)
            if (L.rim != 0) {
                g.blend(Gfx.Blend.ADD)
                p.detail(hpx(-1.16f), hpy(0.8f), hpx(-1.18f), hpy(-0.3f), ActorPaint.RIM_W, L.rim)
                p.detail(hpx(-1.18f), hpy(-0.3f), hpx(-1.1f), hpy(-1.2f), ActorPaint.RIM_W, L.rim)
                p.detail(hpx(-1.1f), hpy(-1.2f), hpx(-1.6f), hpy(-2.0f), ActorPaint.RIM_W, L.rim)
                g.blend(Gfx.Blend.NORMAL)
            }
        }
        eyesAt(hpx(0.62f), hpy(-0.05f))
    }

    // ------------------------------------------------------------- elites

    private fun mixf(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** A soft additive bloom and a hot core line: something that truly emits (visors, lava). */
    private fun emissive(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int, core: Int, bloom: Float = 3.2f) {
        if (p.ink) return
        val a = p.alphaMul * (1f - p.flatAmt)
        if (p.shading && a > 0.05f) {
            g.blend(Gfx.Blend.ADD)
            g.line(x1, y1, x2, y2, w * bloom, Col.fade(Col.alpha(color, 0.22f), a))
            g.blend(Gfx.Blend.NORMAL)
        }
        p.detail(x1, y1, x2, y2, w, color)
        if (p.hi) p.detail(x1, y1, x2, y2, w * 0.42f, core)
    }

    /**
     * The heavy: three big masses. One armoured torso (barrel chest, back and a shoulder yoke
     * rising round the head, a single painted gradient), a small helmet sunk into it with a slot
     * visor, and pillar legs on block boots. Each zone gives him one signature: Tower a corporate
     * black shell with a tie-coloured plate, Labs/Magma white, Metro a riot stripe, Mines a hard
     * hat and lamp, Hell horns.
     */
    private fun heavy(e: Enemy, dir: Int, zone: Zone, pal: Palette, armed: Boolean) {
        val z = if (zone == Zone.ROOFTOP || zone == Zone.VOID) Zone.TOWER else zone
        // The visor is the one emissive; Magma's is a gold heat shield.
        val acc = if (z == Zone.MAGMA) 0xFFFFC040.toInt() else pal.enemyAccent
        val hot = Col.lerp(acc, 0xFFFFFFFF.toInt(), 0.65f)
        val shell = when (z) {
            Zone.TOWER -> 0xFF383846.toInt()
            Zone.LABS -> 0xFFDCE2E0.toInt()
            Zone.METRO -> 0xFF2C3E5C.toInt()
            Zone.MINES -> 0xFF56616A.toInt()
            Zone.MAGMA -> 0xFFC8BCA8.toInt()
            else -> 0xFF3E3438.toInt()
        }
        // The costume's one accent, a mid value that never outshines the visor.
        val trim = when (z) {
            Zone.TOWER -> Col.lerp(pal.enemyAccent, shell, 0.35f)
            Zone.LABS -> Col.lerp(pal.enemyAccent, 0xFF2A6A48.toInt(), 0.45f)
            Zone.METRO -> Col.lerp(shell, 0xFFE8ECF0.toInt(), 0.5f)
            Zone.MAGMA -> 0xFFB08A3E.toInt()
            else -> 0
        }
        // Value steps between the masses: torso, then legs a step down, helmet a step up.
        val legC = Col.lerp(shell, 0xFF0C0C12.toInt(), 0.3f)
        val helm = if (z == Zone.TOWER) 0xFF3A3A48.toInt() else Col.lerp(shell, 0xFFFFFFFF.toInt(), 0.12f)
        val suit = 0xFF101016.toInt()
        val L = look
        L.torso = shell; L.torsoLit = shell; L.legs = legC; L.legsFar = Col.mul(legC, 0.62f)
        L.arms = shell; L.armsFar = Col.mul(shell, 0.55f); L.boots = 0xFF0C0C12.toInt(); L.gloves = 0xFF16161E.toInt(); L.skin = shell
        L.rim = rimOf(pal, 0.75f); L.legW = 1.1f; L.armW = 1.0f
        val hx = k.headX
        val hy = k.headY + k.headR * 0.3f
        val r = k.headR
        val c = k.chestD
        val w = k.waistD
        val lw = k.limbW * L.legW
        val aw = k.limbW * L.armW
        p.lightFrom(dir)
        p.twoPass {
            p.lightFrom(dir)
            // Far arm and leg, a value down.
            p.bone(k.armB.ax, k.armB.ay, k.armB.jx, k.armB.jy, aw * 1.25f, aw * 1.05f, L.armsFar, lit = false)
            p.bone(k.armB.jx, k.armB.jy, k.armB.ex, k.armB.ey, aw * 1.15f, aw * 1.0f, L.armsFar, lit = false)
            body.handAt(k.armB.ex, k.armB.ey, Col.mul(L.gloves, 0.8f))
            pillarLeg(k.legB, lw, L.legsFar, false, 0)
            pillarLeg(k.legF, lw, legC, true, L.rim)
            // The near upper arm tucks in behind the chest.
            p.bone(k.armF.ax, k.armF.ay, k.armF.jx, k.armF.jy, aw * 1.3f, aw * 1.1f, Col.mul(shell, 0.8f))
            // Belt: a band of dark undersuit between the legs and the shell.
            p.begin()
                .add(body.ptX(0.12f, -w * 0.56f), body.ptY(0.12f, -w * 0.56f))
                .add(body.ptX(0.12f, w * 0.62f), body.ptY(0.12f, w * 0.62f))
                .add(body.ptX(-0.1f, w * 0.56f), body.ptY(-0.1f, w * 0.56f))
                .add(body.ptX(-0.1f, -w * 0.52f), body.ptY(-0.1f, -w * 0.52f))
                .shape(suit)
            // The torso: one mass, barrel chest to humped back, the yoke rising round the head.
            p.begin()
                .add(body.ptX(0.08f, -w * 0.6f), body.ptY(0.08f, -w * 0.6f))
                .add(body.ptX(0.5f, -c * 0.7f), body.ptY(0.5f, -c * 0.7f))
                .add(body.ptX(0.92f, -c * 0.86f), body.ptY(0.92f, -c * 0.86f))
                .add(body.ptX(1.16f, -c * 0.74f), body.ptY(1.16f, -c * 0.74f))
                .add(body.ptX(1.26f, -c * 0.38f), body.ptY(1.26f, -c * 0.38f))
                .add(body.ptX(1.24f, c * 0.3f), body.ptY(1.24f, c * 0.3f))
                .add(body.ptX(1.12f, c * 0.66f), body.ptY(1.12f, c * 0.66f))
                .add(body.ptX(0.9f, c * 0.86f), body.ptY(0.9f, c * 0.86f))
                .add(body.ptX(0.62f, c * 0.8f), body.ptY(0.62f, c * 0.8f))
                .add(body.ptX(0.34f, w * 0.72f), body.ptY(0.34f, w * 0.72f))
                .add(body.ptX(0.08f, w * 0.64f), body.ptY(0.08f, w * 0.64f))
                .shapeLit(shell, body.ptX(1.22f, c * 0.55f), body.ptY(1.22f, c * 0.55f), body.ptX(0.15f, -w * 0.5f), body.ptY(0.15f, -w * 0.5f), mid = 0.38f)
            if (p.shading) {
                // One seam where the chest plate meets the belly.
                p.detail(body.ptX(0.5f, -c * 0.66f), body.ptY(0.5f, -c * 0.66f), body.ptX(0.6f, c * 0.76f), body.ptY(0.6f, c * 0.76f), 0.02f, ActorPaint.shade(ActorPaint.shade(shell)))
                // Two broad soft highlights: the shoulder dome and the swell of the chest.
                val hi = Col.alpha(0xFFFFFFFF.toInt(), 0.34f)
                val lo = Col.alpha(0xFFFFFFFF.toInt(), 0f)
                p.begin()
                    .add(body.ptX(1.2f, -c * 0.62f), body.ptY(1.2f, -c * 0.62f))
                    .add(body.ptX(1.25f, -c * 0.3f), body.ptY(1.25f, -c * 0.3f))
                    .add(body.ptX(1.23f, c * 0.28f), body.ptY(1.23f, c * 0.28f))
                    .add(body.ptX(1.08f, c * 0.2f), body.ptY(1.08f, c * 0.2f))
                    .add(body.ptX(1.06f, -c * 0.5f), body.ptY(1.06f, -c * 0.5f))
                    .shapeGradDetail(hi, lo, body.ptX(1.25f, 0f), body.ptY(1.25f, 0f), body.ptX(1.04f, 0f), body.ptY(1.04f, 0f))
                p.begin()
                    .add(body.ptX(1.1f, c * 0.6f), body.ptY(1.1f, c * 0.6f))
                    .add(body.ptX(0.92f, c * 0.83f), body.ptY(0.92f, c * 0.83f))
                    .add(body.ptX(0.74f, c * 0.8f), body.ptY(0.74f, c * 0.8f))
                    .add(body.ptX(0.84f, c * 0.5f), body.ptY(0.84f, c * 0.5f))
                    .add(body.ptX(1.02f, c * 0.42f), body.ptY(1.02f, c * 0.42f))
                    .shapeGradDetail(hi, lo, body.ptX(1.02f, c * 0.8f), body.ptY(1.02f, c * 0.8f), body.ptX(0.8f, c * 0.5f), body.ptY(0.8f, c * 0.5f))
            }
            if (!p.ink && L.rim != 0) {
                // Neon rim down the back contour.
                g.blend(Gfx.Blend.ADD)
                val rw = ActorPaint.RIM_W
                p.detail(body.ptX(0.12f, -w * 0.6f), body.ptY(0.12f, -w * 0.6f), body.ptX(0.5f, -c * 0.7f), body.ptY(0.5f, -c * 0.7f), rw, L.rim)
                p.detail(body.ptX(0.5f, -c * 0.7f), body.ptY(0.5f, -c * 0.7f), body.ptX(0.92f, -c * 0.86f), body.ptY(0.92f, -c * 0.86f), rw, L.rim)
                p.detail(body.ptX(0.92f, -c * 0.86f), body.ptY(0.92f, -c * 0.86f), body.ptX(1.16f, -c * 0.74f), body.ptY(1.16f, -c * 0.74f), rw, L.rim)
                g.blend(Gfx.Blend.NORMAL)
            }
            if (!p.ink) {
                when (z) {
                    Zone.TOWER -> {
                        // The tie: a narrow plate down the chest front in the accent.
                        p.begin()
                            .add(body.ptX(1.12f, c * 0.62f), body.ptY(1.12f, c * 0.62f))
                            .add(body.ptX(0.92f, c * 0.84f), body.ptY(0.92f, c * 0.84f))
                            .add(body.ptX(0.6f, c * 0.78f), body.ptY(0.6f, c * 0.78f))
                            .add(body.ptX(0.64f, c * 0.62f), body.ptY(0.64f, c * 0.62f))
                            .add(body.ptX(0.92f, c * 0.66f), body.ptY(0.92f, c * 0.66f))
                            .shapeGradDetail(ActorPaint.light(trim), ActorPaint.shade(trim), body.ptX(1.1f, c * 0.7f), body.ptY(1.1f, c * 0.7f), body.ptX(0.6f, c * 0.7f), body.ptY(0.6f, c * 0.7f))
                    }
                    Zone.LABS, Zone.MAGMA -> {
                        // Trim along the rim of the yoke: lab green, or heat-shield brass.
                        p.detail(body.ptX(1.2f, -c * 0.6f), body.ptY(1.2f, -c * 0.6f), body.ptX(1.12f, c * 0.62f), body.ptY(1.12f, c * 0.62f), 0.034f, trim)
                    }
                    Zone.METRO -> {
                        // Riot stripe round the chest.
                        p.begin()
                            .add(body.ptX(0.86f, -c * 0.86f), body.ptY(0.86f, -c * 0.86f))
                            .add(body.ptX(0.98f, c * 0.84f), body.ptY(0.98f, c * 0.84f))
                            .add(body.ptX(0.86f, c * 0.86f), body.ptY(0.86f, c * 0.86f))
                            .add(body.ptX(0.74f, -c * 0.8f), body.ptY(0.74f, -c * 0.8f))
                            .shapeDetail(trim)
                    }
                    else -> Unit
                }
            }
            heavyHelm(e, dir, z, helm, acc, hot, hx, hy, r)
        }
        body.headRim(hx, hy, r * 1.05f, look.rim)
        eyesAt(hx + r * 0.7f * dir, hy - r * 0.12f)
        p.twoPass {
            if (armed && showGun) body.gun(3, gunX, gunY, gunUp, acc, spin = if (e.state == EnemyState.AIM) f.t * 40f else 0f, scale = 0.85f)
            p.lightFrom(dir)
            // The near forearm and a big gauntlet on the cannon.
            p.bone(k.armF.jx, k.armF.jy, k.armF.ex, k.armF.ey, aw * 1.25f, aw * 1.15f, Col.lerp(shell, 0xFFFFFFFF.toInt(), 0.1f), sep = true)
            if (L.rim != 0) p.boneRim(k.armF.jx, k.armF.jy, k.armF.ex, k.armF.ey, aw * 1.25f, aw * 1.15f, body.rimX, body.rimY, L.rim)
            val gx = k.armF.ex
            val gy = k.armF.ey
            p.ball(gx, gy, aw * 0.62f, L.gloves)
        }
        if (e.state == EnemyState.AIM && armed) f.glowDot(body.muzzleX, body.muzzleY, 0.04f, pal.laser, p.alphaMul)
    }

    /** A pillar of a leg: armoured thigh tapering into the knee, a plated shin, a big block boot. */
    private fun pillarLeg(l: Limb, lw: Float, color: Int, near: Boolean, rim: Int) {
        p.bone(l.ax, l.ay, l.jx, l.jy, lw * 1.95f, lw * 1.2f, color, near, lit = near)
        p.bone(l.jx, l.jy, l.ex, l.ey, lw * 1.35f, lw * 1.2f, color, near, bulge = lw * 1.45f, lit = near)
        if (rim != 0) p.boneRim(l.ax, l.ay, l.jx, l.jy, lw * 1.95f, lw * 1.2f, body.rimX, body.rimY, rim)
        if (near && p.shading) {
            // The knee: one seam across the top of the shin plate.
            val dx = l.ex - l.jx
            val dy = l.ey - l.jy
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
            val nx = -dy / len * lw * 0.6f
            val ny = dx / len * lw * 0.6f
            val cx = l.jx + dx * 0.14f
            val cy = l.jy + dy * 0.14f
            p.detail(cx - nx, cy - ny, cx + nx, cy + ny, 0.02f, ActorPaint.shade(ActorPaint.shade(color)))
        }
        // Block boot: a big slab of sole under the ankle.
        val d = k.dir.toFloat()
        val s = k.hs * 1.3f
        val bx = l.ex
        val by = l.ey + 0.045f * k.hs
        p.begin()
            .add(bx - 0.1f * s * d, by)
            .add(bx - 0.1f * s * d, by - 0.1f * s)
            .add(bx + 0.05f * s * d, by - 0.12f * s)
            .add(bx + 0.18f * s * d, by - 0.06f * s)
            .add(bx + 0.2f * s * d, by)
            .shapeLit(if (near) 0xFF22222E.toInt() else 0xFF121218.toInt(), bx, by - 0.12f * s, bx, by, sep = near)
    }

    /** The heavy's helmet: a small rounded dome sunk in the yoke, one slot visor glowing in the accent. */
    private fun heavyHelm(e: Enemy, dir: Int, z: Zone, helm: Int, acc: Int, hot: Int, hx: Float, hy: Float, r: Float) {
        val d = dir.toFloat()
        val R = r * 1.14f
        if (z == Zone.HELL) {
            // Thick curved horns of dark bone bolted to the helm, their tips glowing warm.
            demonHorn(hx - R * 0.05f * d, hy + R * 0.25f, R * 0.95f, -d, 0xFF3E3430.toInt(), false, 0xFFFF7A30.toInt(), 1.7f)
            demonHorn(hx - R * 0.1f * d, hy + R * 0.2f, R * 1.05f, d, 0xFF5E5048.toInt(), true, 0xFFFF8A40.toInt(), 1.7f)
        }
        p.begin()
            .add(hx - R * 0.9f * d, hy + R * 0.55f)
            .add(hx - R * 0.95f * d, hy - R * 0.15f)
            .add(hx - R * 0.62f * d, hy - R * 0.78f)
            .add(hx + R * 0.1f * d, hy - R * 0.98f)
            .add(hx + R * 0.72f * d, hy - R * 0.68f)
            .add(hx + R * 1.0f * d, hy - R * 0.05f)
            .add(hx + R * 0.96f * d, hy + R * 0.55f)
            .shapeLit(helm, hx + R * 0.3f * d, hy - R * 1.0f, hx - R * 0.5f * d, hy + R * 0.6f)
        if (z == Zone.MINES) {
            // Hard hat: a yellow dome with a brim, and the lamp.
            val hat = 0xFFF2C020.toInt()
            p.begin()
                .add(hx - R * 1.1f * d, hy - R * 0.32f)
                .add(hx - R * 0.8f * d, hy - R * 0.95f)
                .add(hx + R * 0.05f * d, hy - R * 1.22f)
                .add(hx + R * 0.8f * d, hy - R * 0.9f)
                .add(hx + R * 1.22f * d, hy - R * 0.4f)
                .add(hx + R * 1.2f * d, hy - R * 0.28f)
                .add(hx - R * 1.1f * d, hy - R * 0.22f)
                .shapeLit(hat, hx + R * 0.3f * d, hy - R * 1.2f, hx - R * 0.4f * d, hy - R * 0.2f, sep = true)
            if (!p.ink) {
                p.begin()
                    .add(hx + R * 0.72f * d, hy - R * 0.8f).add(hx + R * 1.02f * d, hy - R * 0.72f)
                    .add(hx + R * 1.02f * d, hy - R * 0.4f).add(hx + R * 0.72f * d, hy - R * 0.42f)
                    .shapeDetail(0xFF2A2A30.toInt())
                if (e.alive) f.glowDot(hx + R * 0.98f * d, hy - R * 0.58f, 0.045f, 0xFFFFF4C0.toInt(), p.alphaMul)
            }
        }
        if (!p.ink) {
            // Slot visor: a narrow recessed black slot, the accent burning in it.
            p.begin()
                .add(hx + R * 0.12f * d, hy - R * 0.3f)
                .add(hx + R * 1.0f * d, hy - R * 0.32f)
                .add(hx + R * 1.0f * d, hy + R * 0.02f)
                .add(hx + R * 0.14f * d, hy + R * 0.02f)
                .shapeDetail(0xFF06060A.toInt())
            val on = if (e.alive || e.state == EnemyState.CHOKED) 1f else 0.25f
            emissive(hx + R * 0.3f * d, hy - R * 0.14f, hx + R * 0.94f * d, hy - R * 0.15f, 0.034f, Col.fade(acc, on), Col.fade(hot, on))
        }
    }

    private val tailX = FloatArray(16)
    private val tailY = FloatArray(16)

    /**
     * A ribbon through tailX/Y[from until from + n]: [w0] wide at the root tapering to a point,
     * flowing cloth (headband tails, sash ends, the demon's tail uses bones instead).
     */
    private fun ribbon(from: Int, n: Int, w0: Float, color: Int) {
        p.begin()
        for (i in 0 until n) {
            val j = from + i
            val a = min(j + 1, from + n - 1)
            val b = max(j - 1, from)
            val dx = tailX[a] - tailX[b]
            val dy = tailY[a] - tailY[b]
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
            val hw = w0 * 0.5f * (1f - i / (n - 1f))
            p.add(tailX[j] - dy / len * hw, tailY[j] + dx / len * hw)
        }
        for (i in n - 1 downTo 0) {
            val j = from + i
            val a = min(j + 1, from + n - 1)
            val b = max(j - 1, from)
            val dx = tailX[a] - tailX[b]
            val dy = tailY[a] - tailY[b]
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
            val hw = w0 * 0.5f * (1f - i / (n - 1f))
            p.add(tailX[j] + dy / len * hw, tailY[j] - dx / len * hw)
        }
        p.shape(color)
        if (p.shading) {
            // A fold of light down the middle of the cloth.
            for (i in 0 until n - 2) {
                p.detail(tailX[from + i], tailY[from + i], tailX[from + i + 1], tailY[from + i + 1], w0 * 0.22f * (1f - i / (n - 1f) * 0.7f), ActorPaint.light(color))
            }
        }
    }

    /** Streams a cloth tail of [n] links of [seg] from (x, y) into tailX/Y[from...], trailing the run. */
    private fun streamTail(from: Int, n: Int, x0: Float, y0: Float, seg: Float, dir: Int, speed: Float, phase: Float, droop: Float) {
        var x = x0
        var y = y0
        tailX[from] = x; tailY[from] = y
        for (i in 1 until n) {
            val a = Rig.mix(droop, 1.4f, speed) + sin(f.t * 11f - i * 1.1f + phase) * (0.1f + 0.07f * i)
            x += sin(a) * seg * -dir
            y += cos(a) * seg
            tailX[from + i] = x; tailY[from + i] = y
        }
    }

    /**
     * The ninja: lean and sharp. Hood and mask with one eye slit, a steel brow plate on the
     * headband and its two tails streaming behind, a crossed jacket, the sash in the accent,
     * wrapped shins and forearms, an empty lacquered scabbard on the back and the katana.
     */
    private fun ninja(e: Enemy, dir: Int, zone: Zone, pal: Palette, armed: Boolean) {
        // Indigo-black cloth with the full lamp-to-violet range, so he isn't a flat black stick.
        val main = 0xFF32324E.toInt()
        val lit = 0xFF6A6A98.toInt()
        val far = 0xFF141422.toInt()
        // The one accent: the zone colour, a notch dimmer than anything that glows.
        val band = Col.lerp(if (zone == Zone.HELL) 0xFFFFB020.toInt() else pal.enemyAccent, main, 0.18f)
        val wrap = 0xFF44446A.toInt()
        val L = look
        // Jacket a step lighter than the trousers, so the wedge of the torso reads.
        L.torso = main; L.torsoLit = lit; L.legs = 0xFF1C1C2E.toInt(); L.legsFar = far
        L.arms = main; L.armsFar = far; L.boots = 0xFF14141E.toInt(); L.gloves = 0xFF1A1A28.toInt(); L.skin = 0xFFE8C8A8.toInt()
        L.rim = rimOf(pal, 0.8f); L.legW = 0.86f; L.armW = 0.86f
        val hx = k.headX
        val hy = k.headY
        val r = k.headR
        val d = dir.toFloat()
        val w = k.waistD
        val c = k.chestD * 1.08f
        val speed = min(1f, abs(e.vx) / 3f)
        // Two thin headband ribbons trailing back and down, a little apart; the sash end hanging at the hip.
        streamTail(0, 5, hx - r * 0.9f * d, hy - r * 0.52f, 0.07f, dir, speed, e.id.toFloat(), 0.95f)
        streamTail(5, 5, hx - r * 0.88f * d, hy - r * 0.42f, 0.064f, dir, speed, e.id + 2.2f, 0.62f)
        streamTail(10, 3, body.ptX(0.08f, w * 0.3f), body.ptY(0.08f, w * 0.3f), 0.075f, dir, speed * 0.5f, e.id + 4.1f, 0.12f)
        p.lightFrom(dir)
        p.twoPass {
            ribbon(5, 5, 0.026f, Col.mul(band, 0.72f))
            ribbon(0, 5, 0.03f, band)
            body.arm(k.armB, L, far = true)
            // The far arm swings out past the back: it carries the rim there.
            if (L.rim != 0) p.boneRim(k.armB.ax, k.armB.ay, k.armB.jx, k.armB.jy, k.limbW * 0.93f, k.limbW * 0.67f, body.rimX, body.rimY, Col.fade(L.rim, 0.7f))
            body.leg(k.legB, L, far = true)
            // The near leg sits inside the silhouette: no rim, or it reads as a stripe.
            val rim = L.rim
            L.rim = 0
            body.leg(k.legF, L, far = false)
            L.rim = rim
            if (p.shading) wraps(k.legF, wrap)
            // Wedge torso: broad shoulders and lats, the waist cinched by the sash.
            p.begin()
                .add(body.ptX(-0.1f, -w * 0.46f), body.ptY(-0.1f, -w * 0.46f))
                .add(body.ptX(-0.1f, w * 0.44f), body.ptY(-0.1f, w * 0.44f))
                .add(body.ptX(0.3f, w * 0.36f), body.ptY(0.3f, w * 0.36f))
                .add(body.ptX(0.72f, c * 0.54f), body.ptY(0.72f, c * 0.54f))
                .add(body.ptX(1.0f, c * 0.4f), body.ptY(1.0f, c * 0.4f))
                .add(body.ptX(1.07f, -c * 0.1f), body.ptY(1.07f, -c * 0.1f))
                .add(body.ptX(0.98f, -c * 0.5f), body.ptY(0.98f, -c * 0.5f))
                .add(body.ptX(0.66f, -c * 0.62f), body.ptY(0.66f, -c * 0.62f))
                .add(body.ptX(0.3f, -w * 0.38f), body.ptY(0.3f, -w * 0.38f))
                .shapeLit(main, body.ptX(1.05f, c * 0.5f), body.ptY(1.05f, c * 0.5f), body.ptX(0.2f, -w * 0.5f), body.ptY(0.2f, -w * 0.5f), mid = 0.4f)
            if (!p.ink) {
                // Crossed jacket: the lapel from the shoulder down to the sash, lit.
                if (p.shading) {
                    p.begin()
                        .add(body.ptX(1.04f, -c * 0.02f), body.ptY(1.04f, -c * 0.02f))
                        .add(body.ptX(1.0f, c * 0.3f), body.ptY(1.0f, c * 0.3f))
                        .add(body.ptX(0.3f, w * 0.36f), body.ptY(0.3f, w * 0.36f))
                        .add(body.ptX(0.3f, w * 0.12f), body.ptY(0.3f, w * 0.12f))
                        .shapeDetail(Col.alpha(lit, 0.55f))
                }
                // Sash cinching the waist, its knot at the front hip.
                p.begin()
                    .add(body.ptX(0.26f, -w * 0.42f), body.ptY(0.26f, -w * 0.42f))
                    .add(body.ptX(0.27f, w * 0.4f), body.ptY(0.27f, w * 0.4f))
                    .add(body.ptX(0.06f, w * 0.44f), body.ptY(0.06f, w * 0.44f))
                    .add(body.ptX(0.04f, -w * 0.46f), body.ptY(0.04f, -w * 0.46f))
                    .shapeGradDetail(ActorPaint.light(band), ActorPaint.shade(band), body.ptX(0.27f, 0f), body.ptY(0.27f, 0f), body.ptX(0.04f, 0f), body.ptY(0.04f, 0f))
                if (L.rim != 0) {
                    // Neon rim down the back contour of the wedge.
                    g.blend(Gfx.Blend.ADD)
                    p.detail(body.ptX(0.72f, -c * 0.61f), body.ptY(0.72f, -c * 0.61f), body.ptX(0.98f, -c * 0.5f), body.ptY(0.98f, -c * 0.5f), ActorPaint.RIM_W, L.rim)
                    p.detail(body.ptX(0.98f, -c * 0.5f), body.ptY(0.98f, -c * 0.5f), body.ptX(1.07f, -c * 0.1f), body.ptY(1.07f, -c * 0.1f), ActorPaint.RIM_W, L.rim)
                    g.blend(Gfx.Blend.NORMAL)
                }
            }
            ribbon(10, 3, 0.034f, Col.mul(band, 0.85f))
            p.disc(body.ptX(0.16f, w * 0.34f), body.ptY(0.16f, w * 0.34f), 0.03f, band)
            body.neck(main)
            // Hood and the mask's jaw.
            p.ball(hx, hy, r, main)
            p.ball(hx + r * 0.3f * d, hy + r * 0.45f, r * 0.62f, main)
            if (!p.ink) {
                // The eye slit: a sliver of skin, one hard narrowed eye.
                p.begin()
                    .add(hx + r * 0.22f * d, hy - r * 0.34f).add(hx + r * 1.02f * d, hy - r * 0.38f)
                    .add(hx + r * 0.98f * d, hy + r * 0.06f).add(hx + r * 0.26f * d, hy + r * 0.04f)
                    .shapeDetail(L.skin)
                p.detail(hx + r * 0.5f * d, hy - r * 0.08f, hx + r * 0.9f * d, hy - r * 0.2f, 0.036f, 0xFF0A0A10.toInt())
                // Headband and its steel brow plate.
                p.detail(hx - r * 0.98f * d, hy - r * 0.55f, hx + r * 0.9f * d, hy - r * 0.6f, 0.055f, band)
                p.begin()
                    .add(hx + r * 0.42f * d, hy - r * 0.8f).add(hx + r * 0.9f * d, hy - r * 0.8f)
                    .add(hx + r * 0.98f * d, hy - r * 0.44f).add(hx + r * 0.46f * d, hy - r * 0.42f)
                    .shapeGradDetail(0xFFC8D0DC.toInt(), 0xFF5A6274.toInt(), hx + r * 0.7f * d, hy - r * 0.8f, hx + r * 0.7f * d, hy - r * 0.42f)
            }
        }
        body.headRim(hx, hy, r, L.rim)
        eyesAt(hx + r * 0.7f * d, hy - r * 0.1f)
        p.twoPass {
            if (armed && bladeA < 90f) katana(k.armF.ex, k.armF.ey, dir, bladeA, band)
            body.arm(k.armF, L, far = false)
            if (p.shading) wraps(k.armF, wrap)
        }
    }

    /** Cloth bindings: a few clean bands across the lower half of a limb (fill pass). */
    private fun wraps(l: Limb, color: Int) {
        val dx = l.ex - l.jx
        val dy = l.ey - l.jy
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val nx = -dy / len * 0.05f * k.hs
        val ny = dx / len * 0.05f * k.hs
        for (i in 0 until 3) {
            val t = 0.36f + i * 0.2f
            val cx = l.jx + dx * t
            val cy = l.jy + dy * t
            p.detail(cx - nx + dx * 0.06f, cy - ny + dy * 0.06f, cx + nx - dx * 0.06f, cy + ny - dy * 0.06f, 0.026f, color)
            p.detail(cx - nx + dx * 0.075f, cy - ny + dy * 0.075f, cx + nx - dx * 0.045f, cy + ny - dy * 0.045f, 0.008f, ActorPaint.light(color))
        }
    }

    private val bladePts = FloatArray(4)

    /** Point on the katana's curved centreline, [t] of the blade from the habaki; into bladePts[0..1]. */
    private fun bladeAt(hx: Float, hy: Float, dx: Float, dy: Float, nx: Float, ny: Float, len: Float, t: Float, off: Float) {
        val sag = 0.05f * t * t
        bladePts[0] = hx + dx * len * t - nx * sag + nx * off
        bladePts[1] = hy + dy * len * t - ny * sag + ny * off
    }

    /**
     * Katana from the hand; [a] = blade angle from horizontal-forward (radians, + = up). A long
     * wrapped hilt with a pommel cap, an oval gold tsuba, and a gently curved blade: dark steel
     * along the spine, a pale temper line and a bright cutting edge rising to the point.
     */
    private fun katana(hx: Float, hy: Float, dir: Int, a: Float, wrap: Int) {
        val c = cos(a)
        val s = sin(a)
        val dx = c * dir
        val dy = -s
        // Edge side: below the blade when it's held level.
        val nx = s * dir
        val ny = c
        val len = 0.68f
        // Hilt: wrap, then the pommel.
        val px = hx - dx * 0.19f
        val py = hy - dy * 0.19f
        p.seg(px, py, hx + dx * 0.02f, hy + dy * 0.02f, 0.042f, 0xFF16121A.toInt())
        p.seg(px - dx * 0.01f, py - dy * 0.01f, px + dx * 0.02f, py + dy * 0.02f, 0.05f, 0xFFB8903A.toInt())
        if (p.shading) {
            for (i in 0 until 3) {
                val t = 0.035f + i * 0.045f
                val qx = px + dx * t
                val qy = py + dy * t
                p.detail(qx - nx * 0.016f, qy - ny * 0.016f, qx + dx * 0.02f + nx * 0.016f, qy + dy * 0.02f + ny * 0.016f, 0.012f, Col.mul(wrap, 0.9f))
            }
        }
        // The tsuba: an oval guard across the blade.
        val gx = hx + dx * 0.035f
        val gy = hy + dy * 0.035f
        p.seg(gx + nx * 0.06f, gy + ny * 0.06f, gx - nx * 0.06f, gy - ny * 0.06f, 0.036f, 0xFFC8A040.toInt())
        if (p.shading) p.detail(gx + nx * 0.05f - dx * 0.008f, gy + ny * 0.05f - dy * 0.008f, gx - nx * 0.05f - dx * 0.008f, gy - ny * 0.05f - dy * 0.008f, 0.012f, 0xFFFFE8A0.toInt())
        // Blade polygon: spine side out, round the point, back along the edge.
        val bx = gx + dx * 0.02f
        val by = gy + dy * 0.02f
        p.begin()
        for (i in 0..4) {
            val t = i / 4f
            bladeAt(bx, by, dx, dy, nx, ny, len, t, -0.017f + 0.004f * t)
            p.add(bladePts[0], bladePts[1])
        }
        bladeAt(bx, by, dx, dy, nx, ny, len, 1.07f, -0.01f)
        val tipX = bladePts[0]
        val tipY = bladePts[1]
        p.add(tipX, tipY)
        for (i in 4 downTo 0) {
            val t = i / 4f * 0.94f
            bladeAt(bx, by, dx, dy, nx, ny, len, t, 0.018f - 0.004f * t)
            p.add(bladePts[0], bladePts[1])
        }
        p.shape(0xFF7A8498.toInt())
        if (!p.ink) {
            // One bright edge along the whole blade, catching the lamp.
            bladeAt(bx, by, dx, dy, nx, ny, len, 0f, 0.011f)
            var ex0 = bladePts[0]
            var ey0 = bladePts[1]
            for (i in 1..4) {
                bladeAt(bx, by, dx, dy, nx, ny, len, i / 4f * 0.96f, 0.011f - 0.003f * i / 4f)
                p.detail(ex0, ey0, bladePts[0], bladePts[1], 0.011f, 0xFFF4F8FF.toInt())
                ex0 = bladePts[0]
                ey0 = bladePts[1]
            }
            // The habaki collar.
            p.detail(bx - nx * 0.018f, by - ny * 0.018f, bx + nx * 0.018f, by + ny * 0.018f, 0.022f, 0xFFE0C060.toInt())
            if (p.shading) {
                g.blend(Gfx.Blend.ADD)
                g.glow(tipX, tipY, 0.07f, p.c(0x70FFFFFF))
                g.blend(Gfx.Blend.NORMAL)
            }
        }
    }

    /**
     * A split in the hide at (x, y) along the spine: an irregular, pointed rift, molten and
     * glowing outside Hell (a hot core, a soft bloom), a dark sculpted groove in it.
     */
    private fun lavaWound(x: Float, y: Float, lava: Boolean, glow: Int, hot: Int, groove: Int) {
        if (p.ink) return
        val ux = k.ux
        val uy = k.uy
        val nx = k.nx
        val ny = k.ny
        val s = k.hs * 1.5f
        fun wx(a: Float, b: Float) = x + (ux * a + nx * b) * s
        fun wy(a: Float, b: Float) = y + (uy * a + ny * b) * s
        if (lava && p.shading) {
            g.blend(Gfx.Blend.ADD)
            g.glow(x, y, 0.16f * s, p.c(Col.alpha(glow, 0.35f)))
            g.blend(Gfx.Blend.NORMAL)
        }
        p.begin()
            .add(wx(0.15f, 0.02f), wy(0.15f, 0.02f))
            .add(wx(0.06f, 0.035f), wy(0.06f, 0.035f))
            .add(wx(0.0f, 0.02f), wy(0.0f, 0.02f))
            .add(wx(-0.07f, 0.04f), wy(-0.07f, 0.04f))
            .add(wx(-0.14f, -0.01f), wy(-0.14f, -0.01f))
            .add(wx(-0.05f, -0.02f), wy(-0.05f, -0.02f))
            .add(wx(0.03f, -0.035f), wy(0.03f, -0.035f))
            .add(wx(0.09f, -0.015f), wy(0.09f, -0.015f))
            .shapeDetail(if (lava) glow else groove)
        if (lava) {
            p.begin()
                .add(wx(0.1f, 0.01f), wy(0.1f, 0.01f))
                .add(wx(0.0f, 0.012f), wy(0.0f, 0.012f))
                .add(wx(-0.09f, 0.01f), wy(-0.09f, 0.01f))
                .add(wx(0.0f, -0.014f), wy(0.0f, -0.014f))
                .shapeDetail(hot)
        }
    }

    /**
     * The demon: hunched and heavy-shouldered, a trapezius hump the head juts forward from,
     * sweeping horns, a long jaw and burning eye slits, spines down the back, long clawed arms,
     * digitigrade legs on cloven hooves and a whipping tail with a spade tip. Charcoal hide
     * split with glowing lava outside Hell; in Hell a saturated red.
     */
    private fun demon(e: Enemy, dir: Int, zone: Zone, pal: Palette) {
        val hell = zone == Zone.HELL
        val main = if (hell) 0xFFAE121C.toInt() else 0xFF3A3036.toInt()
        val lit = if (hell) 0xFFFF6A50.toInt() else 0xFF74626A.toInt()
        val dark = if (hell) 0xFF60060E.toInt() else 0xFF100C10.toInt()
        val glow = if (hell) 0xFFFFC030.toInt() else 0xFFFF6A10.toInt()
        val hot = 0xFFFFF0B0.toInt()
        val groove = Col.lerp(main, dark, 0.7f)
        val bone = 0xFFE8D8C0.toInt()
        val lava = !hell
        val L = look
        L.torso = main; L.torsoLit = lit; L.legs = main; L.legsFar = Col.lerp(main, dark, 0.35f)
        L.arms = main; L.armsFar = Col.lerp(main, dark, 0.55f); L.boots = dark; L.gloves = main; L.skin = main
        // A warm ember rim, not the cool neon: he's lit by his own fire.
        L.rim = Col.alpha(0xFFFF9A50.toInt(), 0.9f); L.legW = 1.05f; L.armW = 1.08f
        val hx = k.headX
        val hy = k.headY
        val r = k.headR * 1.12f
        val d = dir.toFloat()
        val c = k.chestD * 1.2f
        val w = k.waistD
        // The tail: an S-curve whipping behind the hips.
        val t0x = k.hipX - 0.08f * d
        val t0y = k.hipY + 0.02f
        for (i in 0..5) {
            val q = i.toFloat()
            val whip = sin(f.t * 5f + e.id - q * 0.8f) * 0.035f * q
            tailX[i] = t0x - d * 0.13f * q
            tailY[i] = t0y + 0.11f * q - 0.03f * q * q + whip
        }
        val armed = e.alive
        hoofSolve(k.legF, 0)
        hoofSolve(k.legB, 1)
        p.lightFrom(dir)
        p.twoPass {
            for (i in 0 until 5) {
                p.bone(tailX[i], tailY[i], tailX[i + 1], tailY[i + 1], 0.085f - i * 0.012f, 0.073f - i * 0.012f, L.legsFar)
                p.boneRim(tailX[i], tailY[i], tailX[i + 1], tailY[i + 1], 0.085f - i * 0.012f, 0.073f - i * 0.012f, body.rimX, body.rimY, L.rim)
            }
            // The spade.
            val ux0 = tailX[5] - tailX[4]
            val uy0 = tailY[5] - tailY[4]
            val ul = sqrt(ux0 * ux0 + uy0 * uy0).coerceAtLeast(1e-4f)
            val ux = ux0 / ul
            val uy = uy0 / ul
            val ex = tailX[5]
            val ey = tailY[5]
            p.begin()
                .add(ex + ux * 0.16f, ey + uy * 0.16f)
                .add(ex - uy * 0.085f - ux * 0.04f, ey + ux * 0.085f - uy * 0.04f)
                .add(ex + ux * 0.015f, ey + uy * 0.015f)
                .add(ex + uy * 0.085f - ux * 0.04f, ey - ux * 0.085f - uy * 0.04f)
                .shape(if (hell) main else Col.lerp(main, dark, 0.3f))
            // Far horn, far arm and leg.
            demonHorn(hx - r * 0.1f * d, hy + r * 0.12f, r * 0.8f, -d, Col.mul(bone, 0.62f), false)
            body.arm(k.armB, L, far = true, hand = false)
            claws(k.armB, dir, Col.mul(bone, 0.75f))
            hoofLeg(k.legB, 1, L.legsFar, false)
            hoofLeg(k.legF, 0, L.legs, true)
            // Three big spines of dark bone down the back, growing toward the shoulders.
            val spine = if (hell) 0xFF4A2A26.toInt() else 0xFF6A5A50.toInt()
            for (i in 0 until 3) {
                val a = 0.36f + i * 0.26f
                val sz = 0.11f + i * 0.035f
                val bx = body.ptX(a, -c * 0.44f)
                val by = body.ptY(a, -c * 0.44f)
                val tx = bx - k.nx * sz * 1.35f + k.ux * sz * 0.45f
                val ty = by - k.ny * sz * 1.35f + k.uy * sz * 0.45f
                p.begin()
                    .add(bx + k.ux * sz * 0.5f, by + k.uy * sz * 0.5f)
                    .add(tx, ty)
                    .add(bx - k.ux * sz * 0.5f, by - k.uy * sz * 0.5f)
                    .shapeLit(spine, bx + k.ux * sz * 0.5f, by + k.uy * sz * 0.5f, bx - k.ux * sz * 0.5f, by - k.uy * sz * 0.5f)
                if (!p.ink && L.rim != 0 && p.shading) {
                    g.blend(Gfx.Blend.ADD)
                    p.detail(bx + k.ux * sz * 0.5f, by + k.uy * sz * 0.5f, tx, ty, ActorPaint.RIM_W * 0.8f, Col.fade(L.rim, 0.6f))
                    g.blend(Gfx.Blend.NORMAL)
                }
            }
            body.torso(L, chest = 1.2f)
            if (p.shading) {
                // Sculpt: the pectoral shelf in the lamp, the belly and ribs falling into shadow.
                p.begin()
                    .add(body.ptX(1.0f, -c * 0.05f), body.ptY(1.0f, -c * 0.05f))
                    .add(body.ptX(0.98f, c * 0.32f), body.ptY(0.98f, c * 0.32f))
                    .add(body.ptX(0.74f, c * 0.54f), body.ptY(0.74f, c * 0.54f))
                    .add(body.ptX(0.64f, c * 0.3f), body.ptY(0.64f, c * 0.3f))
                    .shapeDetail(Col.alpha(lit, 0.55f))
                p.begin()
                    .add(body.ptX(0.66f, c * 0.5f), body.ptY(0.66f, c * 0.5f))
                    .add(body.ptX(0.4f, w * 0.5f), body.ptY(0.4f, w * 0.5f))
                    .add(body.ptX(0.1f, w * 0.48f), body.ptY(0.1f, w * 0.48f))
                    .add(body.ptX(0.2f, w * 0.1f), body.ptY(0.2f, w * 0.1f))
                    .add(body.ptX(0.55f, c * 0.2f), body.ptY(0.55f, c * 0.2f))
                    .shapeShade(main)
                p.detail(body.ptX(0.66f, c * 0.52f), body.ptY(0.66f, c * 0.52f), body.ptX(0.6f, c * 0.18f), body.ptY(0.6f, c * 0.18f), 0.022f, dark)
            }
            // The hump of the shoulders the head juts forward from.
            p.ball(body.ptX(0.98f, -c * 0.18f), body.ptY(0.98f, -c * 0.18f), c * 0.5f, main)
            body.headRim(body.ptX(0.98f, -c * 0.18f), body.ptY(0.98f, -c * 0.18f), c * 0.5f, L.rim)
            // One molten wound split open in the ribs, glowing through the hide.
            if (armed) lavaWound(body.ptX(0.55f, -c * 0.08f), body.ptY(0.55f, -c * 0.08f), lava, glow, hot, groove)
            if (p.shading) p.detail(body.ptX(1.12f, -c * 0.5f), body.ptY(1.12f, -c * 0.5f), body.ptX(1.2f, -c * 0.02f), body.ptY(1.2f, -c * 0.02f), 0.03f, lit)
            p.seg(k.neckX, k.neckY + 0.04f, mixf(k.neckX, hx, 0.7f), mixf(k.neckY, hy, 0.7f), 0.13f * k.hs, main)
            demonHead(e, hx, hy, r, d, main, lit, dark, glow, hot, bone)
        }
        body.headRim(hx - r * 0.1f * d, hy, r * 0.9f, L.rim)
        eyesAt(hx + r * 0.78f * d, hy - r * 0.26f)
        p.twoPass {
            p.lightFrom(dir)
            body.arm(k.armF, L, far = false, hand = false)
            claws(k.armF, dir, bone)
        }
        if (e.state == EnemyState.AIM && !p.ink) {
            val fs = f.w.floors[e.floor]
            val tAim = (e.stateTime / Heat.aimTime(fs?.plan?.heat ?: 1f)).coerceIn(0f, 1f)
            val rr = 0.07f + 0.12f * tAim
            val fx = k.armF.ex
            val fy = k.armF.ey
            g.fillCircle(fx, fy, rr * 2.2f, p.c(0x40FF6A10))
            g.fillCircle(fx, fy, rr, p.c(0xFFFFA030.toInt()))
            g.fillCircle(fx, fy, rr * 0.55f, p.c(0xFFFFF0B0.toInt()))
            flames(fx, fy - rr * 0.5f, dir, 0xFFFF8A20.toInt(), e.id + 3)
        }
    }

    /** The horn's spine (u forward, v down, in head radii) and half-width at each point. */
    private val hornU = floatArrayOf(0.12f, -0.3f, -0.85f, -1.35f, -1.62f, -1.7f)
    private val hornV = floatArrayOf(-0.78f, -1.3f, -1.62f, -1.78f, -2.05f, -2.4f)
    private val hornW = floatArrayOf(0.3f, 0.25f, 0.19f, 0.13f, 0.07f, 0f)

    /** One horn from its root at (x, y): swept back over the shoulders, the tip hooking up. */
    private fun demonHorn(x: Float, y: Float, r: Float, d: Float, color: Int, rings: Boolean, tip: Int = 0, wide: Float = 1f) {
        val n = hornU.size
        p.begin()
        for (side in 0..1) {
            for (j in 0 until n) {
                val i = if (side == 0) j else n - 1 - j
                val a = min(i + 1, n - 1)
                val b = max(i - 1, 0)
                val tu = hornU[a] - hornU[b]
                val tv = hornV[a] - hornV[b]
                val tl = sqrt(tu * tu + tv * tv)
                val sgn = if (side == 0) 1f else -1f
                val ou = -tv / tl * hornW[i] * sgn * wide
                val ov = tu / tl * hornW[i] * sgn * wide
                if (side == 1 && i == n - 1) continue
                p.add(x + (hornU[i] + ou) * r * d, y + (hornV[i] + ov) * r)
            }
        }
        p.shapeLit(color, x, y - r * 2.4f, x + hornU[2] * r * d, y + (hornV[2] + 0.4f) * r)
        if (tip != 0 && !p.ink) {
            // Warm tips: the last third of the horn heats toward the point.
            for (i in 3 until n - 1) {
                val t = (i - 2f) / (n - 2f)
                p.detail(
                    x + hornU[i] * r * d, y + hornV[i] * r, x + hornU[i + 1] * r * d, y + hornV[i + 1] * r,
                    max(hornW[i] * r * 1.4f, 0.012f), Col.lerp(color, tip, 0.35f + t * 0.5f),
                )
            }
        }
        if (!rings || !p.shading) return
        // Underside in shadow, a lit ridge along the top, growth rings, a darkened tip.
        for (i in 0 until n - 2) {
            p.detail(
                x + (hornU[i] + 0.02f) * r * d, y + (hornV[i] + hornW[i] * 0.55f) * r,
                x + (hornU[i + 1] + 0.02f) * r * d, y + (hornV[i + 1] + hornW[i + 1] * 0.55f) * r,
                hornW[i + 1] * r * 0.8f, ActorPaint.shade(color),
            )
            p.detail(
                x + (hornU[i] + 0.04f) * r * d, y + (hornV[i] - hornW[i] * 0.5f) * r,
                x + (hornU[i + 1] + 0.04f) * r * d, y + (hornV[i + 1] - hornW[i + 1] * 0.5f) * r,
                0.014f, ActorPaint.light(ActorPaint.light(color)),
            )
        }
        for (i in 1 until 4) {
            val cu = hornU[i]
            val cv = hornV[i]
            val tu = hornU[i + 1] - hornU[i - 1]
            val tv = hornV[i + 1] - hornV[i - 1]
            val tl = sqrt(tu * tu + tv * tv)
            val w = hornW[i] * 0.9f
            p.detail(
                x + (cu - tv / tl * w) * r * d, y + (cv + tu / tl * w) * r,
                x + (cu + tv / tl * w) * r * d, y + (cv - tu / tl * w) * r,
                0.012f, Col.mul(color, 0.7f),
            )
        }
        if (tip == 0) p.detail(x + hornU[4] * r * d, y + hornV[4] * r, x + hornU[5] * r * d, y + hornV[5] * r, 0.03f, Col.mul(color, 0.5f))
    }

    /** Skull, jaw, brow and eyes; the near horn over them. */
    private fun demonHead(e: Enemy, hx: Float, hy: Float, r: Float, d: Float, main: Int, lit: Int, dark: Int, glow: Int, hot: Int, bone: Int) {
        val open = if (e.state == EnemyState.WINDUP || e.state == EnemyState.AIM) 0.3f else 0.06f
        // Lower jaw, hinged under the ear.
        p.begin()
            .add(hx + r * 0.35f * d, hy + r * 0.38f)
            .add(hx + r * 1.36f * d, hy + r * (0.42f + open))
            .add(hx + r * 1.22f * d, hy + r * (0.64f + open * 1.3f))
            .add(hx + r * 0.6f * d, hy + r * (0.86f + open * 0.4f))
            .add(hx + r * 0.05f * d, hy + r * 0.72f)
            .shape(Col.lerp(main, dark, 0.25f))
        // The skull: a heavy brow over the eye, a long muzzle.
        p.begin()
            .add(hx - r * 0.8f * d, hy - r * 0.5f)
            .add(hx - r * 0.12f * d, hy - r * 0.98f)
            .add(hx + r * 0.62f * d, hy - r * 0.78f)
            .add(hx + r * 1.1f * d, hy - r * 0.42f)
            .add(hx + r * 0.96f * d, hy - r * 0.18f)
            .add(hx + r * 1.5f * d, hy - r * 0.02f)
            .add(hx + r * 1.62f * d, hy + r * 0.22f)
            .add(hx + r * 1.42f * d, hy + r * 0.42f)
            .add(hx + r * 0.55f * d, hy + r * 0.44f)
            .add(hx + r * 0.05f * d, hy + r * 0.72f)
            .add(hx - r * 0.6f * d, hy + r * 0.55f)
            .shapeLit(main, hx + r * 0.6f * d, hy - r * 0.95f, hx - r * 0.5f * d, hy + r * 0.7f)
        if (!p.ink) {
            if (p.shading) {
                // Back of the skull and under the brow in shadow; the brow ridge and muzzle top lit.
                p.begin()
                    .add(hx - r * 0.8f * d, hy - r * 0.5f)
                    .add(hx - r * 0.2f * d, hy - r * 0.2f)
                    .add(hx + r * 0.2f * d, hy + r * 0.5f)
                    .add(hx + r * 0.05f * d, hy + r * 0.72f)
                    .add(hx - r * 0.6f * d, hy + r * 0.55f)
                    .shapeShade(main)
                p.begin()
                    .add(hx + r * 0.3f * d, hy - r * 0.44f)
                    .add(hx + r * 1.02f * d, hy - r * 0.36f)
                    .add(hx + r * 0.96f * d, hy - r * 0.18f)
                    .add(hx + r * 0.38f * d, hy - r * 0.16f)
                    .shapeShade(main)
                p.detail(hx - r * 0.05f * d, hy - r * 0.84f, hx + r * 1.0f * d, hy - r * 0.5f, 0.03f, lit)
                p.detail(hx + r * 1.0f * d, hy - r * 0.08f, hx + r * 1.48f * d, hy + r * 0.04f, 0.024f, lit)
            }
            // Jaw notch: the hinge cut deep behind the mouth.
            p.begin()
                .add(hx + r * 0.3f * d, hy + r * 0.4f)
                .add(hx + r * 0.72f * d, hy + r * 0.42f)
                .add(hx + r * 0.42f * d, hy + r * 0.66f)
                .shapeDetail(dark)
            // Maw: a lit throat in the gap, one fang.
            val mouth = Col.lerp(glow, dark, 0.35f)
            p.begin()
                .add(hx + r * 0.5f * d, hy + r * 0.42f)
                .add(hx + r * 1.36f * d, hy + r * 0.42f)
                .add(hx + r * 1.3f * d, hy + r * (0.46f + open))
                .add(hx + r * 0.5f * d, hy + r * (0.5f + open * 0.5f))
                .shapeDetail(if (e.alive) mouth else dark)
            p.tri(
                hx + r * 1.12f * d, hy + r * 0.4f, hx + r * 1.28f * d, hy + r * 0.4f,
                hx + r * 1.2f * d, hy + r * (0.62f + open * 0.4f), p.c(bone),
            )
            // Burning eye slit, angled down toward the muzzle.
            if (e.alive) {
                emissive(hx + r * 0.55f * d, hy - r * 0.28f, hx + r * 0.98f * d, hy - r * 0.2f, 0.032f, glow, hot)
            } else {
                p.detail(hx + r * 0.55f * d, hy - r * 0.28f, hx + r * 0.98f * d, hy - r * 0.2f, 0.024f, dark)
            }
            // A swept-back pointed ear.
            p.begin()
                .add(hx - r * 0.1f * d, hy - r * 0.22f)
                .add(hx - r * 0.95f * d, hy - r * 0.62f)
                .add(hx - r * 0.25f * d, hy + r * 0.12f)
                .shapeDetail(if (p.shading) ActorPaint.shade(main) else main)
        }
        // The brow ridge: a heavy shelf jutting over the eye, its own slab.
        p.begin()
            .add(hx + r * 0.1f * d, hy - r * 0.62f)
            .add(hx + r * 0.7f * d, hy - r * 0.72f)
            .add(hx + r * 1.24f * d, hy - r * 0.5f)
            .add(hx + r * 1.18f * d, hy - r * 0.32f)
            .add(hx + r * 0.62f * d, hy - r * 0.4f)
            .add(hx + r * 0.2f * d, hy - r * 0.38f)
            .shapeLit(Col.lerp(main, lit, 0.35f), hx + r * 0.7f * d, hy - r * 0.75f, hx + r * 0.7f * d, hy - r * 0.34f, sep = true)
        demonHorn(hx, hy, r * 0.85f, d, bone, true)
    }

    /** A clawed hand: a knuckled palm and three long hooked talons curling toward the facing. */
    private fun claws(l: Limb, dir: Int, bone: Int) {
        val dx = l.ex - l.jx
        val dy = l.ey - l.jy
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
        val ux = dx / len
        val uy = dy / len
        var nx = -uy
        var ny = ux
        if (nx * dir < 0f || (abs(nx) < 0.2f && ny > 0f)) {
            nx = -nx; ny = -ny
        }
        val s = k.hs
        p.disc(l.ex + ux * 0.02f * s, l.ey + uy * 0.02f * s, 0.062f * s, look.arms)
        for (j in -1..1) {
            val o = j * 0.034f * s
            val bx = l.ex + ux * 0.06f * s - uy * o
            val by = l.ey + uy * 0.06f * s + ux * o
            val mx = bx + ux * 0.075f * s - uy * o * 0.5f
            val my = by + uy * 0.075f * s + ux * o * 0.5f
            val tx = mx + ux * 0.04f * s + nx * 0.05f * s
            val ty = my + uy * 0.04f * s + ny * 0.05f * s
            p.bone(bx, by, mx, my, 0.034f * s, 0.026f * s, bone, lit = false)
            p.bone(mx, my, tx, ty, 0.026f * s, 0.008f * s, bone, lit = false)
        }
    }

    /** Toe x, toe y and ground y per leg, solved once per frame before the two passes. */
    private val hoof = FloatArray(6)

    /** Re-solves a rig leg as digitigrade (hock up behind the ankle) and stores its toe. */
    private fun hoofSolve(l: Limb, i: Int) {
        val d = k.dir
        val s = k.hs
        hoof[i * 3] = l.ex + 0.07f * d * s
        hoof[i * 3 + 1] = l.ey + 0.02f
        hoof[i * 3 + 2] = l.ey + 0.045f * s
        k.ik(l, l.ex - 0.1f * d * s, l.ey - 0.2f * s, true)
    }

    /**
     * Digitigrade leg, painted like the arms: a heavy haunch, the knee forward, a reversed shin
     * back to the hock, a long slim foot down to a glossy cloven hoof.
     */
    private fun hoofLeg(l: Limb, i: Int, color: Int, near: Boolean) {
        val d = k.dir
        val s = k.hs
        val toeX = hoof[i * 3]
        val toeY = hoof[i * 3 + 1]
        val gy = hoof[i * 3 + 2]
        val lw = k.limbW * 1.05f
        p.lightFrom(d)
        p.bone(l.ax, l.ay, l.jx, l.jy, lw * 1.5f, lw * 0.95f, color, near, bulge = lw * 1.6f)
        p.bone(l.jx, l.jy, l.ex, l.ey, lw * 0.95f, lw * 0.62f, color, near, bulge = lw * 1.0f)
        p.bone(l.ex, l.ey, toeX, toeY, lw * 0.62f, lw * 0.5f, color, near)
        // Cloven hoof: dark glossy horn, the split, a glint.
        val hoofC = if (near) 0xFF2E2226.toInt() else 0xFF1C1418.toInt()
        p.begin()
            .add(toeX - 0.06f * d * s, toeY - 0.03f * s)
            .add(toeX + 0.04f * d * s, toeY - 0.04f * s)
            .add(toeX + 0.1f * d * s, gy)
            .add(toeX - 0.07f * d * s, gy)
            .shapeLit(hoofC, toeX, toeY - 0.04f * s, toeX, gy, sep = near)
        if (near && p.shading) {
            p.detail(toeX + 0.035f * d * s, toeY - 0.01f * s, toeX + 0.05f * d * s, gy - 0.005f, 0.012f, 0xFF08060A.toInt())
            p.detail(toeX - 0.03f * d * s, toeY - 0.022f * s, toeX + 0.03f * d * s, toeY - 0.03f * s, 0.012f, Col.alpha(0xFFFFFFFF.toInt(), 0.5f))
        }
    }

    /** Licking tongues of fire rising from (x, y), additive. */
    private fun flames(x: Float, y: Float, dir: Int, color: Int, seed: Int) {
        g.blend(Gfx.Blend.ADD)
        for (i in 0 until 3) {
            val ph = f.t * 9f + i * 2.1f + seed
            val h = 0.15f + 0.07f * sin(ph) + (1 - abs(i - 1)) * 0.05f
            val ox = (i - 1) * 0.05f - 0.03f * dir
            val sway = sin(ph * 1.3f) * 0.045f - 0.03f * dir
            val cx = x + ox
            p.begin()
                .add(cx - 0.042f, y)
                .add(cx - 0.036f + sway * 0.3f, y - h * 0.45f)
                .add(cx + sway, y - h)
                .add(cx + 0.03f + sway * 0.4f, y - h * 0.5f)
                .add(cx + 0.042f, y)
                .shapeDetail(Col.alpha(color, 0.7f))
            p.begin()
                .add(cx - 0.02f, y)
                .add(cx + sway * 0.6f, y - h * 0.62f)
                .add(cx + 0.02f, y)
                .shapeDetail(Col.alpha(Col.lerp(color, 0xFFFFFFFF.toInt(), 0.6f), 0.8f))
        }
        g.blend(Gfx.Blend.NORMAL)
    }

    // ============================================================ machines

    /**
     * A premium security quadcopter in one product language with the turret: a pale ceramic
     * shell over a graphite chassis, a crisp shut-line between them, a black-glass visor
     * wrapping the nose, and one glowing lens, the only light on it. Graphite arms reach up to
     * motor pods under faint rotor discs; a gimballed gun pod rides under the chin at exactly
     * the shot height.
     */
    private fun drone(e: Enemy, x: Float, gy: Float, dir: Int, pal: Palette) {
        val y = gy - e.z - 0.2f
        val dead = e.state == EnemyState.DEAD
        if (dead && e.stateTime > 1.05f) p.alphaMul = max(0f, 1f - (e.stateTime - 1.05f) / 0.5f)
        g.save()
        g.translate(x, y)
        if (dead) g.rotate(e.stateTime * 500f * (if (e.deathVx >= 0f) 1f else -1f))
        else g.rotate(e.vx * 6f + sin(f.t * 3f + e.id) * 2f)
        // Drawn a size up: a small machine has to carry its read at the zoomed-out camera.
        g.scale(DRONE_S, DRONE_S)
        val d = dir.toFloat()
        val alarmed = e.state == EnemyState.AIM || e.state == EnemyState.ALERT
        val eyeC = if (alarmed) 0xFFFF2A40.toInt() else pal.neon2
        if (dead) {
            p.flat = 0xFF000000.toInt(); p.flatAmt = min(0.5f, e.stateTime)
        }
        val podY = 0.1f / DRONE_S
        val muz = 0.54f / DRONE_S
        p.twoPass {
            // Arms up and out to the motor pods.
            for (si in 0..1) {
                val sx = (si * 2 - 1).toFloat()
                p.begin().add(0.1f * sx, -0.115f).add(0.37f * sx, -0.172f).add(0.42f * sx, -0.158f).add(0.42f * sx, -0.122f).add(0.14f * sx, -0.06f)
                    .shapeLit(Mech.GRAPHITE, 0f, -0.172f, 0f, -0.06f)
                p.bone(0.4f * sx, -0.205f, 0.4f * sx, -0.1f, 0.095f, 0.07f, Mech.GRAPHITE)
            }
            // The gun pod: a gimbal ball under the chin and a slim barrel out to the muzzle.
            p.ball(0.12f * d, podY - 0.012f, 0.056f, Mech.GRAPHITE)
            p.begin().add(0.12f * d, podY - 0.022f).add(muz * d, podY - 0.02f).add(muz * d, podY + 0.02f).add(0.12f * d, podY + 0.022f)
                .shapeLit(Mech.GRAPHITE, 0f, podY - 0.022f, 0f, podY + 0.022f)
            // The hull: a long, sculpted teardrop, blunt nose toward the facing side.
            p.begin()
                .add(-0.36f * d, -0.035f).add(-0.28f * d, -0.112f).add(-0.04f * d, -0.155f).add(0.18f * d, -0.138f)
                .add(0.3f * d, -0.07f).add(0.33f * d, -0.005f).add(0.27f * d, 0.06f).add(0.1f * d, 0.095f)
                .add(-0.14f * d, 0.09f).add(-0.3f * d, 0.045f)
                .shapeLit(Mech.GRAPHITE, 0f, -0.155f, 0f, 0.12f)
        }
        if (!p.ink) {
            // The ceramic shell over the chassis, lamp-lit on its crown.
            p.begin()
                .add(-0.35f * d, -0.035f).add(-0.28f * d, -0.112f).add(-0.04f * d, -0.155f).add(0.18f * d, -0.138f)
                .add(0.3f * d, -0.07f).add(0.325f * d, 0.0f).add(0.1f * d, 0.03f).add(-0.14f * d, 0.03f).add(-0.31f * d, 0.02f)
            p.shapeGradDetail(ActorPaint.light(Mech.SHELL), ActorPaint.shade(Mech.SHELL), 0.04f * d, -0.155f, -0.02f * d, 0.03f)
            if (p.shading) {
                // The shut-line, a lamp glint running along the crown, a bounce of cool light off the belly.
                p.detail(-0.31f * d, 0.024f, 0.1f * d, 0.03f, 0.012f, 0xFF07080E.toInt())
                g.blend(Gfx.Blend.ADD)
                g.line(-0.24f * d, -0.108f, 0.08f * d, -0.138f, 0.02f, p.c(0x40FFFFFF))
                g.line(-0.12f * d, -0.128f, 0.02f * d, -0.14f, 0.01f, p.c(0x60FFFFFF))
                g.line(-0.18f * d, 0.075f, 0.06f * d, 0.078f, 0.012f, p.c(0x20B0C0FF))
                g.blend(Gfx.Blend.NORMAL)
                // A ceramic cap on each motor pod and a ring on the gun's muzzle.
                p.detail(-0.44f, -0.21f, -0.36f, -0.21f, 0.03f, ActorPaint.light(Mech.SHELL))
                p.detail(0.36f, -0.21f, 0.44f, -0.21f, 0.03f, ActorPaint.light(Mech.SHELL))
                p.detail((muz - 0.03f) * d, podY - 0.022f, (muz - 0.03f) * d, podY + 0.022f, 0.02f, Mech.SHELL)
            }
            // The black-glass visor wrapping the nose, one clean reflection across it.
            p.begin().add(0.04f * d, -0.098f).add(0.19f * d, -0.114f).add(0.29f * d, -0.062f).add(0.315f * d, -0.005f).add(0.25f * d, 0.03f).add(0.04f * d, 0.024f)
            p.shapeGradDetail(0xFF1C2336.toInt(), 0xFF030407.toInt(), 0f, -0.114f, 0f, 0.03f)
            if (p.shading) {
                g.blend(Gfx.Blend.ADD)
                g.line(0.07f * d, -0.086f, 0.18f * d, -0.1f, 0.012f, p.c(0x50C8DCFF))
                g.line(0.18f * d, -0.1f, 0.25f * d, -0.075f, 0.01f, p.c(0x38C8DCFF))
                g.blend(Gfx.Blend.NORMAL)
            }
            // Rotor discs: barely-there blur, a soft blade streak turning in it.
            for (si in 0..1) {
                val rx = (si * 2 - 1) * 0.4f
                g.save()
                g.translate(rx, -0.222f)
                g.scale(1f, 0.13f)
                if (dead) {
                    val a = e.stateTime * 3f + si
                    g.line(-0.2f * cos(a), 0f, 0.2f * cos(a), 0f, 0.14f, p.c(0xFF2A2E3C.toInt()))
                } else {
                    g.fillCircle(0f, 0f, 0.25f, p.c(0x22B4C0DC))
                    val b = sin(f.t * 50f + si * 1.7f)
                    g.line(-0.24f * b, 0f, 0.24f * b, 0f, 0.16f, p.c(0x40D8E0F0))
                }
                g.restore()
            }
            if (!dead) {
                // The eye: a lit lens in a dark bezel, glowing into the room.
                val ex = 0.2f * d
                val ey = -0.04f
                g.fillCircle(ex, ey, 0.062f, p.c(0xFF040508.toInt()))
                if (p.shading) g.strokeCircle(ex, ey, 0.054f, 0.01f, p.c(0xFF343C52.toInt()))
                g.fillCircle(ex, ey, 0.04f, p.c(eyeC))
                g.fillCircle(ex, ey, 0.019f, p.c(Col.lerp(eyeC, 0xFFFFFFFF.toInt(), 0.75f)))
                g.blend(Gfx.Blend.ADD)
                g.glow(ex, ey, 0.3f, p.c(Col.alpha(eyeC, 0.7f)))
                g.blend(Gfx.Blend.NORMAL)
            }
        }
        g.restore()
        if (!dead && e.state == EnemyState.AIM) f.glowDot(x + 0.54f * dir, y + 0.1f, 0.035f, pal.laser, p.alphaMul)
        p.reset()
        if (!dead && e.state == EnemyState.PATROL) {
            val sweep = sin(f.t * 2f + e.id) * 0.3f
            f.poly.tri(g, x + 0.16f * dir, y, x + 2.2f * dir, y + 0.6f + sweep, x + 2.2f * dir, y + 1.3f + sweep, Col.alpha(pal.neon2, 0.08f))
        }
    }

    /**
     * A ceiling sentry in the drone's product language: a graphite mount plate and stem to a
     * yoke collar, and in it a pale ceramic gun head that tracks the agent: a chamfered shell
     * over a graphite chin, a black-glass face with the lens, twin barrels out front.
     */
    private fun turret(e: Enemy, x: Float, gy: Float, pal: Palette) {
        val rt = gy - Geo.FLOOR_H + Building.SLAB
        val y = gy - e.z
        val dead = e.state == EnemyState.DEAD
        val pl = f.w.player
        var ang = if (e.facing >= 0) 0f else 180f
        if (!dead && pl.floor == e.floor && pl.hall == e.hall) {
            val dx = pl.x - x
            val dy = (Geo.groundY(pl.floorF) - pl.z - 0.9f) - y
            ang = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }
        if (dead) ang = 110f + sin(f.t * 3f) * 6f
        // Its own key light, never the one the last humanoid left in the pen.
        p.lightFrom(if (e.facing >= 0) 1 else -1)
        p.twoPass {
            p.bone(x, rt + 0.05f, x, y - 0.18f, 0.12f, 0.1f, Mech.GRAPHITE)
            p.begin().add(x - 0.3f, rt).add(x + 0.3f, rt).add(x + 0.24f, rt + 0.08f).add(x - 0.24f, rt + 0.08f)
                .shapeLit(Mech.GRAPHITE, x, rt, x, rt + 0.08f)
            // The yoke's collar the head swings under.
            p.begin().add(x - 0.16f, y - 0.27f).add(x + 0.16f, y - 0.27f).add(x + 0.12f, y - 0.17f).add(x - 0.12f, y - 0.17f)
                .shapeLit(Mech.GRAPHITE, x, y - 0.27f, x, y - 0.17f)
        }
        if (p.shading) {
            // A ceramic band on the collar ties it to the head; the lamp down the stem.
            p.detail(x - 0.145f, y - 0.25f, x + 0.145f, y - 0.25f, 0.024f, ActorPaint.light(Mech.SHELL))
            p.detail(x - 0.026f, rt + 0.1f, x - 0.026f, y - 0.2f, 0.014f, Col.alpha(0xFFFFFFFF.toInt(), 0.14f))
        }
        g.save()
        g.translate(x, y)
        g.rotate(ang)
        // Keep the head's top up whichever way it tracks, so its lamp-lit crown stays on top.
        val left = cos(Math.toRadians(ang.toDouble())) < 0.0
        if (left) g.scale(1f, -1f)
        p.twoPass {
            // Twin barrels, as one outlined block, with a heavy muzzle collar.
            p.begin().add(0.16f, -0.046f).add(0.5f, -0.046f).add(0.5f, -0.058f).add(0.565f, -0.058f)
                .add(0.565f, 0.058f).add(0.5f, 0.058f).add(0.5f, 0.046f).add(0.16f, 0.046f)
                .shapeLit(Mech.GRAPHITE, 0f, -0.058f, 0f, 0.058f)
            // The head: a faceted pod, chamfered nose and a heavy brow.
            p.begin()
                .add(-0.3f, -0.08f).add(-0.22f, -0.175f).add(0.1f, -0.19f).add(0.24f, -0.12f)
                .add(0.27f, 0.02f).add(0.21f, 0.13f).add(-0.17f, 0.15f).add(-0.29f, 0.07f)
                .shapeLit(Mech.GRAPHITE, 0f, -0.19f, 0f, 0.15f)
        }
        if (!p.ink) {
            // The ceramic shell over the graphite chin, parted by a crisp shut-line.
            p.begin().add(-0.3f, -0.08f).add(-0.22f, -0.175f).add(0.1f, -0.19f).add(0.24f, -0.12f).add(0.262f, -0.01f).add(-0.295f, 0.03f)
            p.shapeGradDetail(ActorPaint.light(Mech.SHELL), ActorPaint.shade(Mech.SHELL), 0.04f, -0.19f, -0.02f, 0.03f)
            // Black-glass face on the chamfered nose, the lens set in it.
            p.begin().add(0.02f, -0.155f).add(0.1f, -0.172f).add(0.225f, -0.11f).add(0.25f, -0.01f).add(0.02f, 0.008f)
            p.shapeGradDetail(0xFF1C2336.toInt(), 0xFF030407.toInt(), 0f, -0.17f, 0f, 0.01f)
            if (p.shading) {
                p.detail(-0.29f, 0.03f, 0.26f, -0.01f, 0.012f, 0xFF07080E.toInt())
                p.detail(0.18f, 0.0f, 0.5f, 0.0f, 0.012f, 0xFF07080C.toInt())
                g.blend(Gfx.Blend.ADD)
                g.line(-0.21f, -0.16f, 0.0f, -0.172f, 0.02f, p.c(0x40FFFFFF))
                g.line(0.05f, -0.15f, 0.12f, -0.158f, 0.01f, p.c(0x50C8DCFF))
                g.line(0.51f, -0.048f, 0.555f, -0.048f, 0.012f, p.c(0x40FFFFFF))
                g.blend(Gfx.Blend.NORMAL)
            }
        }
        val lx = 0.13f
        val ly = -0.075f
        if (!dead) {
            val alarmed = e.state == EnemyState.AIM || e.state == EnemyState.ALERT
            val eyeC = if (alarmed) 0xFFFF2A40.toInt() else pal.neon2
            g.fillCircle(lx, ly, 0.072f, p.c(0xFF040508.toInt()))
            if (p.shading) g.strokeCircle(lx, ly, 0.064f, 0.012f, p.c(0xFF343C52.toInt()))
            g.fillCircle(lx, ly, 0.048f, p.c(eyeC))
            g.fillCircle(lx, ly, 0.022f, p.c(Col.lerp(eyeC, 0xFFFFFFFF.toInt(), 0.75f)))
            g.blend(Gfx.Blend.ADD)
            g.glow(lx, ly, 0.36f, p.c(Col.alpha(eyeC, 0.75f)))
            g.blend(Gfx.Blend.NORMAL)
        }
        g.restore()
        if (dead && hash((f.t * 12f).toInt(), e.id) > 0.8f) {
            f.glowDot(x + 0.1f, y + 0.1f, 0.04f, 0xFFFFE080.toInt())
        }
    }

    /** The machines' materials: one product line, a pale ceramic shell over graphite. */
    private object Mech {
        const val SHELL = 0xFF98A0B2.toInt()
        const val GRAPHITE = 0xFF22252F.toInt()
    }

    // =========================================================== telegraphs

    /** The single most important read in the game: where and when the shot comes. */
    private fun aimTelegraph(e: Enemy, gy: Float, dir: Int, fs: HallState) {
        val aimT = Heat.aimTime(fs.plan.heat)
        val t = (e.stateTime / aimT).coerceIn(0f, 1f)
        val laser = 0xFFFF1E3C.toInt()
        val blink = if (t > 0.65f) (if (sin(f.t * 60f) > 0f) 1f else 0.45f) else 1f
        val a = (0.35f + 0.65f * t) * blink
        if (e.kind == EnemyKind.TURRET) {
            val pl = f.w.player
            val y0 = gy - e.z
            val tx = pl.x
            val ty = Geo.groundY(pl.floorF) - pl.z - 0.9f
            val dx = tx - e.x
            val dy = ty - y0
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val ex = e.x + dx / len * 12f
            val ey = y0 + dy / len * 12f
            g.save()
            g.clipRect(0f, gy - Geo.FLOOR_H + Building.SLAB, Geo.FLOOR_W, gy)
            f.glowLine(e.x + dx / len * 0.55f, y0 + dy / len * 0.55f, ex, ey, 0.025f + 0.03f * t, Col.fade(laser, a), 0)
            g.restore()
            return
        }
        val z = when {
            e.kind == EnemyKind.DRONE -> e.z + 0.1f
            e.kind == EnemyKind.DEMON -> 1.3f
            e.aimLow -> Body.LOW
            else -> Body.HIGH
        }
        val y = gy - z
        val mx = e.x + dir * (e.halfWidth + 0.2f)
        val end = if (dir > 0) Geo.FLOOR_W else 0f
        if (e.kind == EnemyKind.DEMON) {
            var px = mx
            var pz = 1.3f
            val v = Heat.bulletSpeed(fs.plan.heat) * 0.75f
            var vz = 3.5f
            val dtS = 0.05f
            for (i in 0 until 22) {
                px += dir * v * dtS
                vz -= 9f * dtS
                pz += vz * dtS
                if (pz < 0f || px < 0f || px > Geo.FLOOR_W) break
                if (i % 2 == 0) g.fillCircle(px, gy - pz, 0.04f + 0.02f * t, Col.fade(0xFFFF8A20.toInt(), a))
            }
            return
        }
        f.glowLine(mx, y, end, y, 0.022f + 0.035f * t, Col.fade(laser, a), Col.alpha(0xFFFFFFFF.toInt(), 0.6f * t), 0.9f)
        // Lane marker: a chevron tells you to jump (low) or duck/hide (high).
        val cx = e.x + dir * 1.35f
        val cy = if (e.aimLow) y - 0.3f else y - 0.28f
        val chev = Col.fade(laser, min(1f, a + 0.2f))
        if (e.aimLow) {
            Glyphs.arrow(g, cx, cy, 0.19f, 0f, -1f, 0.065f, chev)
        } else {
            Glyphs.arrow(g, cx, cy + 0.56f, 0.19f, 0f, 1f, 0.065f, chev)
        }
        // Wind-up ring collapsing onto the muzzle.
        val r = 0.45f * (1f - t) + 0.06f
        g.strokeCircle(mx, y, r, 0.03f, Col.fade(laser, 0.3f + 0.7f * t))
    }

    /** Melee windup: the slash arc charging in front, brightening to the strike. */
    private fun windupTelegraph(e: Enemy, gy: Float, dir: Int) {
        val t = (e.stateTime / 0.3f).coerceIn(0f, 1f)
        val cx = e.x + dir * 0.15f
        val cy = gy - 0.85f
        val n = 12
        val r = 1.05f
        val lit = (n * t).toInt()
        for (i in 0 until n) {
            val a0 = -1.25f + i * (2.5f / n)
            val a1 = a0 + 2.5f / n
            val on = i <= lit
            val w = if (on) 0.05f + 0.07f * t * (1f - abs(i - n / 2f) / n) else 0.02f
            val col = if (on) Col.alpha(0xFFFF2A40.toInt(), 0.35f + 0.55f * t) else Col.alpha(0xFFFF2A40.toInt(), 0.15f)
            g.line(cx + cos(a0) * r * dir, cy + sin(a0) * r, cx + cos(a1) * r * dir, cy + sin(a1) * r, w, col)
        }
        if (t > 0.8f) {
            val fl = (t - 0.8f) / 0.2f
            g.line(cx + cos(-1.25f) * r * dir, cy + sin(-1.25f) * r, cx + cos(1.25f) * r * dir * 0.98f, cy + sin(1.25f) * r, 0.01f + 0.02f * fl, Col.alpha(0xFFFFFFFF.toInt(), fl * 0.8f))
        }
    }

    private fun statusMarks(e: Enemy, gy: Float) {
        val top = gy - e.z - e.height - 0.42f
        when (e.state) {
            // (Woken nappers and the box-kicker say it with a popup instead.)
            EnemyState.ALERT -> if (e.stateTime < 0.9f && f.moments.wokeAge(e) < 0f && !(e.id == f.moments.kickId && f.t - f.moments.kickAt < 0.9f)) {
                val pop = Rig.backOut(e.stateTime / 0.16f)
                val rise = (1f - pop) * 0.15f
                val fade = if (e.stateTime > 0.75f) 1f - (e.stateTime - 0.75f) / 0.15f else 1f
                bubble(e.x, top + rise, pop, Col.alpha(0xFFFFD21E.toInt(), fade), fade)
                if (pop > 0.05f) exclaimGlyph(e.x, bubbleMid(top + rise, pop), GLYPH_H * pop, Col.alpha(0xFF1A0A00.toInt(), fade))
            }
            EnemyState.SEARCH -> if (!(e.stateTime < 0.8f && boxWatch(e))) {
                val sway = sin(f.t * 4f + e.id) * 0.06f
                val pop = Rig.backOut(e.stateTime / 0.2f)
                bubble(e.x + sway, top, pop, 0xE8E8ECFF.toInt(), 1f)
                if (pop > 0.05f) questionGlyph(e.x + sway, bubbleMid(top, pop), GLYPH_H * pop, 0xFF1A1A30.toInt())
            }
            EnemyState.STUNNED -> {
                for (i in 0 until 3) {
                    val a = f.t * 5f + i * 2.1f
                    val sx = e.x + cos(a) * 0.25f
                    val sy = top + 0.2f + sin(a) * 0.07f
                    star(sx, sy, 0.06f, 0xFFFFE070.toInt())
                }
            }
            else -> Unit
        }
    }

    /** Speech bubble with a tail, scaled by [s] from its tail tip. */
    private fun bubble(x: Float, bottom: Float, s: Float, color: Int, fade: Float) {
        if (s <= 0.01f) return
        val w = 0.22f * s
        val h = BUBBLE_H * s
        val by = bottom - BUBBLE_TAIL * s
        val o = p.out
        val inkCol = Col.alpha(ActorPaint.INK, fade)
        g.fillRoundRect(x - w - o, by - h - o, x + w + o, by + o, 0.09f * s + o, inkCol)
        f.poly.tri(g, x - 0.07f * s - o, by - 0.01f, x + 0.07f * s + o, by - 0.01f, x, bottom + o * 1.3f, inkCol)
        g.fillRoundRect(x - w, by - h, x + w, by, 0.09f * s, color)
        f.poly.tri(g, x - 0.07f * s, by - 0.02f, x + 0.07f * s, by - 0.02f, x, bottom, color)
        g.line(x - w * 0.55f, by - h + 0.06f * s, x - w * 0.55f, by - h * 0.45f, 0.03f * s, Col.alpha(0xFFFFFFFF.toInt(), 0.35f * fade))
    }

    /** Vertical middle of the body of a [bubble] whose tail tip is at [bottom] (scale [s]). */
    private fun bubbleMid(bottom: Float, s: Float): Float = bottom - (BUBBLE_TAIL + BUBBLE_H / 2f) * s

    /** Shape-drawn "!", [h] tall, centred on ([x], [cy]). */
    private fun exclaimGlyph(x: Float, cy: Float, h: Float, color: Int) {
        val top = cy - h / 2f
        val barBot = top + h * 0.64f
        f.poly.quad(g, x - h * 0.085f, top, x + h * 0.085f, top, x + h * 0.05f, barBot, x - h * 0.05f, barBot, color)
        g.fillCircle(x, cy + h / 2f - h * 0.1f, h * 0.1f, color)
    }

    /** Shape-drawn "?", [h] tall, centred on ([x], [cy]): a hook, a short stem and a dot. */
    private fun questionGlyph(x: Float, cy: Float, h: Float, color: Int) {
        val top = cy - h / 2f
        val sw = h * 0.13f
        val r = h * 0.2f
        val ay = top + sw / 2f + r
        // The hook: from the left, over the top, round and down to the lower right.
        g.strokeArc(x, ay, r, 180f, 250f, sw, color)
        val ex = x + r * cos(70f * PI.toFloat() / 180f)
        val ey = ay + r * sin(70f * PI.toFloat() / 180f)
        g.line(ex, ey, x, top + h * 0.56f, sw, color)
        g.line(x, top + h * 0.56f, x, top + h * 0.66f, sw, color)
        g.fillCircle(x, cy + h / 2f - h * 0.1f, h * 0.1f, color)
    }

    private fun star(x: Float, y: Float, r: Float, color: Int) {
        f.poly.begin()
        for (i in 0 until 8) {
            val a = i * PI.toFloat() / 4f + f.t * 3f
            val rr = if (i % 2 == 0) r else r * 0.45f
            f.poly.add(x + cos(a) * rr, y + sin(a) * rr)
        }
        f.poly.fill(g, color)
    }

    private fun hpPips(e: Enemy, x: Float, y: Float) {
        // Chunky pips on an inked plate: readable at the zoomed-out camera, 1 + n calls.
        val n = e.maxHp
        val pw = 0.15f
        val gap = 0.035f
        val total = n * pw + (n - 1) * gap
        var px = x - total / 2f
        g.fillRoundRect(px - 0.05f, y - 0.09f, px + total + 0.05f, y + 0.09f, 0.05f, 0xE0000000.toInt())
        for (i in 0 until n) {
            g.fillRect(px, y - 0.05f, px + pw, y + 0.05f, if (i < e.hp) 0xFFFF4A5E.toInt() else 0x40FFFFFF)
            px += pw + gap
        }
    }

    /** In the dark, alive enemies are silhouettes with glowing eyes. */
    fun darkEyes(fi: Int, fs: HallState) {
        val d = fs.darkness
        if (d < 0.3f) return
        val gy = Geo.groundY(fi)
        if (!f.visibleY(gy - Geo.FLOOR_H, gy)) return
        val list = f.w.enemies
        for (i in list.indices) {
            val e = list[i]
            if (e.floor != fi || e.hall != fs.plan.hall || !e.alive) continue
            val dir = if (e.facing >= 0) 1 else -1
            // Eyes shut: a napping guard is a dark lump with his Zs.
            if (e.asleep) {
                if (i < eyeOk.size && eyeOk[i]) f.moments.zzz(e, eyeX[i], eyeY[i], dir, 0.125f, d * 0.8f)
                continue
            }
            val alarmed = e.state == EnemyState.ALERT || e.state == EnemyState.AIM || e.state == EnemyState.WINDUP
            val col = if (alarmed) 0xFFFF2A3A.toInt() else 0xFFFFE8A0.toInt()
            when (e.kind) {
                EnemyKind.DRONE -> f.glowDot(e.x + 0.14f * dir, gy - e.z - 0.2f, 0.05f, col, d)
                EnemyKind.TURRET -> f.glowDot(e.x, gy - e.z, 0.05f, col, d)
                else -> if (i < eyeOk.size && eyeOk[i]) {
                    val ex = eyeX[i]
                    val ey = eyeY[i]
                    f.glowDot(ex, ey, 0.026f, col, d)
                    f.glowDot(ex - 0.065f * dir, ey, 0.022f, col, d)
                }
            }
            if (e.state == EnemyState.AIM) aimTelegraph(e, gy, dir, fs)
        }
    }
}
