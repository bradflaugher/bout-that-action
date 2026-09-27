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
        if (e.alive && humanoid && e.state != EnemyState.DEAD) {
            eyeX[idx] = eyeWX
            eyeY[idx] = eyeWY
            eyeOk[idx] = true
        }
        p.reset()
        if (e.alive) {
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
            }
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
                    p.detail(rx - 0.01f * dir, ry - 0.08f, rx - 0.02f * dir, ry - 0.2f, 0.012f, 0xFF15161C.toInt())
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
        val gnd = k.ground + 0.0f
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
                p.detail(body.ptX(0.96f, c * 0.2f), body.ptY(0.96f, c * 0.2f), body.ptX(0.66f, c * 0.42f), body.ptY(0.66f, c * 0.42f), 0.04f, acc)
                p.detail(body.ptX(0.98f, c * 0.0f), body.ptY(0.98f, c * 0.0f), body.ptX(0.5f, w * 0.5f), body.ptY(0.5f, w * 0.5f), 0.018f, Col.mul(L.torso, 0.6f))
            }
            Zone.LABS -> {
                p.detail(body.ptX(0.5f, -w * 0.5f), body.ptY(0.5f, -w * 0.5f), body.ptX(0.5f, w * 0.52f), body.ptY(0.5f, w * 0.52f), 0.05f, acc)
                p.detail(body.ptX(0.02f, -w * 0.5f), body.ptY(0.02f, -w * 0.5f), body.ptX(0.02f, w * 0.5f), body.ptY(0.02f, w * 0.5f), 0.05f, 0xFF3A4A48.toInt())
                p.detail(body.ptX(0.85f, c * 0.1f), body.ptY(0.85f, c * 0.1f), body.ptX(0.3f, w * 0.1f), body.ptY(0.3f, w * 0.1f), 0.012f, Col.mul(L.torso, 0.75f))
            }
            Zone.METRO -> {
                for (i in 0 until 2) {
                    val a = if (i == 0) 0.42f else 0.72f
                    p.detail(body.ptX(a, -w * 0.52f), body.ptY(a, -w * 0.52f), body.ptX(a, c * 0.5f), body.ptY(a, c * 0.5f), 0.055f, acc)
                    p.detail(body.ptX(a, -w * 0.48f), body.ptY(a, -w * 0.48f), body.ptX(a, c * 0.46f), body.ptY(a, c * 0.46f), 0.014f, 0xFFE8E8F0.toInt())
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
                // Quilted aluminised seams and a warning band.
                for (i in 0 until 3) {
                    val a = 0.3f + i * 0.25f
                    p.detail(body.ptX(a, -w * 0.48f), body.ptY(a, -w * 0.48f), body.ptX(a, c * 0.48f), body.ptY(a, c * 0.48f), 0.012f, Col.mul(L.torso, 0.72f))
                }
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
                p.disc(hx, hy, r, L.skin)
                p.disc(hx + r * 0.3f * dir, hy + r * 0.5f, r * 0.6f, L.skin)
                // Slicked-back hair.
                p.begin()
                    .add(hx - r * 1.05f * dir, hy + r * 0.2f).add(hx - r * 0.95f * dir, hy - r * 0.6f)
                    .add(hx - r * 0.2f * dir, hy - r * 1.12f).add(hx + r * 0.75f * dir, hy - r * 0.78f)
                    .add(hx + r * 0.35f * dir, hy - r * 0.5f).add(hx - r * 0.45f * dir, hy - r * 0.2f)
                    .shape(0xFF0A0A10.toInt())
                if (!p.ink) {
                    // Shades with a neon glint, earpiece coil.
                    p.begin()
                        .add(hx + r * 0.05f * dir, hy - r * 0.28f).add(hx + r * 1.1f * dir, hy - r * 0.3f)
                        .add(hx + r * 1.05f * dir, hy + r * 0.12f).add(hx + r * 0.25f * dir, hy + r * 0.1f)
                        .shapeDetail(0xFF050508.toInt())
                    p.detail(hx + r * 0.4f * dir, hy - r * 0.18f, hx + r * 0.85f * dir, hy - r * 0.2f, 0.014f, Col.alpha(pal.neon, 0.9f))
                    p.dot(hx - r * 0.3f * dir, hy + r * 0.05f, 0.022f, 0xFFB8B8C8.toInt())
                    p.detail(hx - r * 0.3f * dir, hy + r * 0.1f, k.neckX - 0.05f * dir, k.neckY + 0.04f, 0.01f, 0xB0B8B8C8.toInt())
                    p.detail(hx + r * 0.55f * dir, hy + r * 0.72f, hx + r * 0.85f * dir, hy + r * 0.65f, 0.012f, Col.mul(L.skin, 0.6f))
                }
                body.headRim(hx, hy, r * 1.0f, look.rim)
                eyesAt(hx + r * 0.65f * dir, hy - r * 0.1f)
            }
            Zone.LABS -> {
                // Hazmat hood with a dark faceplate and a respirator filter.
                p.disc(hx, hy + r * 0.05f, r * 1.2f, L.torso)
                p.begin()
                    .add(hx + r * 0.05f * dir, hy - r * 0.6f).add(hx + r * 1.12f * dir, hy - r * 0.45f)
                    .add(hx + r * 1.15f * dir, hy + r * 0.35f).add(hx + r * 0.1f * dir, hy + r * 0.3f)
                    .shape(0xFF0E1A1A.toInt())
                p.disc(hx + r * 0.95f * dir, hy + r * 0.72f, r * 0.42f, 0xFF3A4848.toInt())
                if (!p.ink) {
                    p.detail(hx + r * 0.3f * dir, hy - r * 0.4f, hx + r * 0.95f * dir, hy - r * 0.32f, 0.018f, Col.alpha(acc, 0.9f))
                    p.dot(hx + r * 0.95f * dir, hy + r * 0.72f, r * 0.2f, acc)
                    p.detail(hx - r * 0.7f * dir, hy - r * 0.8f, hx + r * 0.2f * dir, hy - r * 1.1f, 0.03f, 0x80FFFFFF.toInt())
                }
                body.headRim(hx, hy, r * 1.2f, look.rim)
                eyesAt(hx + r * 0.7f * dir, hy - r * 0.1f)
            }
            Zone.METRO -> {
                p.disc(hx, hy, r, L.skin)
                p.disc(hx + r * 0.3f * dir, hy + r * 0.5f, r * 0.6f, L.skin)
                // Peaked cap: crown, band, brim, badge.
                p.begin()
                    .add(hx - r * 1.05f * dir, hy - r * 0.35f).add(hx - r * 0.9f * dir, hy - r * 1.25f)
                    .add(hx + r * 0.95f * dir, hy - r * 1.35f).add(hx + r * 0.95f * dir, hy - r * 0.45f)
                    .shape(0xFF141C2C.toInt())
                p.seg(hx + r * 0.4f * dir, hy - r * 0.38f, hx + r * 1.45f * dir, hy - r * 0.25f, 0.04f, 0xFF08080C.toInt())
                if (!p.ink) {
                    p.detail(hx - r * 0.95f * dir, hy - r * 0.5f, hx + r * 0.92f * dir, hy - r * 0.55f, 0.035f, 0xFF0A0E16.toInt())
                    p.dot(hx + r * 0.55f * dir, hy - r * 0.9f, 0.03f, 0xFFFFD060.toInt())
                    p.dot(hx + r * 0.72f * dir, hy - r * 0.05f, 0.018f, 0xFF101018.toInt())
                    p.detail(hx + r * 0.55f * dir, hy + r * 0.38f, hx + r * 0.95f * dir, hy + r * 0.36f, 0.025f, 0xFF3A2A20.toInt())
                }
                body.headRim(hx, hy, r * 1.0f, look.rim)
                eyesAt(hx + r * 0.72f * dir, hy - r * 0.05f)
            }
            Zone.MINES -> {
                p.disc(hx, hy, r, L.skin)
                p.disc(hx + r * 0.3f * dir, hy + r * 0.5f, r * 0.6f, L.skin)
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
            body.leg(k.legF, L, far = false)
            body.torso(L)
            // Chest plate and belly armour.
            p.begin()
                .add(body.ptX(0.95f, -k.chestD * 0.1f), body.ptY(0.95f, -k.chestD * 0.1f))
                .add(body.ptX(0.92f, k.chestD * 0.42f), body.ptY(0.92f, k.chestD * 0.42f))
                .add(body.ptX(0.55f, k.chestD * 0.56f), body.ptY(0.55f, k.chestD * 0.56f))
                .add(body.ptX(0.45f, k.waistD * 0.1f), body.ptY(0.45f, k.waistD * 0.1f))
                .shape(plate)
            if (!p.ink) {
                p.detail(body.ptX(0.7f, k.chestD * 0.1f), body.ptY(0.7f, k.chestD * 0.1f), body.ptX(0.7f, k.chestD * 0.5f), body.ptY(0.7f, k.chestD * 0.5f), 0.04f, acc)
                p.detail(body.ptX(0.9f, k.chestD * 0.0f), body.ptY(0.9f, k.chestD * 0.0f), body.ptX(0.88f, k.chestD * 0.38f), body.ptY(0.88f, k.chestD * 0.38f), 0.025f, L.torsoLit)
                // Knee plate.
                p.dot(k.legF.jx + 0.02f * dir, k.legF.jy, 0.075f, plate)
                p.dot(k.legF.jx + 0.03f * dir, k.legF.jy - 0.02f, 0.03f, L.torsoLit)
            }
            body.neck(dark)
            // Helmet: dome, chin guard, glowing visor slit.
            p.disc(hx, hy, r * 1.22f, plate)
            p.begin()
                .add(hx - r * 0.6f * dir, hy + r * 0.2f).add(hx + r * 1.2f * dir, hy + r * 0.1f)
                .add(hx + r * 1.0f * dir, hy + r * 1.05f).add(hx - r * 0.5f * dir, hy + r * 1.0f)
                .shape(dark)
            if (zone == Zone.HELL) {
                p.begin().add(hx - r * 0.2f * dir, hy - r * 1.0f).add(hx + r * 0.3f * dir, hy - r * 1.1f).add(hx - r * 0.5f * dir, hy - r * 2.1f).shape(0xFFE8D8C0.toInt())
                p.begin().add(hx - r * 0.9f * dir, hy - r * 0.7f).add(hx - r * 0.5f * dir, hy - r * 1.0f).add(hx - r * 1.6f * dir, hy - r * 1.7f).shape(0xFFC8B8A0.toInt())
            }
            if (!p.ink) {
                p.detail(hx - r * 0.1f * dir, hy - r * 0.12f, hx + r * 1.12f * dir, hy - r * 0.18f, 0.045f, 0xFF050508.toInt())
                p.detail(hx + r * 0.15f * dir, hy - r * 0.15f, hx + r * 1.05f * dir, hy - r * 0.2f, 0.022f, acc)
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
        for (i in 1..5) {
            val a = Rig.mix(0.5f, 1.45f, speed) + sin(f.t * 12f - i * 0.9f + e.id) * (0.12f + 0.05f * i)
            x += sin(a) * 0.1f * -dir
            y += cos(a) * 0.1f
            tailX[i] = x; tailY[i] = y
        }
        p.twoPass {
            for (i in 0 until 5) p.seg(tailX[i], tailY[i], tailX[i + 1], tailY[i + 1], 0.045f - i * 0.005f, band)
            body.arm(k.armB, L, far = true)
            body.leg(k.legB, L, far = true)
            // Sheath across the back.
            p.seg(body.ptX(0.2f, -k.waistD * 0.7f), body.ptY(0.2f, -k.waistD * 0.7f), body.ptX(1.05f, -k.chestD * 0.2f), body.ptY(1.05f, -k.chestD * 0.2f), 0.05f, 0xFF2A1A20.toInt())
            body.leg(k.legF, L, far = false)
            body.torso(L)
            if (!p.ink) {
                // Sash and shin wraps.
                p.detail(body.ptX(0.05f, -k.waistD * 0.52f), body.ptY(0.05f, -k.waistD * 0.52f), body.ptX(0.05f, k.waistD * 0.52f), body.ptY(0.05f, k.waistD * 0.52f), 0.06f, band)
                p.detail(k.legF.jx + (k.legF.ex - k.legF.jx) * 0.55f - 0.04f, k.legF.jy + (k.legF.ey - k.legF.jy) * 0.55f, k.legF.jx + (k.legF.ex - k.legF.jx) * 0.55f + 0.04f, k.legF.jy + (k.legF.ey - k.legF.jy) * 0.55f + 0.02f, 0.035f, lit)
            }
            body.neck(main)
            p.disc(hx, hy, r, main)
            p.disc(hx + r * 0.3f * dir, hy + r * 0.45f, r * 0.62f, main)
            if (!p.ink) {
                p.detail(hx - r * 0.95f * dir, hy - r * 0.48f, hx + r * 0.95f * dir, hy - r * 0.52f, 0.045f, band)
                p.begin()
                    .add(hx + r * 0.15f * dir, hy - r * 0.2f).add(hx + r * 1.05f * dir, hy - r * 0.22f)
                    .add(hx + r * 1.0f * dir, hy + r * 0.2f).add(hx + r * 0.2f * dir, hy + r * 0.18f)
                    .shapeDetail(L.skin)
                p.dot(hx + r * 0.7f * dir, hy - r * 0.02f, 0.02f, 0xFF101010.toInt())
                p.dot(hx + r * 0.66f * dir, hy - r * 0.07f, 0.008f, 0xFFFFFFFF.toInt())
                p.detail(hx - r * 0.6f * dir, hy - r * 0.8f, hx + r * 0.3f * dir, hy - r * 0.95f, 0.025f, lit)
            }
        }
        body.headRim(hx, hy, r * 1.0f, look.rim)
        eyesAt(hx + r * 0.7f * dir, hy - r * 0.02f)
        p.twoPass {
            if (armed && bladeA < 90f) katana(k.armF.ex, k.armF.ey, dir, bladeA, band)
            body.arm(k.armF, L, far = false)
        }
    }

    /** Katana from the hand; [a] = blade angle from horizontal-forward (radians, + = up). */
    private fun katana(hx: Float, hy: Float, dir: Int, a: Float, wrap: Int) {
        val c = cos(a)
        val s = sin(a)
        val len = 0.66f
        val bx = hx + c * len * dir
        val by = hy - s * len
        p.seg(hx - c * 0.14f * dir, hy + s * 0.14f, hx, hy, 0.045f, wrap)
        p.seg(hx, hy, bx, by, 0.04f, 0xFFC8D0E0.toInt())
        if (!p.ink) {
            p.detail(hx + c * 0.04f * dir, hy - s * 0.04f, bx, by, 0.014f, 0xFFFFFFFF.toInt())
            p.detail(hx - s * 0.05f * dir, hy - c * 0.05f, hx + s * 0.05f * dir, hy + c * 0.05f, 0.03f, 0xFF3A3A48.toInt())
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
            if (!p.ink) {
                // Ribs / lava cracks.
                val crack = if (hell) Col.mul(main, 0.65f) else glow
                p.detail(body.ptX(0.2f, k.waistD * 0.1f), body.ptY(0.2f, k.waistD * 0.1f), body.ptX(0.75f, k.chestD * 0.3f), body.ptY(0.75f, k.chestD * 0.3f), 0.022f, crack)
                p.detail(body.ptX(0.55f, -k.waistD * 0.3f), body.ptY(0.55f, -k.waistD * 0.3f), body.ptX(0.45f, k.waistD * 0.35f), body.ptY(0.45f, k.waistD * 0.35f), 0.018f, crack)
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
            p.disc(hx, hy, r * 1.02f, main)
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
        val body = 0xFF1E2230.toInt()
        val hull = 0xFF2C3246.toInt()
        val lit = 0xFF4A5470.toInt()
        val alarmed = e.state == EnemyState.AIM || e.state == EnemyState.ALERT
        val eyeC = if (alarmed) 0xFFFF2A40.toInt() else pal.neon2
        if (dead) {
            p.flat = 0xFF000000.toInt(); p.flatAmt = min(0.5f, e.stateTime)
        }
        p.twoPass {
            // Arms and motor pods.
            p.seg(-0.38f, -0.1f, 0.38f, -0.1f, 0.05f, body)
            p.seg(-0.38f, -0.18f, -0.38f, -0.08f, 0.07f, body)
            p.seg(0.38f, -0.18f, 0.38f, -0.08f, 0.07f, body)
            // Hull: teardrop pod with a nose toward the facing side.
            p.begin()
                .add(-0.28f * dir, -0.1f).add(0.18f * dir, -0.12f).add(0.3f * dir, 0.0f)
                .add(0.2f * dir, 0.12f).add(-0.22f * dir, 0.12f).add(-0.32f * dir, 0.01f)
                .shape(hull)
            // Gun pod: muzzle at exactly the shot height.
            p.seg(0.05f * dir, 0.1f, 0.54f * dir, 0.1f, 0.05f, 0xFF0A0A10.toInt())
        }
        if (!p.ink) {
            g.line(-0.2f * dir, -0.09f, 0.16f * dir, -0.1f, 0.03f, p.c(lit))
            g.fillRect(-0.26f, 0.04f, 0.2f, 0.065f, p.c(pal.enemyAccent))
            for (si in 0..1) {
                val s = si * 2 - 1
                val rx = s * 0.38f
                val b = if (dead) 0.2f else abs(sin(f.t * 50f + s))
                g.save()
                g.translate(rx, -0.2f)
                g.scale(1f, 0.22f)
                g.fillCircle(0f, 0f, 0.24f, p.c(0x40A0A8C0))
                g.restore()
                g.line(rx - 0.23f * b, -0.2f, rx + 0.23f * b, -0.2f, 0.028f, p.c(0xC0505A6E.toInt()))
            }
            if (!dead) {
                val ex = 0.14f * dir
                g.fillCircle(ex, 0.0f, 0.13f, p.c(Col.alpha(eyeC, 0.22f)))
                g.fillCircle(ex, 0.0f, 0.07f, p.c(0xFF0A0A10.toInt()))
                g.fillCircle(ex, 0.0f, 0.05f, p.c(eyeC))
                g.fillCircle(ex + 0.018f * dir, -0.018f, 0.018f, p.c(0xFFFFFFFF.toInt()))
                if (e.state == EnemyState.AIM) f.glowDot(0.54f * dir, 0.1f, 0.03f, pal.laser, p.alphaMul)
            }
        }
        g.restore()
        p.reset()
        if (!dead && e.state == EnemyState.PATROL) {
            val sweep = sin(f.t * 2f + e.id) * 0.3f
            f.poly.tri(g, x + 0.14f * dir, y, x + 2.2f * dir, y + 0.6f + sweep, x + 2.2f * dir, y + 1.3f + sweep, Col.alpha(pal.neon2, 0.07f))
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
        val housing = 0xFF2E3040.toInt()
        val lit = 0xFF4A4E66.toInt()
        p.twoPass {
            p.seg(x, rt, x, y - 0.2f, 0.14f, 0xFF22222C.toInt())
            p.begin().add(x - 0.32f, rt).add(x + 0.32f, rt).add(x + 0.26f, rt + 0.1f).add(x - 0.26f, rt + 0.1f).shape(0xFF3A3A46.toInt())
        }
        g.save()
        g.translate(x, y)
        g.rotate(ang)
        p.twoPass {
            p.seg(0.12f, -0.035f, 0.55f, -0.035f, 0.05f, 0xFF15151C.toInt())
            p.seg(0.12f, 0.035f, 0.55f, 0.035f, 0.05f, 0xFF15151C.toInt())
            p.seg(0.1f, 0f, 0.3f, 0f, 0.14f, 0xFF2A2A34.toInt())
        }
        g.restore()
        p.twoPass {
            p.begin().add(x - 0.32f, y - 0.26f).add(x + 0.32f, y - 0.26f).add(x + 0.28f, y - 0.08f).add(x - 0.28f, y - 0.08f).shape(lit)
            p.disc(x, y - 0.02f, 0.22f, housing)
        }
        g.line(x - 0.14f, y - 0.14f, x + 0.06f, y - 0.2f, 0.03f, 0x40FFFFFF)
        if (!dead) {
            val alarmed = e.state == EnemyState.AIM || e.state == EnemyState.ALERT
            val eyeC = if (alarmed) 0xFFFF2A40.toInt() else pal.neon2
            g.fillCircle(x, y, 0.09f, 0xFF08080C.toInt())
            f.glowDot(x, y, 0.055f, eyeC, 1f)
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
            Glyphs.arrow(g, cx, cy, 0.14f, 0f, -1f, 0.05f, chev)
        } else {
            Glyphs.arrow(g, cx, cy + 0.56f, 0.14f, 0f, 1f, 0.05f, chev)
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
            EnemyState.ALERT -> if (e.stateTime < 0.9f) {
                val pop = Rig.backOut(e.stateTime / 0.16f)
                val rise = (1f - pop) * 0.15f
                val fade = if (e.stateTime > 0.75f) 1f - (e.stateTime - 0.75f) / 0.15f else 1f
                bubble(e.x, top + rise, pop, Col.alpha(0xFFFFD21E.toInt(), fade), fade)
                if (pop > 0.05f) exclaimGlyph(e.x, top + rise - 0.06f * pop, 0.34f * pop, Col.alpha(0xFF1A0A00.toInt(), fade))
            }
            EnemyState.SEARCH -> {
                val sway = sin(f.t * 4f + e.id) * 0.06f
                val pop = Rig.backOut(e.stateTime / 0.2f)
                bubble(e.x + sway, top, pop, 0xE8E8ECFF.toInt(), 1f)
                if (pop > 0.3f) f.worldText("?", e.x + sway, top - 0.05f, 0.4f * pop, 0xFF1A1A30.toInt(), Gfx.Font.TITLE)
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
        val w = 0.19f * s
        val h = 0.46f * s
        val by = bottom - 0.08f * s
        val o = ActorPaint.OUT
        val inkCol = Col.alpha(ActorPaint.INK, fade)
        g.fillRoundRect(x - w - o, by - h - o, x + w + o, by + o, 0.09f * s + o, inkCol)
        f.poly.tri(g, x - 0.07f * s - o, by - 0.01f, x + 0.07f * s + o, by - 0.01f, x, bottom + o * 1.3f, inkCol)
        g.fillRoundRect(x - w, by - h, x + w, by, 0.09f * s, color)
        f.poly.tri(g, x - 0.07f * s, by - 0.02f, x + 0.07f * s, by - 0.02f, x, bottom, color)
        g.line(x - w * 0.55f, by - h + 0.06f * s, x - w * 0.55f, by - h * 0.45f, 0.03f * s, Col.alpha(0xFFFFFFFF.toInt(), 0.35f * fade))
    }

    /** Shape-drawn "!" of height [s] ending at [bottom]. */
    private fun exclaimGlyph(x: Float, bottom: Float, s: Float, color: Int) {
        val top = bottom - s - 0.08f * s / 0.34f
        val barBot = bottom - s * 0.36f
        f.poly.quad(g, x - 0.06f * s / 0.34f, top, x + 0.06f * s / 0.34f, top, x + 0.035f * s / 0.34f, barBot, x - 0.035f * s / 0.34f, barBot, color)
        g.fillCircle(x, bottom - s * 0.13f, 0.042f * s / 0.34f, color)
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
        val n = e.maxHp
        val pw = 0.12f
        val gap = 0.03f
        val total = n * pw + (n - 1) * gap
        var px = x - total / 2f
        g.fillRoundRect(px - 0.05f, y - 0.075f, px + total + 0.05f, y + 0.075f, 0.05f, 0xD0000000.toInt())
        g.strokeRoundRect(px - 0.05f, y - 0.075f, px + total + 0.05f, y + 0.075f, 0.05f, 0.012f, 0x40FFFFFF)
        for (i in 0 until n) {
            if (i < e.hp) {
                g.fillRect(px, y - 0.04f, px + pw, y + 0.04f, 0xFFFF4A5E.toInt())
                g.fillRect(px, y - 0.04f, px + pw, y - 0.015f, 0xFFFFA0A8.toInt())
            } else {
                g.fillRect(px, y - 0.04f, px + pw, y + 0.04f, 0x40FFFFFF)
            }
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
