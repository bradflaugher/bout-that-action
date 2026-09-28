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
     * A face in profile on the head pen: a lit cranium and one sculpted jaw-and-nose shape with a
     * square, jutting chin (a goon, not a hero). At 120 px a face is two tones; any more marks
     * read as scars.
     */
    private fun face(skin: Int) {
        p.ball(hpx(-0.06f), hpy(-0.04f), hcr, skin)
        p.begin()
        hp(-0.55f, 0.3f); hp(0.3f, -0.18f); hp(0.9f, -0.26f); hp(0.98f, -0.02f); hp(1.24f, 0.3f)
        hp(1.0f, 0.42f); hp(1.04f, 0.64f); hp(0.88f, 0.94f); hp(0.3f, 1.02f); hp(-0.22f, 0.8f)
        p.shapeLit(skin, hpx(0.7f), hpy(-0.3f), hpx(-0.2f), hpy(1.0f), mid = 0.5f)
        if (p.shading) {
            // The ear, and the jaw's underside turning away from the lamp.
            p.dot(hpx(-0.2f), hpy(0.14f), hcr * 0.2f, Col.lerp(skin, ActorPaint.shade(skin), 0.4f))
            p.begin()
            hp(-0.22f, 0.8f); hp(0.3f, 1.02f); hp(0.88f, 0.94f); hp(0.6f, 0.8f); hp(0.05f, 0.72f)
            p.shapeShade(skin)
        }
    }

    /** An eye and a heavy brow, for the heads that show one. */
    private fun eye(brow: Int) {
        if (p.ink || !p.hi) return
        p.detail(hpx(0.42f), hpy(-0.3f), hpx(0.94f), hpy(-0.26f), hcr * 0.18f, brow)
        p.dot(hpx(0.7f), hpy(-0.06f), hcr * 0.13f, 0xFF0C0A12.toInt())
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
                L.legW = 1.16f; L.armW = 1.14f
            }
            Zone.METRO -> {
                val navy = Col.lerp(main, 0xFF40598A.toInt(), 0.45f)
                L.torso = navy; L.torsoLit = Col.lerp(navy, 0xFFFFFFFF.toInt(), 0.3f); L.legs = Col.mul(navy, 0.8f); L.legsFar = Col.mul(navy, 0.56f)
                L.arms = navy; L.armsFar = Col.mul(navy, 0.6f); L.boots = 0xFF0A0A0E.toInt(); L.gloves = 0xFF141418.toInt(); L.skin = skin
                L.legW = 1.04f; L.armW = 1.04f
            }
            Zone.MINES -> {
                // Denim overalls over a rust work shirt, rolled to the elbow.
                L.torso = main; L.torsoLit = Col.lerp(main, 0xFFFFFFFF.toInt(), 0.3f); L.legs = main; L.legsFar = Col.mul(main, 0.6f)
                L.arms = 0xFF8A4A30.toInt(); L.armsFar = 0xFF55301F.toInt(); L.boots = 0xFF2E1C10.toInt(); L.gloves = 0xFF9A7446.toInt(); L.skin = skin
                L.legW = 1.06f
            }
            Zone.MAGMA -> {
                // Aluminised proximity suit: bulky, mirror-bright, charcoal gauntlets and boots.
                L.torso = Col.lerp(main, 0xFFDCD8D2.toInt(), 0.35f); L.torsoLit = 0xFFFFFCF4.toInt()
                L.legs = Col.mul(L.torso, 0.9f); L.legsFar = Col.mul(L.torso, 0.62f)
                L.arms = L.torso; L.armsFar = Col.mul(L.torso, 0.64f); L.boots = 0xFF26221E.toInt(); L.gloves = 0xFF3A322C.toInt(); L.skin = skin
                L.legW = 1.2f; L.armW = 1.18f
            }
            Zone.HELL -> {
                val robe = Col.lerp(main, 0xFF9A2030.toInt(), 0.45f)
                L.torso = robe; L.torsoLit = Col.lerp(robe, 0xFFFF6040.toInt(), 0.35f); L.legs = Col.mul(main, 0.6f); L.legsFar = Col.mul(main, 0.45f)
                L.arms = robe; L.armsFar = Col.mul(robe, 0.6f); L.boots = 0xFF140406.toInt(); L.gloves = 0xFFA8948E.toInt(); L.skin = 0xFFA8948E.toInt()
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
                    Zone.MAGMA -> band(k.legF, -0.02f, 0.1f, k.limbW * 1.1f, 0xFF3A342E.toInt())
                    else -> Unit
                }
                if (z == Zone.TOWER) body.hem(L.torso, 0.2f, 0.035f)
                body.torso(L, chest = when (z) { Zone.TOWER -> 1.08f; Zone.LABS, Zone.MAGMA -> 1.06f; Zone.METRO -> 1.04f; else -> 1f })
            } else {
                robeBody(L, acc)
            }
            guardDetails(z, dir, L, acc)
            body.neck(if (z == Zone.LABS || z == Zone.MAGMA || robe) L.torso else skin)
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
                    Zone.MAGMA -> band(k.armF, 0.78f, 0.16f, k.limbW * 0.86f, L.gloves)
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
                val tw = 0.17f * k.hs
                p.lightFrom(k.dir)
                p.bone(tx1, ty1, tx2, ty2, tw, tw, metal)
                // Valve cap, and a hose looping from the valve up into the hood.
                p.disc(tx2 + (tx2 - tx1) * 0.12f, ty2 + (ty2 - ty1) * 0.12f, 0.035f * k.hs, 0xFF2A2E34.toInt())
                val hx = k.headX - k.dir * k.headR * 0.9f
                val hy = k.headY + k.headR * 0.7f
                val mx = tx2 - k.dir * 0.1f * k.hs
                val my = (ty2 + hy) * 0.5f + 0.04f * k.hs
                p.seg(tx2, ty2 - 0.02f, mx, my, 0.03f * k.hs, 0xFF22262C.toInt())
                p.seg(mx, my, hx, hy, 0.03f * k.hs, 0xFF22262C.toInt())
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
        val gnd = min(k.ground, max(k.legF.ey, k.legB.ey) + 0.03f * k.hs)
        val fx = k.legF.ex
        val bx = k.legB.ex
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
        // Bell sleeve widening toward the wrist, the hand emerging from its shadow.
        p.bone(l.jx, l.jy, l.ex, l.ey, aw * 0.95f, aw * 1.4f, L.arms, true)
        if (!p.ink) p.dot(l.ex, l.ey, aw * 0.55f, 0xFF140204.toInt())
        body.handAt(l.ex + (l.ex - l.jx) * 0.25f, l.ey + (l.ey - l.jy) * 0.25f, L.skin)
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
                // A hazard patch on the chest in the zone accent.
                p.begin()
                tp(0.74f, c * 0.1f); tp(0.74f, c * 0.34f); tp(0.6f, c * 0.36f); tp(0.6f, c * 0.12f)
                p.shapeGradDetail(ActorPaint.light(acc), acc, body.ptX(0.74f, c * 0.2f), body.ptY(0.74f, c * 0.2f), body.ptX(0.6f, c * 0.2f), body.ptY(0.6f, c * 0.2f))
            }
            Zone.METRO -> {
                // Duty belt with a brass buckle, a gold shield on the chest, a shoulder radio.
                p.detail(body.ptX(0.08f, -w * 0.56f), body.ptY(0.08f, -w * 0.56f), body.ptX(0.08f, w * 0.54f), body.ptY(0.08f, w * 0.54f), 0.07f * s, 0xFF0C0C10.toInt())
                p.detail(body.ptX(0.08f, w * 0.34f), body.ptY(0.08f, w * 0.34f), body.ptX(0.08f, w * 0.46f), body.ptY(0.08f, w * 0.46f), 0.05f * s, 0xFFE0B040.toInt())
                val gold = 0xFFFFD060.toInt()
                p.begin()
                tp(0.84f, c * 0.12f); tp(0.86f, c * 0.28f); tp(0.84f, c * 0.44f); tp(0.66f, c * 0.38f); tp(0.6f, c * 0.28f); tp(0.66f, c * 0.18f)
                p.shapeGradDetail(0xFFFFF0B0.toInt(), 0xFFB07818.toInt(), body.ptX(0.82f, c * 0.3f), body.ptY(0.82f, c * 0.3f), body.ptX(0.62f, c * 0.3f), body.ptY(0.62f, c * 0.3f))
                if (p.shading) p.dot(body.ptX(0.74f, c * 0.3f), body.ptY(0.74f, c * 0.3f), 0.018f * s, ActorPaint.shade(gold))
                // Epaulette, and the radio clipped at the collar with its stubby antenna.
                p.detail(body.ptX(1.0f, -c * 0.2f), body.ptY(1.0f, -c * 0.2f), body.ptX(0.98f, c * 0.14f), body.ptY(0.98f, c * 0.14f), 0.04f * s, Col.mul(L.torso, 0.6f))
                val rx = body.ptX(0.9f, c * 0.46f); val ry = body.ptY(0.9f, c * 0.46f)
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
                    p.detail(body.ptX(0.62f, -c * 0.1f), body.ptY(0.62f, -c * 0.1f), body.ptX(0.18f, -w * 0.1f), body.ptY(0.18f, -w * 0.1f), 0.03f * s, ActorPaint.shade(L.torso))
                }
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
        hp(-1.05f, 0.3f); hp(0.95f, 0.5f)
        tp(0.88f, c * 0.55f); tp(0.86f, -c * 0.6f)
        p.shapeLit(L.torso, hpx(0.3f), hpy(0f), body.ptX(0.86f, -c * 0.6f), body.ptY(0.86f, -c * 0.6f))
        if (p.shading) p.detail(body.ptX(0.87f, c * 0.5f), body.ptY(0.87f, c * 0.5f), body.ptX(0.85f, -c * 0.56f), body.ptY(0.85f, -c * 0.56f), 0.03f * k.hs, ActorPaint.shade(L.torso))
        p.ball(hpx(-0.06f), hpy(0.02f), hcr * 1.26f, L.torso, gloss = 0.35f)
        // The faceplate: a black-glass rounded window with a frame.
        p.begin()
        hp(0.05f, -0.62f); hp(0.7f, -0.74f); hp(1.12f, -0.52f); hp(1.3f, -0.05f); hp(1.2f, 0.42f); hp(0.8f, 0.58f); hp(0.18f, 0.5f); hp(-0.02f, -0.05f)
        p.shape(0xFF3A4848.toInt(), sep = true)
        p.begin()
        hp(0.14f, -0.52f); hp(0.7f, -0.62f); hp(1.04f, -0.44f); hp(1.19f, -0.04f); hp(1.1f, 0.34f); hp(0.78f, 0.47f); hp(0.24f, 0.4f); hp(0.08f, -0.05f)
        p.shapeGradDetail(Col.lerp(acc, 0xFF061412.toInt(), 0.55f), 0xFF040A0A.toInt(), hpx(0.6f), hpy(-0.6f), hpx(0.6f), hpy(0.45f))
        glint(0.3f, -0.36f, 0.74f, -0.48f, 0.12f, 0.75f)
        glint(0.98f, -0.3f, 1.08f, 0.0f, 0.07f, 0.4f)
        // Respirator: a canister under the chin, capped in the accent.
        p.lightFrom(hcd)
        p.bone(hpx(0.72f), hpy(0.6f), hpx(0.86f), hpy(0.92f), hcr * 0.46f, hcr * 0.5f, 0xFF3A4646.toInt(), sep = true)
        if (!p.ink) p.dot(hpx(0.87f), hpy(0.95f), hcr * 0.16f, acc)
        body.headRim(hpx(-0.06f), hpy(0.02f), hcr * 1.26f, look.rim)
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
        // Bandana: a cloth triangle over the nose, its point on the chest, knotted behind.
        val cloth = 0xFF8C3428.toInt()
        p.begin()
        hp(-0.6f, 0.28f); hp(0.55f, 0.22f); hp(1.29f, 0.3f); hp(1.18f, 0.76f); hp(0.72f, 1.36f); hp(0.1f, 1.1f); hp(-0.46f, 0.72f)
        p.shapeLit(cloth, hpx(1.0f), hpy(0.1f), hpx(0.1f), hpy(1.2f), sep = true)
        p.disc(hpx(-0.82f), hpy(0.4f), hcr * 0.2f, Col.mul(cloth, 0.8f))
        p.seg(hpx(-0.88f), hpy(0.46f), hpx(-1.3f), hpy(0.9f), hcr * 0.18f, Col.mul(cloth, 0.72f))
        if (p.shading) {
            // One fold from the nose bridge down to the point.
            p.detail(hpx(0.95f), hpy(0.44f), hpx(0.64f), hpy(1.18f), hcr * 0.1f, ActorPaint.shade(cloth))
            p.detail(hpx(0.55f), hpy(0.27f), hpx(1.22f), hpy(0.34f), hcr * 0.08f, ActorPaint.light(cloth))
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
        hp(-0.4f, -1.22f); hp(0.55f, -1.2f); hp(1.12f, -0.8f); hp(1.28f, 0.0f); hp(1.22f, 0.9f)
        tp(0.8f, c * 0.6f); tp(0.78f, -c * 0.62f)
        hp(-1.25f, 0.2f); hp(-1.18f, -0.62f)
        p.shapeLit(L.torso, hpx(0.4f), hpy(-1.2f), body.ptX(0.8f, -c * 0.6f), body.ptY(0.8f, -c * 0.6f), mid = 0.35f)
        if (p.shading) {
            // The quilted skirt: one seam where the hood meets the cape, a mirror streak on the crown.
            p.detail(hpx(-1.2f), hpy(0.55f), hpx(1.2f), hpy(1.0f), hcr * 0.1f, ActorPaint.shade(L.torso))
            g.blend(Gfx.Blend.ADD)
            p.detail(hpx(-0.7f), hpy(-0.85f), hpx(0.3f), hpy(-1.08f), hcr * 0.14f, Col.alpha(0xFFFFFFFF.toInt(), 0.5f * (1f - p.flatAmt)))
            g.blend(Gfx.Blend.NORMAL)
        }
        // The visor: a framed pane of gold glass.
        p.begin()
        hp(-0.05f, -0.78f); hp(0.95f, -0.74f); hp(1.24f, -0.3f); hp(1.24f, 0.46f); hp(1.0f, 0.64f); hp(0.06f, 0.6f)
        p.shape(0xFF3A322C.toInt(), sep = true)
        p.begin()
        hp(0.05f, -0.66f); hp(0.9f, -0.63f); hp(1.13f, -0.26f); hp(1.13f, 0.4f); hp(0.95f, 0.52f); hp(0.14f, 0.5f)
        p.shapeGradDetail(0xFFFFE890.toInt(), 0xFFA04E06.toInt(), hpx(0.6f), hpy(-0.66f), hpx(0.6f), hpy(0.5f))
        if (p.shading) {
            // The lava below, caught in the bottom of the visor.
            p.begin()
            hp(0.14f, 0.22f); hp(1.13f, 0.16f); hp(1.13f, 0.4f); hp(0.95f, 0.52f); hp(0.14f, 0.5f)
            p.shapeDetail(Col.alpha(0xFFFF6A10.toInt(), 0.55f))
        }
        glint(0.3f, -0.5f, 0.52f, 0.36f, 0.14f, 0.7f)
        glint(0.72f, -0.5f, 0.82f, -0.2f, 0.07f, 0.5f)
        body.headRim(hcx, hcy, hcr * 1.22f, look.rim)
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

    private fun heavy(e: Enemy, dir: Int, zone: Zone, pal: Palette, armed: Boolean) {
        val lightZone = zone == Zone.LABS || zone == Zone.MAGMA
        val base = if (lightZone) Col.lerp(pal.enemyMain, 0xFF5A6264.toInt(), 0.45f) else Col.lerp(pal.enemyMain, 0xFF505A74.toInt(), 0.62f)
        val plate = Col.lerp(base, 0xFF9AA2B2.toInt(), 0.38f)
        val dark = Col.mul(base, 0.62f)
        val acc = pal.enemyAccent
        val L = look
        L.torso = base; L.torsoLit = Col.lerp(plate, 0xFFFFFFFF.toInt(), 0.3f); L.legs = base; L.legsFar = dark
        L.arms = base; L.armsFar = dark; L.boots = 0xFF0A0A0E.toInt(); L.gloves = 0xFF14141A.toInt(); L.skin = plate
        L.rim = rimOf(pal, 0.55f); L.legW = 1.1f; L.armW = 1.0f
        val hx = k.headX
        val hy = k.headY
        val r = k.headR
        p.twoPass {
            body.arm(k.armB, L, far = true)
            body.leg(k.legB, L, far = true)
            // Ammo pack on the back.
            val c = k.chestD
            p.begin()
                .add(body.ptX(0.38f, -c * 0.45f), body.ptY(0.38f, -c * 0.45f))
                .add(body.ptX(0.98f, -c * 0.4f), body.ptY(0.98f, -c * 0.4f))
                .add(body.ptX(0.95f, -c * 0.82f), body.ptY(0.95f, -c * 0.82f))
                .add(body.ptX(0.4f, -c * 0.8f), body.ptY(0.4f, -c * 0.8f))
                .shape(dark)
            if (!p.ink) p.detail(body.ptX(0.5f, -c * 0.8f), body.ptY(0.5f, -c * 0.8f), body.ptX(0.88f, -c * 0.8f), body.ptY(0.88f, -c * 0.8f), 0.025f, pal.enemyAccent)
            bevel(body.ptX(0.97f, -c * 0.42f), body.ptY(0.97f, -c * 0.42f), body.ptX(0.95f, -c * 0.78f), body.ptY(0.95f, -c * 0.78f),
                body.ptX(0.41f, -c * 0.47f), body.ptY(0.41f, -c * 0.47f), body.ptX(0.42f, -c * 0.78f), body.ptY(0.42f, -c * 0.78f), dark)
            body.leg(k.legF, L, far = false)
            body.torso(L)
            // Chest plate and belly armour.
            p.begin()
                .add(body.ptX(0.95f, -k.chestD * 0.1f), body.ptY(0.95f, -k.chestD * 0.1f))
                .add(body.ptX(0.92f, k.chestD * 0.42f), body.ptY(0.92f, k.chestD * 0.42f))
                .add(body.ptX(0.55f, k.chestD * 0.56f), body.ptY(0.55f, k.chestD * 0.56f))
                .add(body.ptX(0.45f, k.waistD * 0.1f), body.ptY(0.45f, k.waistD * 0.1f))
                .shape(plate)
            if (p.shading) {
                // The plate's lower half turned away from the lamp, a bevelled top edge.
                p.begin()
                    .add(body.ptX(0.72f, k.chestD * 0.5f), body.ptY(0.72f, k.chestD * 0.5f))
                    .add(body.ptX(0.55f, k.chestD * 0.56f), body.ptY(0.55f, k.chestD * 0.56f))
                    .add(body.ptX(0.45f, k.waistD * 0.1f), body.ptY(0.45f, k.waistD * 0.1f))
                    .add(body.ptX(0.66f, k.chestD * 0.02f), body.ptY(0.66f, k.chestD * 0.02f))
                    .shapeShade(plate)
                bevel(body.ptX(0.94f, -k.chestD * 0.06f), body.ptY(0.94f, -k.chestD * 0.06f), body.ptX(0.92f, k.chestD * 0.38f), body.ptY(0.92f, k.chestD * 0.38f),
                    body.ptX(0.5f, k.chestD * 0.5f), body.ptY(0.5f, k.chestD * 0.5f), body.ptX(0.46f, k.waistD * 0.14f), body.ptY(0.46f, k.waistD * 0.14f), plate)
                // Rivets.
                for (i in 0 until 3) {
                    val a = 0.88f - i * 0.12f
                    p.dot(body.ptX(a, k.chestD * 0.44f - i * 0.01f), body.ptY(a, k.chestD * 0.44f - i * 0.01f), 0.011f, ActorPaint.light(ActorPaint.light(plate)))
                }
            }
            if (!p.ink) {
                p.detail(body.ptX(0.7f, k.chestD * 0.1f), body.ptY(0.7f, k.chestD * 0.1f), body.ptX(0.7f, k.chestD * 0.5f), body.ptY(0.7f, k.chestD * 0.5f), 0.04f, acc)
            }
            // Knee plate.
            p.ball(k.legF.jx + 0.02f * dir, k.legF.jy, 0.075f, plate, gloss = 0.25f)
            body.neck(dark)
            // Helmet: dome, chin guard, glowing visor slit.
            p.ball(hx, hy, r * 1.22f, plate, gloss = 0.35f)
            p.begin()
                .add(hx - r * 0.6f * dir, hy + r * 0.2f).add(hx + r * 1.2f * dir, hy + r * 0.1f)
                .add(hx + r * 1.0f * dir, hy + r * 1.05f).add(hx - r * 0.5f * dir, hy + r * 1.0f)
                .shape(dark)
            if (p.shading) {
                // Breathing vents on the chin guard.
                for (i in 0 until 3) {
                    val vx = hx + r * (0.35f + i * 0.2f) * dir
                    p.detail(vx, hy + r * 0.45f, vx - r * 0.05f * dir, hy + r * 0.85f, 0.014f, ActorPaint.shade(dark))
                }
            }
            if (zone == Zone.HELL) {
                p.begin().add(hx - r * 0.2f * dir, hy - r * 1.0f).add(hx + r * 0.3f * dir, hy - r * 1.1f).add(hx - r * 0.5f * dir, hy - r * 2.1f).shape(0xFFE8D8C0.toInt())
                p.begin().add(hx - r * 0.9f * dir, hy - r * 0.7f).add(hx - r * 0.5f * dir, hy - r * 1.0f).add(hx - r * 1.6f * dir, hy - r * 1.7f).shape(0xFFC8B8A0.toInt())
            }
            if (!p.ink) {
                // The visor slit: a dark band with a hot accent core, the heavy's face.
                p.detail(hx - r * 0.1f * dir, hy - r * 0.12f, hx + r * 1.14f * dir, hy - r * 0.18f, 0.06f, 0xFF050508.toInt())
                p.detail(hx + r * 0.1f * dir, hy - r * 0.15f, hx + r * 1.08f * dir, hy - r * 0.2f, 0.032f, acc)
                p.detail(hx - r * 0.8f * dir, hy - r * 0.75f, hx + r * 0.3f * dir, hy - r * 1.1f, 0.03f, L.torsoLit)
                if (zone == Zone.MINES && e.alive) f.glowDot(hx + r * 0.9f * dir, hy - r * 0.8f, 0.035f, 0xFFFFF4C0.toInt(), p.alphaMul)
            }
        }
        body.headRim(hx, hy, r * 1.22f, look.rim)
        eyesAt(hx + r * 0.7f * dir, hy - r * 0.16f)
        p.twoPass {
            if (armed && showGun) body.gun(3, gunX, gunY, gunUp, acc, spin = if (e.state == EnemyState.AIM) f.t * 40f else 0f, scale = 0.85f)
            body.arm(k.armF, L, far = false)
            // Pauldron over the near shoulder.
            val sx = k.armF.ax
            val sy = k.armF.ay
            p.begin()
                .add(sx - 0.16f * dir, sy - 0.02f).add(sx - 0.05f * dir, sy - 0.14f)
                .add(sx + 0.14f * dir, sy - 0.1f).add(sx + 0.18f * dir, sy + 0.06f)
                .add(sx - 0.1f * dir, sy + 0.1f)
                .shape(plate, sep = true)
            if (p.shading) {
                p.begin()
                    .add(sx - 0.16f * dir, sy - 0.02f).add(sx + 0.18f * dir, sy + 0.06f)
                    .add(sx - 0.1f * dir, sy + 0.1f)
                    .shapeShade(plate)
                p.detail(sx - 0.13f * dir, sy + 0.01f, sx + 0.16f * dir, sy + 0.04f, 0.014f, ActorPaint.shade(ActorPaint.shade(plate)))
            }
            if (!p.ink) p.detail(sx - 0.08f * dir, sy - 0.1f, sx + 0.12f * dir, sy - 0.08f, 0.025f, L.torsoLit)
        }
        if (e.state == EnemyState.AIM && armed) f.glowDot(body.muzzleX, body.muzzleY, 0.04f, pal.laser, p.alphaMul)
    }

    private val tailX = FloatArray(6)
    private val tailY = FloatArray(6)

    private fun ninja(e: Enemy, dir: Int, zone: Zone, pal: Palette, armed: Boolean) {
        val main = 0xFF1C1C2C.toInt()
        val lit = 0xFF4A4A66.toInt()
        val band = if (zone == Zone.HELL) 0xFFFFB020.toInt() else pal.enemyAccent
        val L = look
        L.torso = main; L.torsoLit = lit; L.legs = main; L.legsFar = 0xFF07070B.toInt()
        L.arms = main; L.armsFar = 0xFF07070B.toInt(); L.boots = 0xFF1E1E28.toInt(); L.gloves = 0xFF1E1E28.toInt(); L.skin = 0xFFE8C8A8.toInt()
        L.rim = rimOf(pal, 0.65f); L.legW = 0.95f; L.armW = 0.95f
        val hx = k.headX
        val hy = k.headY
        val r = k.headR
        // Headband tails streaming behind.
        val speed = min(1f, abs(e.vx) / 3f)
        var x = hx - r * 0.9f * dir
        var y = hy - r * 0.45f
        tailX[0] = x; tailY[0] = y
        for (i in 1..3) {
            val a = Rig.mix(0.5f, 1.45f, speed) + sin(f.t * 12f - i * 1.2f + e.id) * (0.12f + 0.08f * i)
            x += sin(a) * 0.16f * -dir
            y += cos(a) * 0.16f
            tailX[i] = x; tailY[i] = y
        }
        p.twoPass {
            for (i in 0 until 3) p.seg(tailX[i], tailY[i], tailX[i + 1], tailY[i + 1], 0.055f - i * 0.01f, band)
            body.arm(k.armB, L, far = true)
            body.leg(k.legB, L, far = true)
            // Sheath across the back.
            p.seg(body.ptX(0.2f, -k.waistD * 0.7f), body.ptY(0.2f, -k.waistD * 0.7f), body.ptX(1.05f, -k.chestD * 0.2f), body.ptY(1.05f, -k.chestD * 0.2f), 0.05f, 0xFF2A1A20.toInt())
            body.leg(k.legF, L, far = false)
            body.torso(L)
            if (!p.ink) {
                // Sash and shin wraps.
                p.detail(body.ptX(0.05f, -k.waistD * 0.52f), body.ptY(0.05f, -k.waistD * 0.52f), body.ptX(0.05f, k.waistD * 0.52f), body.ptY(0.05f, k.waistD * 0.52f), 0.07f, band)
                if (p.shading) {
                    // The sash knot, a crossed wrap over the chest, and bindings on the near shin.
                    p.dot(body.ptX(0.05f, k.waistD * 0.3f), body.ptY(0.05f, k.waistD * 0.3f), 0.04f, ActorPaint.light(band))
                    p.detail(body.ptX(0.98f, -k.chestD * 0.2f), body.ptY(0.98f, -k.chestD * 0.2f), body.ptX(0.2f, k.waistD * 0.45f), body.ptY(0.2f, k.waistD * 0.45f), 0.016f, lit)
                    wraps(k.legF, lit)
                }
            }
            body.neck(main)
            p.ball(hx, hy, r, main)
            p.ball(hx + r * 0.3f * dir, hy + r * 0.45f, r * 0.62f, main)
            if (!p.ink) {
                p.detail(hx - r * 0.95f * dir, hy - r * 0.48f, hx + r * 0.95f * dir, hy - r * 0.52f, 0.055f, band)
                if (p.shading) p.detail(hx - r * 0.9f * dir, hy - r * 0.58f, hx + r * 0.9f * dir, hy - r * 0.62f, 0.014f, ActorPaint.light(band))
                p.begin()
                    .add(hx + r * 0.15f * dir, hy - r * 0.2f).add(hx + r * 1.05f * dir, hy - r * 0.22f)
                    .add(hx + r * 1.0f * dir, hy + r * 0.2f).add(hx + r * 0.2f * dir, hy + r * 0.18f)
                    .shapeDetail(L.skin)
                p.dot(hx + r * 0.7f * dir, hy - r * 0.02f, 0.024f, 0xFF101010.toInt())
                p.detail(hx - r * 0.6f * dir, hy - r * 0.8f, hx + r * 0.3f * dir, hy - r * 0.95f, 0.025f, lit)
            }
        }
        body.headRim(hx, hy, r * 1.0f, look.rim)
        eyesAt(hx + r * 0.7f * dir, hy - r * 0.02f)
        p.twoPass {
            if (armed && bladeA < 90f) katana(k.armF.ex, k.armF.ey, dir, bladeA, band)
            body.arm(k.armF, L, far = false)
            if (p.shading) wraps(k.armF, lit)
        }
    }

    /** Cloth bindings crossing the lower half of a limb (fill pass). */
    private fun wraps(l: Limb, color: Int) {
        val dx = l.ex - l.jx
        val dy = l.ey - l.jy
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-4f)
        val nx = -dy / len * 0.045f
        val ny = dx / len * 0.045f
        for (i in 0 until 3) {
            val t = 0.4f + i * 0.17f
            val cx = l.jx + dx * t
            val cy = l.jy + dy * t
            p.detail(cx - nx + dx * 0.05f, cy - ny + dy * 0.05f, cx + nx - dx * 0.05f, cy + ny - dy * 0.05f, 0.012f, color)
        }
    }

    /** Katana from the hand; [a] = blade angle from horizontal-forward (radians, + = up). */
    private fun katana(hx: Float, hy: Float, dir: Int, a: Float, wrap: Int) {
        val c = cos(a)
        val s = sin(a)
        val len = 0.66f
        val bx = hx + c * len * dir
        val by = hy - s * len
        p.seg(hx - c * 0.15f * dir, hy + s * 0.15f, hx, hy, 0.045f, wrap)
        // The guard: a small gold tsuba where the blade leaves the grip.
        val gx = hx + c * 0.03f * dir
        val gy = hy - s * 0.03f
        p.seg(gx + s * 0.04f * dir, gy + c * 0.04f, gx - s * 0.04f * dir, gy - c * 0.04f, 0.022f, 0xFFC8A040.toInt())
        p.seg(hx + c * 0.04f * dir, hy - s * 0.04f, bx, by, 0.038f, 0xFF8A94A8.toInt())
        if (!p.ink) {
            // Steel: a darker spine, a bright edge, the tip catching the lamp.
            p.detail(hx + c * 0.05f * dir + s * 0.008f * dir, hy - s * 0.05f + c * 0.008f, bx, by, 0.018f, 0xFFD8E0F0.toInt())
            p.detail(hx + c * 0.05f * dir, hy - s * 0.05f, bx - c * 0.02f * dir, by + s * 0.02f, 0.006f, 0xFFFFFFFF.toInt())
            if (p.shading) {
                g.blend(Gfx.Blend.ADD)
                g.glow(bx, by, 0.06f, p.c(0x60FFFFFF))
                g.blend(Gfx.Blend.NORMAL)
                for (i in 0 until 3) {
                    val t = -0.11f + i * 0.04f
                    p.dot(hx + c * t * dir, hy - s * t, 0.012f, ActorPaint.light(wrap))
                }
            }
        }
    }

    private fun demon(e: Enemy, dir: Int, zone: Zone, pal: Palette) {
        val hell = zone == Zone.HELL
        val main = if (hell) 0xFFB0101E.toInt() else 0xFF2E2222.toInt()
        val lit = if (hell) 0xFFFF4A4A.toInt() else 0xFF604440.toInt()
        val dark = if (hell) 0xFF640612.toInt() else 0xFF0C0808.toInt()
        val glow = if (hell) 0xFFFFD040.toInt() else 0xFFFF6A10.toInt()
        val bone = 0xFFE8D8C0.toInt()
        val L = look
        L.torso = main; L.torsoLit = lit; L.legs = main; L.legsFar = dark
        L.arms = main; L.armsFar = dark; L.boots = dark; L.gloves = main; L.skin = main
        L.rim = Col.alpha(Col.lerp(pal.neon2, 0xFFFFFFFF.toInt(), 0.3f), 0.55f); L.legW = 1.05f; L.armW = 0.95f
        val hx = k.headX
        val hy = k.headY
        val r = k.headR
        val tw = sin(f.t * 5f + e.id) * 0.14f
        p.twoPass {
            // Tail with an arrowhead.
            val t0x = k.hipX - 0.1f * dir; val t0y = k.hipY
            val t1x = k.hipX - 0.42f * dir; val t1y = k.hipY + 0.18f + tw
            val t2x = k.hipX - 0.7f * dir; val t2y = k.hipY - 0.06f + tw * 1.4f
            p.seg(t0x, t0y, t1x, t1y, 0.075f, dark)
            p.seg(t1x, t1y, t2x, t2y, 0.05f, dark)
            p.begin().add(t2x - 0.03f * dir, t2y + 0.06f).add(t2x - 0.2f * dir, t2y - 0.04f).add(t2x + 0.02f * dir, t2y - 0.1f).shape(dark)
            body.arm(k.armB, L, far = true, hand = false)
            claws(k.armB, dir, bone)
            hoofLeg(k.legB, L.legsFar, dark)
            hoofLeg(k.legF, L.legs, dark)
            body.torso(L, chest = 1.15f)
            if (p.shading) {
                // Muscle: a pectoral shelf and the belly's ridges.
                p.detail(body.ptX(0.78f, k.chestD * 0.05f), body.ptY(0.78f, k.chestD * 0.05f), body.ptX(0.7f, k.chestD * 0.55f), body.ptY(0.7f, k.chestD * 0.55f), 0.02f, dark)
                p.detail(body.ptX(0.9f, k.chestD * 0.1f), body.ptY(0.9f, k.chestD * 0.1f), body.ptX(0.8f, k.chestD * 0.5f), body.ptY(0.8f, k.chestD * 0.5f), 0.02f, lit)
                for (i in 0 until 2) {
                    val a = 0.28f + i * 0.14f
                    p.detail(body.ptX(a, k.waistD * 0.18f), body.ptY(a, k.waistD * 0.18f), body.ptX(a, k.waistD * 0.46f), body.ptY(a, k.waistD * 0.46f), 0.014f, dark)
                }
            }
            if (!p.ink) {
                // Ribs / lava cracks.
                val crack = if (hell) Col.mul(main, 0.65f) else glow
                if (!hell && p.shading) {
                    // Lava in the cracks, glowing through the hide.
                    g.blend(Gfx.Blend.ADD)
                    g.line(body.ptX(0.2f, k.waistD * 0.1f), body.ptY(0.2f, k.waistD * 0.1f), body.ptX(0.75f, k.chestD * 0.3f), body.ptY(0.75f, k.chestD * 0.3f), 0.07f, p.c(Col.alpha(glow, 0.22f)))
                    g.blend(Gfx.Blend.NORMAL)
                }
                p.detail(body.ptX(0.2f, k.waistD * 0.1f), body.ptY(0.2f, k.waistD * 0.1f), body.ptX(0.75f, k.chestD * 0.3f), body.ptY(0.75f, k.chestD * 0.3f), 0.022f, crack)
                p.detail(body.ptX(0.55f, -k.waistD * 0.3f), body.ptY(0.55f, -k.waistD * 0.3f), body.ptX(0.45f, k.waistD * 0.35f), body.ptY(0.45f, k.waistD * 0.35f), 0.018f, crack)
                if (!hell && p.shading) p.detail(body.ptX(0.2f, k.waistD * 0.1f), body.ptY(0.2f, k.waistD * 0.1f), body.ptX(0.75f, k.chestD * 0.3f), body.ptY(0.75f, k.chestD * 0.3f), 0.008f, 0xFFFFE0A0.toInt())
                // Spine ridges.
                for (i in 0 until 2) {
                    val a = 0.55f + i * 0.25f
                    val sx = body.ptX(a, -k.chestD * 0.55f)
                    val sy = body.ptY(a, -k.chestD * 0.55f)
                    p.tri(sx, sy, sx - (0.06f * k.ux + 0.08f * k.nx), sy - (0.06f * k.uy + 0.08f * k.ny), sx + 0.08f * k.ux, sy + 0.08f * k.uy, p.c(dark))
                }
            }
            body.neck(main)
            // Horns sweeping back.
            p.begin()
                .add(hx - r * 0.1f * dir, hy - r * 0.7f).add(hx + r * 0.35f * dir, hy - r * 0.85f)
                .add(hx - r * 0.2f * dir, hy - r * 1.9f).add(hx - r * 1.1f * dir, hy - r * 2.6f)
                .add(hx - r * 0.6f * dir, hy - r * 1.7f)
                .shape(bone)
            p.begin()
                .add(hx - r * 0.7f * dir, hy - r * 0.55f).add(hx - r * 0.35f * dir, hy - r * 0.85f)
                .add(hx - r * 1.2f * dir, hy - r * 1.5f).add(hx - r * 1.9f * dir, hy - r * 1.6f)
                .shape(Col.mul(bone, 0.82f))
            if (p.shading) {
                // Growth rings on the near horn.
                for (i in 0 until 3) {
                    val t = 0.25f + i * 0.2f
                    val cx = Rig.mix(hx + r * 0.1f * dir, hx - r * 0.9f * dir, t)
                    val cy = Rig.mix(hy - r * 0.8f, hy - r * 2.3f, t)
                    p.detail(cx - r * 0.18f * dir, cy + r * 0.05f, cx + r * 0.2f * dir, cy - r * 0.04f, 0.01f, Col.mul(bone, 0.7f))
                }
            }
            p.ball(hx, hy, r * 1.02f, main)
            // Snout / jaw.
            p.begin()
                .add(hx + r * 0.2f * dir, hy - r * 0.2f).add(hx + r * 1.35f * dir, hy + r * 0.2f)
                .add(hx + r * 1.2f * dir, hy + r * 0.75f).add(hx + r * 0.1f * dir, hy + r * 0.9f)
                .shape(main)
            if (!p.ink) {
                val open = if (e.state == EnemyState.WINDUP || e.state == EnemyState.AIM) 0.25f else 0.08f
                p.begin()
                    .add(hx + r * 0.4f * dir, hy + r * 0.45f).add(hx + r * 1.28f * dir, hy + r * 0.42f)
                    .add(hx + r * 1.1f * dir, hy + r * (0.55f + open * 2f)).add(hx + r * 0.45f * dir, hy + r * (0.6f + open))
                    .shapeDetail(0xFF1A0204.toInt())
                for (i in 0 until 2) {
                    val tx = hx + r * (0.65f + i * 0.3f) * dir
                    p.tri(tx, hy + r * 0.45f, tx + r * 0.1f * dir, hy + r * 0.45f, tx + r * 0.05f * dir, hy + r * 0.62f, p.c(bone))
                }
                val eg = p.alphaMul * (1f - p.flatAmt)
                f.glowDot(hx + r * 0.65f * dir, hy - r * 0.15f, 0.028f, glow, eg)
                p.detail(hx + r * 0.3f * dir, hy - r * 0.45f, hx + r * 0.95f * dir, hy - r * 0.2f, 0.03f, dark)
                p.detail(hx - r * 0.6f * dir, hy - r * 0.8f, hx + r * 0.2f * dir, hy - r * 0.95f, 0.025f, lit)
                // Hellfire licking up the back of the skull.
                if (e.alive) flames(hx - r * 0.5f * dir, hy - r * 0.6f, dir, glow, e.id)
            }
        }
        body.headRim(hx, hy, r * 1.02f, look.rim)
        eyesAt(hx + r * 0.65f * dir, hy - r * 0.15f)
        p.twoPass {
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

    private fun claws(l: Limb, dir: Int, bone: Int) {
        val dx = l.ex - l.jx
        val dy = l.ey - l.jy
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
        val ux = dx / len
        val uy = dy / len
        p.disc(l.ex, l.ey, 0.055f * k.hs, look.arms)
        for (j in 0..1) {
            val i = j * 1.2f - 0.6f
            val sx = -uy * i * 0.05f
            val sy = ux * i * 0.05f
            p.seg(l.ex + sx * 0.5f, l.ey + sy * 0.5f, l.ex + ux * 0.13f + sx, l.ey + uy * 0.13f + sy, 0.022f, bone)
        }
    }

    /** Digitigrade leg: knee forward, hock back, long foot to a hoof. */
    private fun hoofLeg(l: Limb, color: Int, dark: Int) {
        val d = k.dir
        val toeX = l.ex + 0.06f * d * k.hs
        val toeY = l.ey + 0.04f
        val hockX = l.ex - 0.1f * d * k.hs
        val hockY = l.ey - 0.2f * k.hs
        k.ik(l, hockX, hockY, true)
        val lw = k.limbW * 1.05f
        val sep = l === k.legF
        p.bone(l.ax, l.ay, l.jx, l.jy, lw * 1.45f, lw * 0.9f, color, sep)
        p.bone(l.jx, l.jy, l.ex, l.ey, lw * 0.9f, lw * 0.6f, color, sep)
        p.bone(l.ex, l.ey, toeX, toeY, lw * 0.6f, lw * 0.55f, color, sep)
        p.seg(toeX - 0.02f * d, toeY, toeX + 0.07f * d, toeY + 0.005f, 0.07f, dark)
    }

    private fun flames(x: Float, y: Float, dir: Int, color: Int, seed: Int) {
        for (i in 0 until 3) {
            val ph = f.t * 9f + i * 2.1f + seed
            val h = 0.14f + 0.07f * sin(ph) + i * 0.02f
            val ox = (i - 1) * 0.05f - 0.03f * dir
            val sway = sin(ph * 1.3f) * 0.04f
            val c = if (i == 1) Col.lerp(color, 0xFFFFFFFF.toInt(), 0.4f) else color
            p.tri(x + ox - 0.04f, y, x + ox + 0.04f, y, x + ox + sway - 0.03f * dir, y - h, p.c(Col.alpha(c, 0.85f)))
        }
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
