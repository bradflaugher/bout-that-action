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

    /**
     * A face in profile: a shaded cranium and jaw and a nose breaking the silhouette. At 120 px
     * a face is flat skin and one clean shadow shape; any more marks read as scars.
     */
    private fun face(hx: Float, hy: Float, r: Float, skin: Int, dir: Int) {
        p.ball(hx, hy, r, skin)
        p.ball(hx + r * 0.3f * dir, hy + r * 0.5f, r * 0.6f, skin)
        if (!p.hi) return
        p.begin()
            .add(hx + r * 0.9f * dir, hy - r * 0.12f)
            .add(hx + r * 1.2f * dir, hy + r * 0.28f)
            .add(hx + r * 0.94f * dir, hy + r * 0.36f)
            .shape(skin)
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
                L.torso = main; L.torsoLit = 0xFFFFFFFF.toInt(); L.legs = Col.mul(main, 0.92f); L.legsFar = Col.mul(main, 0.68f)
                L.arms = main; L.armsFar = Col.mul(main, 0.7f); L.boots = 0xFF1C2828.toInt(); L.gloves = 0xFF2A4A40.toInt(); L.skin = skin
                L.legW = 1.12f; L.armW = 1.1f
            }
            Zone.METRO -> {
                val navy = Col.lerp(main, 0xFF40598A.toInt(), 0.5f)
                L.torso = navy; L.torsoLit = Col.lerp(navy, 0xFFFFFFFF.toInt(), 0.3f); L.legs = Col.mul(navy, 0.82f); L.legsFar = Col.mul(navy, 0.58f)
                L.arms = navy; L.armsFar = Col.mul(navy, 0.62f); L.boots = 0xFF0C0C10.toInt(); L.gloves = 0xFF121418.toInt(); L.skin = skin
            }
            Zone.MINES -> {
                L.torso = main; L.torsoLit = Col.lerp(main, 0xFFFFFFFF.toInt(), 0.3f); L.legs = main; L.legsFar = Col.mul(main, 0.62f)
                L.arms = 0xFF7A4A34.toInt(); L.armsFar = 0xFF4E2E20.toInt(); L.boots = 0xFF2A1A10.toInt(); L.gloves = 0xFF8A6A40.toInt(); L.skin = skin
            }
            Zone.MAGMA -> {
                L.torso = main; L.torsoLit = 0xFFF4F0E8.toInt(); L.legs = Col.mul(main, 0.92f); L.legsFar = Col.mul(main, 0.66f)
                L.arms = main; L.armsFar = Col.mul(main, 0.68f); L.boots = 0xFF2A2420.toInt(); L.gloves = 0xFF3A322C.toInt(); L.skin = skin
                L.legW = 1.15f; L.armW = 1.12f
            }
            Zone.HELL -> {
                val robe = Col.lerp(main, 0xFF9A2030.toInt(), 0.4f)
                L.torso = robe; L.torsoLit = Col.lerp(robe, 0xFFFF6040.toInt(), 0.35f); L.legs = Col.mul(main, 0.6f); L.legsFar = Col.mul(main, 0.45f)
                L.arms = main; L.armsFar = Col.mul(main, 0.62f); L.boots = 0xFF140406.toInt(); L.gloves = skin; L.skin = skin
                L.armW = 1.3f
            }
            else -> {
                val suit = Col.lerp(main, 0xFF4C4A66.toInt(), 0.62f)
                L.torso = suit; L.torsoLit = Col.lerp(suit, 0xFFFFFFFF.toInt(), 0.22f); L.legs = suit; L.legsFar = Col.mul(suit, 0.62f)
                L.arms = suit; L.armsFar = Col.mul(suit, 0.62f); L.boots = 0xFF050508.toInt(); L.gloves = skin; L.skin = skin
            }
        }
        val robe = z == Zone.HELL
        val gunTrim = if (robe) acc else if (z == Zone.TOWER) 0xFF30303C.toInt() else 0xFF3A3E4A.toInt()
        p.twoPass {
            body.arm(k.armB, L, far = true)
            if (!robe) body.leg(k.legB, L, far = true) else robeFeet(L)
            // Back-worn kit.
            when (z) {
                Zone.LABS, Zone.MAGMA -> {
                    val tx1 = body.ptX(0.3f, -k.waistD * 0.62f); val ty1 = body.ptY(0.3f, -k.waistD * 0.62f)
                    val tx2 = body.ptX(0.88f, -k.chestD * 0.62f); val ty2 = body.ptY(0.88f, -k.chestD * 0.62f)
                    p.seg(tx1, ty1, tx2, ty2, 0.15f, if (z == Zone.LABS) 0xFF8C9C9C.toInt() else 0xFF5A524C.toInt())
                    p.detail(tx1, ty1 - 0.02f, tx2, ty2 - 0.02f, 0.03f, 0x50FFFFFF)
                    p.detail(body.ptX(0.55f, -k.waistD * 0.68f), body.ptY(0.55f, -k.waistD * 0.68f), body.ptX(0.62f, -k.waistD * 0.68f), body.ptY(0.62f, -k.waistD * 0.68f), 0.15f, acc)
                }
                Zone.METRO -> {
                    // Radio on the belt.
                    val rx = body.ptX(0.12f, -k.waistD * 0.55f); val ry = body.ptY(0.12f, -k.waistD * 0.55f)
                    p.seg(rx, ry, rx, ry - 0.08f, 0.07f, 0xFF15161C.toInt())
                }
                else -> Unit
            }
            if (!robe) {
                body.leg(k.legF, L, far = false)
                if (z == Zone.TOWER) body.hem(L.torso, 0.2f, 0.035f)
                body.torso(L)
            } else {
                robeBody(L, acc)
            }
            guardDetails(z, dir, L, acc)
            body.neck(if (z == Zone.LABS || z == Zone.MAGMA || robe) L.torso else skin)
            guardHead(e, z, dir, L, pal)
        }
        p.twoPass {
            weapon(armed, gunTrim)
            if (robe) sleeveArm(L) else body.arm(k.armF, L, far = false)
        }
        if (e.state == EnemyState.AIM && armed && showGun) {
            f.glowDot(body.muzzleX, body.muzzleY, 0.035f, pal.laser, p.alphaMul)
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
        val gnd = min(k.ground, max(k.legF.ey, k.legB.ey) + 0.03f * k.hs)
        val fx = k.legF.ex
        val bx = k.legB.ex
        val front = if ((fx - bx) * k.dir > 0f) fx else bx
        val back = if ((fx - bx) * k.dir > 0f) bx else fx
        val c = k.chestD
        p.begin()
            .add(body.ptX(1.02f, -c * 0.3f), body.ptY(1.02f, -c * 0.3f))
            .add(body.ptX(1.0f, c * 0.32f), body.ptY(1.0f, c * 0.32f))
            .add(body.ptX(0.6f, c * 0.5f), body.ptY(0.6f, c * 0.5f))
            .add(front + 0.12f * k.dir, gnd - 0.03f)
            .add(back - 0.16f * k.dir, gnd - 0.03f)
            .add(body.ptX(0.4f, -c * 0.62f), body.ptY(0.4f, -c * 0.62f))
            .shape(L.torso)
        if (p.shading) {
            // The robe's back half in shadow, and long folds falling from the belt.
            p.begin()
                .add(body.ptX(1.02f, -c * 0.3f), body.ptY(1.02f, -c * 0.3f))
                .add(body.ptX(0.95f, -c * 0.02f), body.ptY(0.95f, -c * 0.02f))
                .add(body.ptX(0.3f, -c * 0.05f), body.ptY(0.3f, -c * 0.05f))
                .add((front + back) * 0.5f - 0.04f * k.dir, gnd - 0.03f)
                .add(back - 0.16f * k.dir, gnd - 0.03f)
                .add(body.ptX(0.4f, -c * 0.62f), body.ptY(0.4f, -c * 0.62f))
                .shapeShade(L.torso)
            p.detail(body.ptX(0.05f, c * 0.3f), body.ptY(0.05f, c * 0.3f), front + 0.04f * k.dir, gnd - 0.08f, 0.02f, ActorPaint.light(L.torso))
        }
        if (!p.ink) {
            p.detail(front + 0.1f * k.dir, gnd - 0.06f, back - 0.14f * k.dir, gnd - 0.06f, 0.05f, trim)
            // Fold shadows and a rope belt with a tassel.
            p.detail(body.ptX(0.2f, 0f), body.ptY(0.2f, 0f), (front + back) * 0.5f, gnd - 0.1f, 0.03f, Col.mul(L.torso, 0.7f))
            p.detail(body.ptX(0.08f, -k.waistD * 0.55f), body.ptY(0.08f, -k.waistD * 0.55f), body.ptX(0.08f, k.waistD * 0.55f), body.ptY(0.08f, k.waistD * 0.55f), 0.035f, trim)
            val tx = body.ptX(0.08f, k.waistD * 0.3f)
            val ty = body.ptY(0.08f, k.waistD * 0.3f)
            p.detail(tx, ty, tx + 0.02f * k.dir, ty + 0.2f + sin(f.t * 4f) * 0.01f, 0.02f, trim)
            if (L.rim != 0) p.detail(body.ptX(0.45f, -c * 0.58f), body.ptY(0.45f, -c * 0.58f), body.ptX(0.98f, -c * 0.28f), body.ptY(0.98f, -c * 0.28f), ActorPaint.RIM_W, L.rim)
        }
    }

    private fun sleeveArm(L: Look) {
        val l = k.armF
        val aw = k.limbW * L.armW
        p.bone(l.ax, l.ay, l.jx, l.jy, aw, aw * 0.95f, L.arms, true)
        // Bell sleeve widening toward the wrist.
        p.bone(l.jx, l.jy, l.ex, l.ey, aw * 0.95f, aw * 1.35f, L.arms, true)
        body.handAt(l.ex + (l.ex - l.jx) * 0.25f, l.ey + (l.ey - l.jy) * 0.25f, L.skin)
    }

    private fun guardDetails(z: Zone, dir: Int, L: Look, acc: Int) {
        if (p.ink) return
        val c = k.chestD
        val w = k.waistD
        when (z) {
            Zone.TOWER -> {
                // White shirt V, accent tie, lapel.
                p.begin()
                    .add(body.ptX(1.0f, c * 0.3f), body.ptY(1.0f, c * 0.3f))
                    .add(body.ptX(0.99f, c * 0.02f), body.ptY(0.99f, c * 0.02f))
                    .add(body.ptX(0.64f, c * 0.46f), body.ptY(0.64f, c * 0.46f))
                    .shapeDetail(0xFFE8E8F0.toInt())
                p.detail(body.ptX(0.96f, c * 0.2f), body.ptY(0.96f, c * 0.2f), body.ptX(0.66f, c * 0.42f), body.ptY(0.66f, c * 0.42f), 0.05f, acc)
                if (p.shading) {
                    // A notched lapel rolling back over the chest, and a pocket square.
                    p.begin()
                        .add(body.ptX(0.99f, c * 0.02f), body.ptY(0.99f, c * 0.02f))
                        .add(body.ptX(0.86f, -c * 0.02f), body.ptY(0.86f, -c * 0.02f))
                        .add(body.ptX(0.8f, c * 0.14f), body.ptY(0.8f, c * 0.14f))
                        .add(body.ptX(0.64f, c * 0.46f), body.ptY(0.64f, c * 0.46f))
                        .shapeDetail(ActorPaint.light(L.torso))
                    p.detail(body.ptX(0.86f, -c * 0.02f), body.ptY(0.86f, -c * 0.02f), body.ptX(0.64f, c * 0.44f), body.ptY(0.64f, c * 0.44f), 0.012f, ActorPaint.shade(L.torso))
                    p.detail(body.ptX(0.78f, -c * 0.2f), body.ptY(0.78f, -c * 0.2f), body.ptX(0.8f, -c * 0.32f), body.ptY(0.8f, -c * 0.32f), 0.03f, 0xFFE8E8F0.toInt())
                    p.dot(body.ptX(0.3f, c * 0.47f), body.ptY(0.3f, c * 0.47f), 0.013f, 0xFF050508.toInt())
                }
            }
            Zone.LABS -> {
                p.detail(body.ptX(0.5f, -w * 0.5f), body.ptY(0.5f, -w * 0.5f), body.ptX(0.5f, w * 0.52f), body.ptY(0.5f, w * 0.52f), 0.05f, acc)
                p.detail(body.ptX(0.02f, -w * 0.5f), body.ptY(0.02f, -w * 0.5f), body.ptX(0.02f, w * 0.5f), body.ptY(0.02f, w * 0.5f), 0.05f, 0xFF3A4A48.toInt())
            }
            Zone.METRO -> {
                for (i in 0 until 2) {
                    val a = if (i == 0) 0.42f else 0.72f
                    p.detail(body.ptX(a, -w * 0.52f), body.ptY(a, -w * 0.52f), body.ptX(a, c * 0.5f), body.ptY(a, c * 0.5f), 0.065f, acc)
                }
                p.dot(body.ptX(0.86f, c * 0.3f), body.ptY(0.86f, c * 0.3f), 0.03f, 0xFFFFD060.toInt())
                p.detail(body.ptX(0.03f, -w * 0.5f), body.ptY(0.03f, -w * 0.5f), body.ptX(0.03f, w * 0.5f), body.ptY(0.03f, w * 0.5f), 0.05f, 0xFF0C0C10.toInt())
            }
            Zone.MINES -> {
                // Overall bib and straps, reflective band.
                p.detail(body.ptX(0.98f, -c * 0.1f), body.ptY(0.98f, -c * 0.1f), body.ptX(0.55f, w * 0.1f), body.ptY(0.55f, w * 0.1f), 0.035f, Col.mul(L.torso, 0.65f))
                p.detail(body.ptX(0.98f, c * 0.22f), body.ptY(0.98f, c * 0.22f), body.ptX(0.6f, c * 0.45f), body.ptY(0.6f, c * 0.45f), 0.035f, Col.mul(L.torso, 0.65f))
                p.detail(body.ptX(0.35f, -w * 0.5f), body.ptY(0.35f, -w * 0.5f), body.ptX(0.35f, w * 0.52f), body.ptY(0.35f, w * 0.52f), 0.045f, acc)
                p.detail(k.legF.jx - 0.03f, k.legF.jy + 0.08f, k.legF.jx + 0.03f, k.legF.jy + 0.1f, 0.05f, acc)
            }
            Zone.MAGMA -> {
                // A warning band and a knee plate.
                p.detail(body.ptX(0.02f, -w * 0.5f), body.ptY(0.02f, -w * 0.5f), body.ptX(0.02f, w * 0.5f), body.ptY(0.02f, w * 0.5f), 0.06f, acc)
                p.detail(k.legF.jx - 0.04f, k.legF.jy, k.legF.jx + 0.04f, k.legF.jy, 0.06f, 0xFF4A423A.toInt())
            }
            else -> Unit
        }
    }

    private fun guardHead(e: Enemy, z: Zone, dir: Int, L: Look, pal: Palette) {
        val hx = k.headX
        val hy = k.headY
        val r = k.headR
        val acc = pal.enemyAccent
        when (z) {
            Zone.TOWER -> {
                face(hx, hy, r, L.skin, dir)
                // Slicked-back hair.
                p.begin()
                    .add(hx - r * 1.05f * dir, hy + r * 0.2f).add(hx - r * 0.95f * dir, hy - r * 0.6f)
                    .add(hx - r * 0.2f * dir, hy - r * 1.12f).add(hx + r * 0.75f * dir, hy - r * 0.78f)
                    .add(hx + r * 0.35f * dir, hy - r * 0.5f).add(hx - r * 0.45f * dir, hy - r * 0.2f)
                    .shape(0xFF0A0A10.toInt())
                if (p.shading) {
                    // A pomade sheen along the swept-back hair.
                    p.detail(hx - r * 0.7f * dir, hy - r * 0.62f, hx + r * 0.2f * dir, hy - r * 0.95f, 0.018f, 0xFF4A4A66.toInt())
                }
                if (!p.ink) {
                    // Shades with a neon glint, earpiece coil.
                    p.begin()
                        .add(hx + r * 0.05f * dir, hy - r * 0.28f).add(hx + r * 1.1f * dir, hy - r * 0.3f)
                        .add(hx + r * 1.05f * dir, hy + r * 0.12f).add(hx + r * 0.25f * dir, hy + r * 0.1f)
                        .shapeDetail(0xFF050508.toInt())
                    p.detail(hx + r * 0.35f * dir, hy - r * 0.16f, hx + r * 0.9f * dir, hy - r * 0.18f, 0.022f, Col.alpha(pal.neon, 0.95f))
                }
                body.headRim(hx, hy, r * 1.0f, look.rim)
                eyesAt(hx + r * 0.65f * dir, hy - r * 0.1f)
            }
            Zone.LABS -> {
                // Hazmat hood with a dark faceplate and a respirator filter.
                p.ball(hx, hy + r * 0.05f, r * 1.2f, L.torso, gloss = 0.3f)
                p.begin()
                    .add(hx + r * 0.05f * dir, hy - r * 0.6f).add(hx + r * 1.12f * dir, hy - r * 0.45f)
                    .add(hx + r * 1.15f * dir, hy + r * 0.35f).add(hx + r * 0.1f * dir, hy + r * 0.3f)
                    .shape(0xFF0E1A1A.toInt())
                p.disc(hx + r * 0.95f * dir, hy + r * 0.72f, r * 0.42f, 0xFF3A4848.toInt())
                if (!p.ink) {
                    p.detail(hx + r * 0.3f * dir, hy - r * 0.4f, hx + r * 0.95f * dir, hy - r * 0.32f, 0.018f, Col.alpha(acc, 0.9f))
                    if (p.shading) p.dot(hx + r * 0.95f * dir, hy - r * 0.35f, r * 0.1f, 0xC0FFFFFF.toInt())
                    p.dot(hx + r * 0.95f * dir, hy + r * 0.72f, r * 0.2f, acc)
                    p.detail(hx - r * 0.7f * dir, hy - r * 0.8f, hx + r * 0.2f * dir, hy - r * 1.1f, 0.03f, 0x80FFFFFF.toInt())
                }
                body.headRim(hx, hy, r * 1.2f, look.rim)
                eyesAt(hx + r * 0.7f * dir, hy - r * 0.1f)
            }
            Zone.METRO -> {
                face(hx, hy, r, L.skin, dir)
                // Peaked cap: crown, band, brim, badge.
                p.begin()
                    .add(hx - r * 1.05f * dir, hy - r * 0.35f).add(hx - r * 0.9f * dir, hy - r * 1.25f)
                    .add(hx + r * 0.95f * dir, hy - r * 1.35f).add(hx + r * 0.95f * dir, hy - r * 0.45f)
                    .shape(0xFF141C2C.toInt())
                p.seg(hx + r * 0.4f * dir, hy - r * 0.38f, hx + r * 1.45f * dir, hy - r * 0.25f, 0.04f, 0xFF08080C.toInt())
                if (!p.ink) {
                    p.detail(hx - r * 0.95f * dir, hy - r * 0.5f, hx + r * 0.92f * dir, hy - r * 0.55f, 0.035f, 0xFF0A0E16.toInt())
                    p.dot(hx + r * 0.55f * dir, hy - r * 0.9f, 0.03f, 0xFFFFD060.toInt())
                    if (p.shading) {
                        p.detail(hx - r * 0.7f * dir, hy - r * 1.18f, hx + r * 0.7f * dir, hy - r * 1.26f, 0.016f, 0xFF3A4660.toInt())
                    }
                    p.dot(hx + r * 0.72f * dir, hy - r * 0.05f, 0.024f, 0xFF101018.toInt())
                }
                body.headRim(hx, hy, r * 1.0f, look.rim)
                eyesAt(hx + r * 0.72f * dir, hy - r * 0.05f)
            }
            Zone.MINES -> {
                face(hx, hy, r, L.skin, dir)
                // Bandana over the mouth.
                p.begin()
                    .add(hx - r * 0.2f * dir, hy + r * 0.1f).add(hx + r * 1.1f * dir, hy + r * 0.05f)
                    .add(hx + r * 0.9f * dir, hy + r * 1.0f).add(hx + r * 0.1f * dir, hy + r * 0.9f)
                    .shape(0xFFB0302A.toInt())
                // Hard hat.
                p.begin()
                    .add(hx - r * 1.05f * dir, hy - r * 0.35f).add(hx - r * 0.75f * dir, hy - r * 1.05f)
                    .add(hx + r * 0.1f * dir, hy - r * 1.35f).add(hx + r * 0.85f * dir, hy - r * 1.0f)
                    .add(hx + r * 1.05f * dir, hy - r * 0.4f)
                    .shape(acc)
                if (p.shading) {
                    p.begin()
                        .add(hx - r * 1.05f * dir, hy - r * 0.35f).add(hx - r * 0.75f * dir, hy - r * 1.05f)
                        .add(hx - r * 0.3f * dir, hy - r * 1.28f).add(hx - r * 0.35f * dir, hy - r * 0.4f)
                        .shapeShade(acc)
                }
                p.seg(hx - r * 1.2f * dir, hy - r * 0.38f, hx + r * 1.3f * dir, hy - r * 0.38f, 0.035f, Col.mul(acc, 0.8f))
                if (!p.ink) {
                    p.detail(hx - r * 0.5f * dir, hy - r * 1.0f, hx + r * 0.2f * dir, hy - r * 1.2f, 0.025f, 0x90FFFFFF.toInt())
                    p.dot(hx + r * 0.62f * dir, hy - r * 0.12f, 0.018f, 0xFF101018.toInt())
                    if (e.alive) {
                        val lx = hx + r * 0.95f * dir
                        val ly = hy - r * 0.72f
                        f.glowDot(lx, ly, 0.035f, 0xFFFFF4C0.toInt(), p.alphaMul)
                        p.quad(lx, ly - 0.03f, lx, ly + 0.03f, lx + 2.2f * dir, ly + 0.9f, lx + 2.2f * dir, ly - 0.35f, p.c(0x1AFFF4C0))
                    }
                }
                body.headRim(hx, hy, r * 1.0f, look.rim)
                eyesAt(hx + r * 0.62f * dir, hy - r * 0.12f)
            }
            Zone.MAGMA -> {
                // Aluminised heat hood with a big gold visor.
                p.begin()
                    .add(hx - r * 1.15f * dir, hy + r * 0.9f).add(hx - r * 1.2f * dir, hy - r * 0.5f)
                    .add(hx - r * 0.6f * dir, hy - r * 1.2f).add(hx + r * 0.7f * dir, hy - r * 1.15f)
                    .add(hx + r * 1.2f * dir, hy - r * 0.4f).add(hx + r * 1.15f * dir, hy + r * 0.9f)
                    .shape(L.torso)
                p.begin()
                    .add(hx + r * 0.0f * dir, hy - r * 0.75f).add(hx + r * 1.1f * dir, hy - r * 0.6f)
                    .add(hx + r * 1.12f * dir, hy + r * 0.45f).add(hx + r * 0.1f * dir, hy + r * 0.5f)
                    .shape(0xFFFFB81E.toInt())
                if (!p.ink) {
                    if (p.shading) {
                        p.begin()
                            .add(hx + r * 0.1f * dir, hy + r * 0.05f).add(hx + r * 1.11f * dir, hy + r * 0.0f)
                            .add(hx + r * 1.12f * dir, hy + r * 0.45f).add(hx + r * 0.1f * dir, hy + r * 0.5f)
                            .shapeDetail(0xFFB86A0A.toInt())
                        p.detail(hx - r * 1.1f * dir, hy - r * 0.2f, hx - r * 1.1f * dir, hy + r * 0.8f, 0.03f, ActorPaint.shade(L.torso))
                    }
                    p.detail(hx + r * 0.3f * dir, hy - r * 0.5f, hx + r * 0.55f * dir, hy + r * 0.3f, 0.03f, 0xB0FFFFFF.toInt())

                    p.detail(hx - r * 0.8f * dir, hy - r * 0.9f, hx + r * 0.3f * dir, hy - r * 1.05f, 0.03f, L.torsoLit)
                }
                body.headRim(hx, hy, r * 1.2f, look.rim)
                eyesAt(hx + r * 0.7f * dir, hy - r * 0.1f)
            }
            else -> {
                // Pointed cult hood, the face lost in shadow except the eyes.
                p.begin()
                    .add(hx - r * 1.15f * dir, hy + r * 1.0f).add(hx - r * 1.1f * dir, hy - r * 0.4f)
                    .add(hx - r * 0.9f * dir, hy - r * 2.3f).add(hx + r * 0.8f * dir, hy - r * 0.9f)
                    .add(hx + r * 1.2f * dir, hy + r * 0.3f).add(hx + r * 0.9f * dir, hy + r * 1.0f)
                    .shape(L.torso)
                p.begin()
                    .add(hx + r * 0.1f * dir, hy - r * 0.6f).add(hx + r * 0.95f * dir, hy - r * 0.5f)
                    .add(hx + r * 1.05f * dir, hy + r * 0.5f).add(hx + r * 0.2f * dir, hy + r * 0.7f)
                    .shape(0xFF0E0204.toInt())
                if (!p.ink) {
                    p.detail(hx + r * 0.95f * dir, hy - r * 0.5f, hx + r * 1.05f * dir, hy + r * 0.5f, 0.03f, pal.enemyAccent)
                    val eye = 0xFFFFC040.toInt()
                    f.glowDot(hx + r * 0.75f * dir, hy - r * 0.05f, 0.022f, eye, p.alphaMul * (1f - p.flatAmt))
                    f.glowDot(hx + r * 0.35f * dir, hy - r * 0.05f, 0.022f, eye, p.alphaMul * (1f - p.flatAmt))
                    if (L.rim != 0) p.detail(hx - r * 1.05f * dir, hy - r * 0.2f, hx - r * 0.85f * dir, hy - r * 1.9f, ActorPaint.RIM_W, L.rim)
                }
                eyesAt(hx + r * 0.6f * dir, hy - r * 0.05f)
            }
        }
    }

    // ------------------------------------------------------------- elites

    private fun mixf(a: Float, b: Float, t: Float) = a + (b - a) * t

    /**
     * An armour plate a-b-c-d, a->b its lamp-facing top edge: its own ink seam (so layered
     * plates read as separate slabs), the underside turned into shadow, a bevelled lit rim along
     * the top and one hard specular glint near its front end.
     */
    private fun plate(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float, color: Int, shadow: Float = 0.42f, spec: Float = 1f) {
        // Painted from the lamp-lit top edge down into shadow; [shadow] pulls the midtone up.
        p.begin().add(ax, ay).add(bx, by).add(cx, cy).add(dx, dy)
            .shapeLit(color, (ax + bx) * 0.5f, (ay + by) * 0.5f, (cx + dx) * 0.5f, (cy + dy) * 0.5f, sep = true, mid = 1f - shadow)
        if (!p.shading) return
        // A bevel line along the top, then the glint.
        val i = 0.1f
        p.detail(
            mixf(mixf(ax, bx, 0.06f), mixf(dx, cx, 0.06f), i), mixf(mixf(ay, by, 0.06f), mixf(dy, cy, 0.06f), i),
            mixf(mixf(ax, bx, 0.94f), mixf(dx, cx, 0.94f), i), mixf(mixf(ay, by, 0.94f), mixf(dy, cy, 0.94f), i),
            0.018f, ActorPaint.light(ActorPaint.light(color)),
        )
        if (spec > 0f) {
            p.detail(
                mixf(mixf(ax, bx, 0.6f), mixf(dx, cx, 0.6f), i), mixf(mixf(ay, by, 0.6f), mixf(dy, cy, 0.6f), i),
                mixf(mixf(ax, bx, 0.86f), mixf(dx, cx, 0.86f), i), mixf(mixf(ay, by, 0.86f), mixf(dy, cy, 0.86f), i),
                0.016f, Col.alpha(0xFFFFFFFF.toInt(), 0.85f * spec),
            )
        }
    }

    /** A plate strapped along a bone from [t0] to [t1] of it, [w0] to [w1] wide, lit on the lamp side. */
    private fun limbPlate(x1: Float, y1: Float, x2: Float, y2: Float, t0: Float, t1: Float, w0: Float, w1: Float, color: Int, spec: Float = 1f) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        var nx = -dy / len
        var ny = dx / len
        if (nx * p.lightX + ny * p.lightY < 0f) {
            nx = -nx; ny = -ny
        }
        val ax = x1 + dx * t0
        val ay = y1 + dy * t0
        val bx = x1 + dx * t1
        val by = y1 + dy * t1
        plate(
            ax + nx * w0 * 0.5f, ay + ny * w0 * 0.5f, bx + nx * w1 * 0.5f, by + ny * w1 * 0.5f,
            bx - nx * w1 * 0.5f, by - ny * w1 * 0.5f, ax - nx * w0 * 0.5f, ay - ny * w0 * 0.5f,
            color, 0.45f, spec,
        )
    }

    /** A soft additive bloom and a hot core line: something that truly emits (visors, lava). */
    private fun emissive(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int, core: Int) {
        if (p.ink) return
        val a = p.alphaMul * (1f - p.flatAmt)
        if (p.shading && a > 0.05f) {
            g.blend(Gfx.Blend.ADD)
            g.line(x1, y1, x2, y2, w * 3.2f, Col.fade(Col.alpha(color, 0.22f), a))
            g.blend(Gfx.Blend.NORMAL)
        }
        p.detail(x1, y1, x2, y2, w, color)
        if (p.hi) p.detail(x1, y1, x2, y2, w * 0.42f, core)
    }

    /**
     * The heavy: a walking wall of armour. Head sunk between a high gorget and a slab of a
     * pauldron, a barrel cuirass, a tasset over the hip, knee cops and greaves, a bucket helm with
     * one glowing visor slit, a power pack on his back feeding the cannon.
     */
    private fun heavy(e: Enemy, dir: Int, zone: Zone, pal: Palette, armed: Boolean) {
        val lightZone = zone == Zone.LABS || zone == Zone.MAGMA
        // Plates in the zone's uniform colour, lifted to read as polished metal; a near-black undersuit.
        val plate = when {
            lightZone -> Col.lerp(pal.enemyMain, 0xFF9AA0A8.toInt(), 0.25f)
            zone == Zone.HELL -> Col.lerp(pal.enemyMain, 0xFF5E5660.toInt(), 0.62f)
            else -> Col.lerp(pal.enemyMain, 0xFF8A92A8.toInt(), 0.45f)
        }
        val suit = Col.lerp(pal.enemyMain, 0xFF121218.toInt(), if (lightZone) 0.8f else 0.5f)
        val dark = Col.lerp(plate, 0xFF0A0A10.toInt(), 0.55f)
        val acc = pal.enemyAccent
        val hot = Col.lerp(acc, 0xFFFFFFFF.toInt(), 0.65f)
        val L = look
        L.torso = suit; L.torsoLit = Col.lerp(suit, 0xFFFFFFFF.toInt(), 0.12f); L.legs = suit; L.legsFar = Col.mul(suit, 0.7f)
        L.arms = suit; L.armsFar = Col.mul(suit, 0.7f); L.boots = 0xFF0C0C12.toInt(); L.gloves = 0xFF1A1B24.toInt(); L.skin = plate
        L.rim = rimOf(pal, 0.55f); L.legW = 1.1f; L.armW = 1.0f
        val hx = k.headX
        val hy = k.headY
        val r = k.headR
        val c = k.chestD
        val w = k.waistD
        val lw = k.limbW * L.legW
        val far = Col.mul(plate, 0.72f)
        p.lightFrom(dir)
        p.twoPass {
            body.arm(k.armB, L, far = true)
            body.leg(k.legB, L, far = true)
            p.lightFrom(dir)
            limbPlate(k.legB.jx, k.legB.jy, k.legB.ex, k.legB.ey, 0.08f, 0.82f, lw * 1.25f, lw * 0.95f, far, 0f)
            // The power pack: a slab on his back, lit on top, an accent status strip.
            val pa = body.ptX(0.34f, -c * 0.5f); val pb = body.ptY(0.34f, -c * 0.5f)
            plate(
                body.ptX(1.02f, -c * 0.48f), body.ptY(1.02f, -c * 0.48f), body.ptX(1.06f, -c * 1.02f), body.ptY(1.06f, -c * 1.02f),
                body.ptX(0.3f, -c * 1.0f), body.ptY(0.3f, -c * 1.0f), pa, pb, dark, 0.5f, 0.5f,
            )
            if (!p.ink) {
                emissive(body.ptX(0.88f, -c * 0.99f), body.ptY(0.88f, -c * 0.99f), body.ptX(0.52f, -c * 0.98f), body.ptY(0.52f, -c * 0.98f), 0.028f, acc, hot)
            }
            // Feed hose from the pack under the arm to the cannon.
            if (armed && showGun && abs(gunX - k.hipX) < 0.6f && abs(gunY - k.hipY) < 0.6f) {
                val mx = body.ptX(0.12f, -c * 0.2f)
                val my = body.ptY(0.12f, -c * 0.2f) + 0.06f
                p.seg(body.ptX(0.34f, -c * 0.8f), body.ptY(0.34f, -c * 0.8f), mx, my, 0.07f, 0xFF15161E.toInt())
                p.seg(mx, my, gunX - 0.02f * dir, gunY + 0.05f, 0.07f, 0xFF15161E.toInt())
                if (p.shading) {
                    p.detail(body.ptX(0.34f, -c * 0.8f), body.ptY(0.34f, -c * 0.8f) - 0.018f, mx, my - 0.02f, 0.012f, 0xFF3A3C4C.toInt())
                    p.detail(mx, my - 0.02f, gunX - 0.02f * dir, gunY + 0.03f, 0.012f, 0xFF3A3C4C.toInt())
                }
            }
            body.leg(k.legF, L, far = false)
            p.lightFrom(dir)
            // Greave down the shin, a domed knee cop over the joint.
            limbPlate(k.legF.jx, k.legF.jy, k.legF.ex, k.legF.ey, 0.1f, 0.84f, lw * 1.3f, lw * 1.0f, plate)
            p.ball(k.legF.jx + 0.015f * dir, k.legF.jy, lw * 0.5f, plate, gloss = 0.3f)
            body.torso(L, chest = 1.05f)
            p.lightFrom(dir)
            // Tasset: a skirt plate hung from the belt over the front thigh.
            limbPlate(k.legF.ax, k.legF.ay, k.legF.jx, k.legF.jy, -0.12f, 0.5f, lw * 1.9f, lw * 1.65f, plate, 0.6f)
            // Belly: two banded lames under the cuirass.
            for (i in 0 until 2) {
                val a0 = 0.12f + i * 0.17f
                val a1 = a0 + 0.17f
                plate(
                    body.ptX(a1, -w * 0.46f), body.ptY(a1, -w * 0.46f), body.ptX(a1, w * 0.6f), body.ptY(a1, w * 0.6f),
                    body.ptX(a0, w * 0.58f), body.ptY(a0, w * 0.58f), body.ptX(a0, -w * 0.44f), body.ptY(a0, -w * 0.44f),
                    Col.mul(plate, 0.86f), 0.5f, 0f,
                )
            }
            // The cuirass: one barrel of a chest plate, jutting past the ribs.
            p.begin()
                .add(body.ptX(1.02f, -c * 0.46f), body.ptY(1.02f, -c * 0.46f))
                .add(body.ptX(1.08f, c * 0.36f), body.ptY(1.08f, c * 0.36f))
                .add(body.ptX(0.88f, c * 0.8f), body.ptY(0.88f, c * 0.8f))
                .add(body.ptX(0.62f, c * 0.72f), body.ptY(0.62f, c * 0.72f))
                .add(body.ptX(0.44f, w * 0.5f), body.ptY(0.44f, w * 0.5f))
                .add(body.ptX(0.46f, -w * 0.5f), body.ptY(0.46f, -w * 0.5f))
                .shapeLit(plate, body.ptX(1.06f, c * 0.4f), body.ptY(1.06f, c * 0.4f), body.ptX(0.45f, -w * 0.4f), body.ptY(0.45f, -w * 0.4f), sep = true, mid = 0.4f)
            if (p.shading) {
                // Upper plane of the chest in the lamp, and a hard specular streak across it.
                p.begin()
                    .add(body.ptX(1.02f, -c * 0.4f), body.ptY(1.02f, -c * 0.4f))
                    .add(body.ptX(1.07f, c * 0.34f), body.ptY(1.07f, c * 0.34f))
                    .add(body.ptX(0.9f, c * 0.72f), body.ptY(0.9f, c * 0.72f))
                    .add(body.ptX(0.86f, -c * 0.4f), body.ptY(0.86f, -c * 0.4f))
                    .shapeDetail(Col.alpha(ActorPaint.light(plate), 0.75f))
                p.detail(body.ptX(0.99f, c * 0.2f), body.ptY(0.99f, c * 0.2f), body.ptX(0.9f, c * 0.62f), body.ptY(0.9f, c * 0.62f), 0.02f, Col.alpha(0xFFFFFFFF.toInt(), 0.8f))
                // The keel ridge down the middle of the plate.
                p.detail(body.ptX(0.98f, c * 0.02f), body.ptY(0.98f, c * 0.02f), body.ptX(0.5f, c * 0.02f), body.ptY(0.5f, c * 0.02f), 0.016f, ActorPaint.shade(plate))
            }
            if (!p.ink) {
                // One accent light on the chest: the heavy's reactor.
                emissive(body.ptX(0.8f, c * 0.6f), body.ptY(0.8f, c * 0.6f), body.ptX(0.68f, c * 0.58f), body.ptY(0.68f, c * 0.58f), 0.034f, acc, hot)
            }
            // Gorget: a high collar the helmet sinks into.
            p.begin()
                .add(body.ptX(0.9f, -c * 0.5f), body.ptY(0.9f, -c * 0.5f))
                .add(body.ptX(1.22f, -c * 0.42f), body.ptY(1.22f, -c * 0.42f))
                .add(body.ptX(1.2f, c * 0.3f), body.ptY(1.2f, c * 0.3f))
                .add(body.ptX(0.98f, c * 0.46f), body.ptY(0.98f, c * 0.46f))
                .shape(Col.mul(plate, 0.8f), sep = true)
            if (p.shading) {
                p.detail(body.ptX(1.19f, -c * 0.38f), body.ptY(1.19f, -c * 0.38f), body.ptX(1.17f, c * 0.26f), body.ptY(1.17f, c * 0.26f), 0.02f, ActorPaint.light(plate))
            }
            heavyHelm(e, dir, zone, plate, dark, acc, hot, hx, hy + r * 0.12f, r)
        }
        body.headRim(hx, hy + r * 0.12f, r * 1.15f, look.rim)
        eyesAt(hx + r * 0.75f * dir, hy - r * 0.02f)
        p.twoPass {
            if (armed && showGun) body.gun(3, gunX, gunY, gunUp, acc, spin = if (e.state == EnemyState.AIM) f.t * 40f else 0f, scale = 0.85f)
            body.arm(k.armF, L, far = false)
            p.lightFrom(dir)
            val aw = k.limbW * L.armW
            // Vambrace on the forearm.
            limbPlate(k.armF.jx, k.armF.jy, k.armF.ex, k.armF.ey, 0.12f, 0.78f, aw * 1.15f, aw * 1.0f, plate, 0.7f)
            // The pauldron: a big layered slab over the near shoulder, and a lame under it.
            val sx = k.armF.ax
            val sy = k.armF.ay
            val ux = k.ux
            val uy = k.uy
            val nx = k.nx
            val ny = k.ny
            // Lower lame first, then the dome over it.
            plate(
                sx - nx * 0.114f - ux * 0.026f, sy - ny * 0.114f - uy * 0.026f, sx + nx * 0.132f - ux * 0.035f, sy + ny * 0.132f - uy * 0.035f,
                sx + nx * 0.106f - ux * 0.15f, sy + ny * 0.106f - uy * 0.15f, sx - nx * 0.097f - ux * 0.141f, sy - ny * 0.097f - uy * 0.141f,
                Col.mul(plate, 0.8f), 0.5f, 0f,
            )
            p.begin()
                .add(sx - nx * 0.141f - ux * 0.018f, sy - ny * 0.141f - uy * 0.018f)
                .add(sx - nx * 0.123f + ux * 0.088f, sy - ny * 0.123f + uy * 0.088f)
                .add(sx - nx * 0.026f + ux * 0.158f, sy - ny * 0.026f + uy * 0.158f)
                .add(sx + nx * 0.088f + ux * 0.132f, sy + ny * 0.088f + uy * 0.132f)
                .add(sx + nx * 0.15f + ux * 0.035f, sy + ny * 0.15f + uy * 0.035f)
                .add(sx + nx * 0.123f - ux * 0.062f, sy + ny * 0.123f - uy * 0.062f)
                .add(sx - nx * 0.018f - ux * 0.079f, sy - ny * 0.018f - uy * 0.079f)
                .shape(plate, sep = true)
            if (p.shading) {
                p.begin()
                    .add(sx + nx * 0.15f + ux * 0.0f, sy + ny * 0.15f + uy * 0.0f)
                    .add(sx + nx * 0.123f - ux * 0.062f, sy + ny * 0.123f - uy * 0.062f)
                    .add(sx - nx * 0.018f - ux * 0.079f, sy - ny * 0.018f - uy * 0.079f)
                    .add(sx - nx * 0.141f - ux * 0.018f, sy - ny * 0.141f - uy * 0.018f)
                    .add(sx - nx * 0.088f + ux * 0.026f, sy - ny * 0.088f + uy * 0.026f)
                    .shapeShade(plate)
                p.begin()
                    .add(sx - nx * 0.123f + ux * 0.088f, sy - ny * 0.123f + uy * 0.088f)
                    .add(sx - nx * 0.026f + ux * 0.158f, sy - ny * 0.026f + uy * 0.158f)
                    .add(sx + nx * 0.088f + ux * 0.132f, sy + ny * 0.088f + uy * 0.132f)
                    .add(sx + nx * 0.07f + ux * 0.079f, sy + ny * 0.07f + uy * 0.079f)
                    .add(sx - nx * 0.07f + ux * 0.07f, sy - ny * 0.07f + uy * 0.07f)
                    .shapeDetail(Col.alpha(ActorPaint.light(plate), 0.85f))
                p.detail(sx - nx * 0.018f + ux * 0.128f, sy - ny * 0.018f + uy * 0.128f, sx + nx * 0.07f + ux * 0.11f, sy + ny * 0.07f + uy * 0.11f, 0.018f, Col.alpha(0xFFFFFFFF.toInt(), 0.9f))
            }
            if (!p.ink) {
                // A dark seam where the dome overlaps the lame.
                p.detail(sx - nx * 0.12f - ux * 0.035f, sy - ny * 0.12f - uy * 0.035f, sx + nx * 0.13f - ux * 0.05f, sy + ny * 0.13f - uy * 0.05f, 0.014f, dark)
            }
        }
        if (e.state == EnemyState.AIM && armed) f.glowDot(body.muzzleX, body.muzzleY, 0.04f, pal.laser, p.alphaMul)
    }

    /** The heavy's bucket helm: flat crown, sloped brow, a deep jaw, one glowing visor slit. */
    private fun heavyHelm(e: Enemy, dir: Int, zone: Zone, plate: Int, dark: Int, acc: Int, hot: Int, hx: Float, hy: Float, r: Float) {
        val d = dir.toFloat()
        val R = r * 1.2f
        if (zone == Zone.HELL) {
            // Bone horns bolted to the helm, sweeping back and up.
            val bone = 0xFFE8D8C0.toInt()
            p.begin()
                .add(hx + R * 0.3f * d, hy - R * 0.6f).add(hx - R * 0.25f * d, hy - R * 0.75f)
                .add(hx - R * 0.65f * d, hy - R * 1.45f).add(hx - R * 0.4f * d, hy - R * 2.25f)
                .add(hx - R * 0.02f * d, hy - R * 1.3f)
                .shape(bone)
            p.begin()
                .add(hx - R * 0.55f * d, hy - R * 0.3f).add(hx - R * 0.8f * d, hy - R * 0.55f)
                .add(hx - R * 1.55f * d, hy - R * 1.05f).add(hx - R * 2.2f * d, hy - R * 0.95f)
                .add(hx - R * 1.2f * d, hy - R * 0.55f)
                .shape(Col.mul(bone, 0.8f))
            if (p.shading) {
                p.detail(hx - R * 0.12f * d, hy - R * 0.85f, hx - R * 0.35f * d, hy - R * 1.7f, 0.016f, 0xFFFFF4E4.toInt())
                p.detail(hx + R * 0.1f * d, hy - R * 0.8f, hx - R * 0.25f * d, hy - R * 1.5f, 0.02f, Col.mul(bone, 0.62f))
            }
        }
        p.begin()
            .add(hx - R * 0.88f * d, hy - R * 0.5f)
            .add(hx - R * 0.45f * d, hy - R * 0.95f)
            .add(hx + R * 0.5f * d, hy - R * 0.98f)
            .add(hx + R * 1.02f * d, hy - R * 0.45f)
            .add(hx + R * 1.06f * d, hy + R * 0.32f)
            .add(hx + R * 0.78f * d, hy + R * 0.82f)
            .add(hx - R * 0.35f * d, hy + R * 0.86f)
            .add(hx - R * 0.95f * d, hy + R * 0.42f)
            .shapeLit(plate, hx + R * 0.4f * d, hy - R * 1.0f, hx - R * 0.5f * d, hy + R * 0.8f)
        if (p.shading) {
            p.begin()
                .add(hx - R * 0.45f * d, hy - R * 0.95f)
                .add(hx + R * 0.5f * d, hy - R * 0.98f)
                .add(hx + R * 0.86f * d, hy - R * 0.62f)
                .add(hx - R * 0.55f * d, hy - R * 0.66f)
                .shapeDetail(Col.alpha(ActorPaint.light(plate), 0.8f))
            p.detail(hx - R * 0.1f * d, hy - R * 0.86f, hx + R * 0.5f * d, hy - R * 0.87f, 0.02f, Col.alpha(0xFFFFFFFF.toInt(), 0.85f))
            // Crest ridge from brow to nape.
            p.detail(hx + R * 0.7f * d, hy - R * 0.7f, hx - R * 0.7f * d, hy - R * 0.62f, 0.022f, ActorPaint.shade(plate))
        }
        // Jaw guard: a darker slab below the visor.
        p.begin()
            .add(hx + R * 0.05f * d, hy + R * 0.12f)
            .add(hx + R * 1.1f * d, hy + R * 0.1f)
            .add(hx + R * 0.86f * d, hy + R * 0.8f)
            .add(hx + R * 0.02f * d, hy + R * 0.86f)
            .shape(Col.mul(plate, 0.62f), sep = true)
        if (p.shading) {
            p.detail(hx + R * 0.3f * d, hy + R * 0.5f, hx + R * 0.86f * d, hy + R * 0.48f, 0.02f, dark)
            p.detail(hx + R * 0.1f * d, hy + R * 0.2f, hx + R * 1.02f * d, hy + R * 0.18f, 0.016f, ActorPaint.light(Col.mul(plate, 0.62f)))
        }
        if (!p.ink) {
            // The visor: a recessed black band, the slit glowing in the zone accent.
            p.begin()
                .add(hx + R * 0.02f * d, hy - R * 0.36f)
                .add(hx + R * 1.06f * d, hy - R * 0.42f)
                .add(hx + R * 1.08f * d, hy - R * 0.02f)
                .add(hx + R * 0.05f * d, hy + R * 0.02f)
                .shapeDetail(0xFF06060A.toInt())
            val on = if (e.alive || e.state == EnemyState.CHOKED) 1f else 0.25f
            emissive(hx + R * 0.26f * d, hy - R * 0.19f, hx + R * 1.02f * d, hy - R * 0.23f, 0.038f, Col.fade(acc, on), Col.fade(hot, on))
            if (zone == Zone.MINES && e.alive) f.glowDot(hx + R * 0.2f * d, hy - R * 0.98f, 0.035f, 0xFFFFF4C0.toInt(), p.alphaMul)
        }
    }

    private val tailX = FloatArray(12)
    private val tailY = FloatArray(12)

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
            val hw = w0 * 0.5f * (1f - i / (n - 1f) * 0.75f)
            p.add(tailX[j] - dy / len * hw, tailY[j] + dx / len * hw)
        }
        for (i in n - 1 downTo 0) {
            val j = from + i
            val a = min(j + 1, from + n - 1)
            val b = max(j - 1, from)
            val dx = tailX[a] - tailX[b]
            val dy = tailY[a] - tailY[b]
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
            val hw = w0 * 0.5f * (1f - i / (n - 1f) * 0.75f)
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
        val main = 0xFF1E1E30.toInt()
        val lit = 0xFF56567C.toInt()
        val far = 0xFF0A0A12.toInt()
        val band = if (zone == Zone.HELL) 0xFFFFB020.toInt() else pal.enemyAccent
        val wrap = 0xFF2C2C42.toInt()
        val L = look
        L.torso = main; L.torsoLit = lit; L.legs = main; L.legsFar = far
        L.arms = main; L.armsFar = far; L.boots = 0xFF14141E.toInt(); L.gloves = 0xFF14141E.toInt(); L.skin = 0xFFE8C8A8.toInt()
        L.rim = rimOf(pal, 0.7f); L.legW = 0.86f; L.armW = 0.86f
        val hx = k.headX
        val hy = k.headY
        val r = k.headR
        val d = dir.toFloat()
        val speed = min(1f, abs(e.vx) / 3f)
        streamTail(0, 4, hx - r * 0.95f * d, hy - r * 0.55f, 0.15f, dir, speed, e.id.toFloat(), 1.0f)
        streamTail(4, 4, hx - r * 0.95f * d, hy - r * 0.45f, 0.12f, dir, speed, e.id + 2.2f, 0.6f)
        streamTail(8, 3, body.ptX(0.02f, -k.waistD * 0.5f), body.ptY(0.02f, -k.waistD * 0.5f), 0.1f, dir, speed, e.id + 4.1f, 0.2f)
        p.lightFrom(dir)
        p.twoPass {
            ribbon(4, 4, 0.05f, Col.mul(band, 0.7f))
            ribbon(0, 4, 0.065f, band)
            body.arm(k.armB, L, far = true)
            body.leg(k.legB, L, far = true)
            // Empty scabbard across the back: black lacquer, a lit edge, an accent chape.
            val s0x = body.ptX(0.12f, -k.waistD * 0.75f); val s0y = body.ptY(0.12f, -k.waistD * 0.75f)
            val s1x = body.ptX(1.08f, -k.chestD * 0.25f); val s1y = body.ptY(1.08f, -k.chestD * 0.25f)
            p.seg(s0x, s0y, s1x, s1y, 0.05f, 0xFF120C12.toInt())
            if (!p.ink) {
                if (p.shading) p.detail(mixf(s0x, s1x, 0.15f), mixf(s0y, s1y, 0.15f) - 0.01f, mixf(s0x, s1x, 0.9f), mixf(s0y, s1y, 0.9f) - 0.01f, 0.012f, 0xFF5A4A5A.toInt())
                p.detail(s0x, s0y, mixf(s0x, s1x, 0.06f), mixf(s0y, s1y, 0.06f), 0.04f, 0xFFB8903A.toInt())
            }
            ribbon(8, 3, 0.05f, Col.mul(band, 0.85f))
            body.leg(k.legF, L, far = false)
            if (p.shading) wraps(k.legF, wrap)
            body.torso(L, chest = 0.94f)
            if (!p.ink) {
                val c = k.chestD * 0.94f
                val w = k.waistD
                // Crossed jacket: the lapel running from the far shoulder down to the sash.
                if (p.shading) {
                    p.begin()
                        .add(body.ptX(1.0f, c * 0.05f), body.ptY(1.0f, c * 0.05f))
                        .add(body.ptX(1.0f, c * 0.3f), body.ptY(1.0f, c * 0.3f))
                        .add(body.ptX(0.3f, w * 0.52f), body.ptY(0.3f, w * 0.52f))
                        .add(body.ptX(0.3f, w * 0.3f), body.ptY(0.3f, w * 0.3f))
                        .shapeDetail(Col.alpha(lit, 0.6f))
                }
                // Sash: a wide band of the accent, its knot and a shadow fold.
                p.begin()
                    .add(body.ptX(0.18f, -w * 0.54f), body.ptY(0.18f, -w * 0.54f))
                    .add(body.ptX(0.2f, w * 0.54f), body.ptY(0.2f, w * 0.54f))
                    .add(body.ptX(0.02f, w * 0.52f), body.ptY(0.02f, w * 0.52f))
                    .add(body.ptX(0.0f, -w * 0.52f), body.ptY(0.0f, -w * 0.52f))
                    .shapeDetail(band)
                if (p.shading) {
                    p.detail(body.ptX(0.17f, -w * 0.5f), body.ptY(0.17f, -w * 0.5f), body.ptX(0.19f, w * 0.5f), body.ptY(0.19f, w * 0.5f), 0.014f, ActorPaint.light(band))
                    p.detail(body.ptX(0.03f, -w * 0.48f), body.ptY(0.03f, -w * 0.48f), body.ptX(0.05f, w * 0.48f), body.ptY(0.05f, w * 0.48f), 0.02f, ActorPaint.shade(band))
                }
            }
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
                    .shapeDetail(0xFF9AA4B8.toInt())
                if (p.shading) {
                    p.detail(hx + r * 0.48f * d, hy - r * 0.74f, hx + r * 0.86f * d, hy - r * 0.74f, 0.014f, 0xFFFFFFFF.toInt())
                    // The hood's crown in the lamp.
                    p.detail(hx - r * 0.55f * d, hy - r * 0.78f, hx + r * 0.25f * d, hy - r * 0.93f, 0.028f, lit)
                }
                // The knot at the back of the head.
                p.dot(hx - r * 0.92f * d, hy - r * 0.52f, 0.03f, band)
            }
        }
        body.headRim(hx, hy, r * 1.0f, look.rim)
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
        p.shape(0xFF6A7488.toInt())
        if (!p.ink) {
            // The flat of the blade in the lamp, the temper line and the bright edge.
            for (i in 0 until 4) {
                val t0 = i / 4f
                val t1 = (i + 1) / 4f
                bladeAt(bx, by, dx, dy, nx, ny, len, t0, 0.004f)
                val x0 = bladePts[0]; val y0 = bladePts[1]
                bladeAt(bx, by, dx, dy, nx, ny, len, t1 * 0.97f, 0.004f)
                p.detail(x0, y0, bladePts[0], bladePts[1], 0.016f, 0xFFB8C4D8.toInt())
                bladeAt(bx, by, dx, dy, nx, ny, len, t0, 0.014f)
                val e0x = bladePts[0]; val e0y = bladePts[1]
                bladeAt(bx, by, dx, dy, nx, ny, len, t1 * 0.97f, 0.013f)
                p.detail(e0x, e0y, bladePts[0], bladePts[1], 0.006f, 0xFFFFFFFF.toInt())
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

    /** A line of cracked hide: molten and glowing outside Hell, a dark sculpted groove in it. */
    private fun crack(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, lava: Boolean, glow: Int, hot: Int, groove: Int) {
        if (p.ink) return
        // A kink a third of the way along, so it splits like rock rather than runs like a seam.
        val dx = x2 - x1
        val dy = y2 - y1
        val mx = x1 + dx * 0.4f - dy * 0.22f
        val my = y1 + dy * 0.4f + dx * 0.22f
        if (lava) {
            emissive(x1, y1, mx, my, w, glow, hot)
            emissive(mx, my, x2, y2, w * 0.8f, glow, hot)
        } else if (p.shading) {
            p.detail(x1, y1, mx, my, w, groove)
            p.detail(mx, my, x2, y2, w * 0.8f, groove)
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
        val main = if (hell) 0xFFAE121C.toInt() else 0xFF2E262C.toInt()
        val lit = if (hell) 0xFFFF6A50.toInt() else 0xFF6A5A62.toInt()
        val dark = if (hell) 0xFF60060E.toInt() else 0xFF100C10.toInt()
        val glow = if (hell) 0xFFFFC030.toInt() else 0xFFFF6A10.toInt()
        val hot = 0xFFFFF0B0.toInt()
        val groove = Col.lerp(main, dark, 0.7f)
        val bone = 0xFFE8D8C0.toInt()
        val lava = !hell
        val L = look
        L.torso = main; L.torsoLit = lit; L.legs = main; L.legsFar = Col.lerp(main, dark, 0.55f)
        L.arms = main; L.armsFar = Col.lerp(main, dark, 0.55f); L.boots = dark; L.gloves = main; L.skin = main
        L.rim = Col.alpha(Col.lerp(pal.neon2, 0xFFFFFFFF.toInt(), 0.3f), 0.6f); L.legW = 1.05f; L.armW = 1.08f
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
        p.lightFrom(dir)
        p.twoPass {
            for (i in 0 until 5) {
                p.bone(tailX[i], tailY[i], tailX[i + 1], tailY[i + 1], 0.085f - i * 0.012f, 0.073f - i * 0.012f, L.legsFar)
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
            if (lava && armed) emissive(ex + ux * 0.02f, ey + uy * 0.02f, ex + ux * 0.11f, ey + uy * 0.11f, 0.014f, glow, hot)
            // Far horn, far arm and leg.
            demonHorn(hx - r * 0.1f * d, hy + r * 0.12f, r * 0.8f, -d, Col.mul(bone, 0.62f), false)
            body.arm(k.armB, L, far = true, hand = false)
            claws(k.armB, dir, Col.mul(bone, 0.75f))
            hoofLeg(k.legB, L.legsFar, dark)
            hoofLeg(k.legF, L.legs, dark)
            if (!p.ink && armed) {
                val l = k.legF
                crack(mixf(l.ax, l.jx, 0.25f), mixf(l.ay, l.jy, 0.25f), mixf(l.ax, l.jx, 0.75f) + 0.02f * d, mixf(l.ay, l.jy, 0.75f), 0.018f, lava, glow, hot, groove)
            }
            // Spines down the back, growing toward the shoulders.
            for (i in 0 until 3) {
                val a = 0.38f + i * 0.24f
                val sz = 0.07f + i * 0.025f
                val bx = body.ptX(a, -c * 0.46f)
                val by = body.ptY(a, -c * 0.46f)
                p.begin()
                    .add(bx + k.ux * sz * 0.55f, by + k.uy * sz * 0.55f)
                    .add(bx - k.nx * sz * 1.3f + k.ux * sz * 0.2f, by - k.ny * sz * 1.3f + k.uy * sz * 0.2f)
                    .add(bx - k.ux * sz * 0.55f, by - k.uy * sz * 0.55f)
                    .shape(bone)
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
            if (armed) {
                // Molten seams through the hide: one main fault and a branch.
                crack(body.ptX(0.18f, w * 0.05f), body.ptY(0.18f, w * 0.05f), body.ptX(0.42f, c * 0.22f), body.ptY(0.42f, c * 0.22f), 0.022f, lava, glow, hot, groove)
                crack(body.ptX(0.42f, c * 0.22f), body.ptY(0.42f, c * 0.22f), body.ptX(0.7f, c * 0.12f), body.ptY(0.7f, c * 0.12f), 0.02f, lava, glow, hot, groove)
                crack(body.ptX(0.7f, c * 0.12f), body.ptY(0.7f, c * 0.12f), body.ptX(0.86f, c * 0.36f), body.ptY(0.86f, c * 0.36f), 0.016f, lava, glow, hot, groove)
                crack(body.ptX(0.42f, c * 0.22f), body.ptY(0.42f, c * 0.22f), body.ptX(0.56f, -c * 0.22f), body.ptY(0.56f, -c * 0.22f), 0.014f, lava, glow, hot, groove)
            }
            // The hump of the shoulders the head juts forward from.
            p.ball(body.ptX(0.98f, -c * 0.18f), body.ptY(0.98f, -c * 0.18f), c * 0.5f, main)
            if (p.shading) p.detail(body.ptX(1.12f, -c * 0.5f), body.ptY(1.12f, -c * 0.5f), body.ptX(1.2f, -c * 0.02f), body.ptY(1.2f, -c * 0.02f), 0.03f, lit)
            p.seg(k.neckX, k.neckY + 0.04f, mixf(k.neckX, hx, 0.7f), mixf(k.neckY, hy, 0.7f), 0.13f * k.hs, main)
            demonHead(e, hx, hy, r, d, main, lit, dark, glow, hot, bone)
        }
        eyesAt(hx + r * 0.78f * d, hy - r * 0.26f)
        p.twoPass {
            p.lightFrom(dir)
            body.arm(k.armF, L, far = false, hand = false)
            if (!p.ink && armed) {
                val l = k.armF
                crack(mixf(l.jx, l.ex, 0.2f), mixf(l.jy, l.ey, 0.2f), mixf(l.jx, l.ex, 0.7f), mixf(l.jy, l.ey, 0.7f), 0.014f, lava, glow, hot, groove)
            }
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
    private fun demonHorn(x: Float, y: Float, r: Float, d: Float, color: Int, rings: Boolean) {
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
                val ou = -tv / tl * hornW[i] * sgn
                val ov = tu / tl * hornW[i] * sgn
                if (side == 1 && i == n - 1) continue
                p.add(x + (hornU[i] + ou) * r * d, y + (hornV[i] + ov) * r)
            }
        }
        p.shape(color)
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
                0.014f, 0xFFFFF6E8.toInt(),
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
        p.detail(x + hornU[4] * r * d, y + hornV[4] * r, x + hornU[5] * r * d, y + hornV[5] * r, 0.03f, Col.mul(color, 0.5f))
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
                emissive(hx + r * 0.52f * d, hy - r * 0.34f, hx + r * 0.94f * d, hy - r * 0.2f, 0.03f, glow, hot)
            } else {
                p.detail(hx + r * 0.52f * d, hy - r * 0.34f, hx + r * 0.94f * d, hy - r * 0.2f, 0.024f, dark)
            }
            // A swept-back pointed ear.
            p.begin()
                .add(hx - r * 0.1f * d, hy - r * 0.22f)
                .add(hx - r * 0.95f * d, hy - r * 0.62f)
                .add(hx - r * 0.25f * d, hy + r * 0.12f)
                .shapeDetail(if (p.shading) ActorPaint.shade(main) else main)
        }
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

    /** Digitigrade leg: a heavy haunch, knee forward, hock back with a spur, long foot to a cloven hoof. */
    private fun hoofLeg(l: Limb, color: Int, dark: Int) {
        val d = k.dir
        val toeX = l.ex + 0.06f * d * k.hs
        val toeY = l.ey + 0.04f
        val hockX = l.ex - 0.1f * d * k.hs
        val hockY = l.ey - 0.2f * k.hs
        k.ik(l, hockX, hockY, true)
        val lw = k.limbW * 1.05f
        val sep = l === k.legF
        p.bone(l.ax, l.ay, l.jx, l.jy, lw * 1.55f, lw * 0.9f, color, sep, bulge = lw * 1.7f, lit = sep)
        p.bone(l.jx, l.jy, l.ex, l.ey, lw * 0.9f, lw * 0.55f, color, sep, lit = sep)
        p.bone(l.ex, l.ey, toeX, toeY, lw * 0.6f, lw * 0.45f, color, sep, lit = sep)
        // The hock spur.
        p.begin()
            .add(l.ex + 0.02f * d, l.ey - 0.03f)
            .add(l.ex - 0.1f * d * k.hs, l.ey + 0.02f)
            .add(l.ex + 0.01f * d, l.ey + 0.04f)
            .shape(if (sep) 0xFFD8C8B0.toInt() else 0xFFA89880.toInt())
        // Cloven hoof: glossy black keratin, the split, a glint.
        p.begin()
            .add(toeX - 0.055f * d, toeY - 0.035f)
            .add(toeX + 0.035f * d, toeY - 0.045f)
            .add(toeX + 0.105f * d, toeY + 0.035f)
            .add(toeX - 0.065f * d, toeY + 0.035f)
            .shape(dark, sep)
        if (p.shading) {
            p.detail(toeX + 0.045f * d, toeY - 0.02f, toeX + 0.06f * d, toeY + 0.03f, 0.012f, 0xFF000000.toInt())
            p.detail(toeX - 0.03f * d, toeY - 0.025f, toeX + 0.03f * d, toeY - 0.032f, 0.012f, Col.alpha(0xFFFFFFFF.toInt(), 0.45f))
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
        val body = 0xFF1E2230.toInt()
        val hull = 0xFF3A4462.toInt()
        val lit = 0xFF7282A6.toInt()
        val alarmed = e.state == EnemyState.AIM || e.state == EnemyState.ALERT
        val eyeC = if (alarmed) 0xFFFF2A40.toInt() else pal.neon2
        if (dead) {
            p.flat = 0xFF000000.toInt(); p.flatAmt = min(0.5f, e.stateTime)
        }
        p.twoPass {
            // Arm bar and motor pods.
            p.seg(-0.38f, -0.1f, 0.38f, -0.1f, 0.06f, body)
            p.seg(-0.38f, -0.19f, -0.38f, -0.08f, 0.08f, body)
            p.seg(0.38f, -0.19f, 0.38f, -0.08f, 0.08f, body)
            // Hull: teardrop pod with a nose toward the facing side.
            p.begin()
                .add(-0.28f * dir, -0.11f).add(0.18f * dir, -0.13f).add(0.3f * dir, 0.0f)
                .add(0.2f * dir, 0.13f).add(-0.22f * dir, 0.13f).add(-0.32f * dir, 0.01f)
                .shape(hull)
            // Gun pod: muzzle at exactly the shot height.
            p.seg(0.05f * dir, 0.1f / DRONE_S, 0.54f * dir / DRONE_S, 0.1f / DRONE_S, 0.055f, 0xFF0A0A10.toInt())
        }
        if (p.shading) {
            // The hull's belly in shadow, a panel seam, and a gloss streak over the top.
            p.begin()
                .add(-0.31f * dir, 0.03f).add(0.28f * dir, 0.03f).add(0.2f * dir, 0.13f).add(-0.22f * dir, 0.13f)
                .shapeShade(hull)
            p.detail(-0.04f * dir, -0.11f, -0.06f * dir, 0.12f, 0.01f, ActorPaint.shade(hull))
            g.blend(Gfx.Blend.ADD)
            g.line(-0.12f * dir, -0.075f, 0.12f * dir, -0.085f, 0.014f, p.c(0x80FFFFFF.toInt()))
            g.blend(Gfx.Blend.NORMAL)
            p.dot(-0.38f, -0.1f, 0.022f, lit)
            p.dot(0.38f, -0.1f, 0.022f, lit)
        }
        if (!p.ink) {
            g.line(-0.2f * dir, -0.095f, 0.16f * dir, -0.105f, 0.035f, p.c(lit))
            g.fillRect(-0.26f, 0.045f, 0.2f, 0.075f, p.c(pal.enemyAccent))
            // Rotor blur: one translucent blade stroke per motor.
            for (si in 0..1) {
                val rx = (si * 2 - 1) * 0.38f
                val b = if (dead) 0.2f else 0.55f + 0.45f * abs(sin(f.t * 50f + si))
                g.line(rx - 0.25f * b, -0.21f, rx + 0.25f * b, -0.21f, 0.04f, p.c(0x90A8B0C8.toInt()))
            }
            if (!dead) {
                // The eye: a lit lens in a dark socket, glowing into the room.
                val ex = 0.14f * dir
                g.fillCircle(ex, 0.0f, 0.075f, p.c(0xFF0A0A10.toInt()))
                g.fillCircle(ex, 0.0f, 0.052f, p.c(eyeC))
                g.blend(Gfx.Blend.ADD)
                g.glow(ex, 0f, 0.3f, p.c(Col.alpha(eyeC, 0.7f)))
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
        val housing = 0xFF3A3E54.toInt()
        val lit = 0xFF626884.toInt()
        // Its own key light, never the one the last humanoid left in the pen.
        p.lightFrom(if (e.facing >= 0) 1 else -1)
        p.twoPass {
            p.seg(x, rt, x, y - 0.2f, 0.14f, 0xFF22222C.toInt())
            p.begin().add(x - 0.32f, rt).add(x + 0.32f, rt).add(x + 0.26f, rt + 0.1f).add(x - 0.26f, rt + 0.1f).shape(0xFF3A3A46.toInt())
        }
        g.save()
        g.translate(x, y)
        g.rotate(ang)
        p.twoPass {
            // Twin barrels as one fat stroke, and the breech.
            p.seg(0.12f, 0f, 0.56f, 0f, 0.13f, 0xFF15151C.toInt())
            p.seg(0.1f, 0f, 0.3f, 0f, 0.17f, 0xFF2A2A34.toInt())
        }
        g.restore()
        p.twoPass {
            p.begin().add(x - 0.32f, y - 0.26f).add(x + 0.32f, y - 0.26f).add(x + 0.28f, y - 0.08f).add(x - 0.28f, y - 0.08f).shape(lit)
            p.ball(x, y - 0.02f, 0.23f, housing, gloss = 0.3f)
        }
        bevel(x - 0.3f, y - 0.255f, x + 0.3f, y - 0.255f, x - 0.27f, y - 0.09f, x + 0.27f, y - 0.09f, lit)
        if (!dead) {
            val alarmed = e.state == EnemyState.AIM || e.state == EnemyState.ALERT
            val eyeC = if (alarmed) 0xFFFF2A40.toInt() else pal.neon2
            g.fillCircle(x, y, 0.1f, 0xFF08080C.toInt())
            g.fillCircle(x, y, 0.065f, eyeC)
            g.blend(Gfx.Blend.ADD)
            g.glow(x, y, 0.36f, Col.alpha(eyeC, 0.75f))
            g.blend(Gfx.Blend.NORMAL)
        } else if (hash((f.t * 12f).toInt(), e.id) > 0.8f) {
            f.glowDot(x + 0.1f, y + 0.1f, 0.04f, 0xFFFFE080.toInt())
        }
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
