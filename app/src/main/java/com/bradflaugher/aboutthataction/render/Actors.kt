package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Body
import com.bradflaugher.aboutthataction.engine.Enemy
import com.bradflaugher.aboutthataction.engine.EnemyKind
import com.bradflaugher.aboutthataction.engine.EnemyState
import com.bradflaugher.aboutthataction.engine.HallState
import com.bradflaugher.aboutthataction.engine.Hero
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
 * most readable thing on screen, whichever hero is playing (see [HeroArt]).
 */
internal class Actors(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly
    private val p = ActorPaint(f)
    private val k = Rig()
    private val body = ActorBody(p, k)
    private val cast = ActorCast(f, p, k, body)
    private val art = HeroArt(f, p, k, body)

    // Renderer-side animation memory (visual only; the simulation never reads it).
    private var wasAir = false
    private var landT = 9f
    private var landHard = 0f
    private var watchT = 0f

    companion object {
        /** The agent reads larger than the guards: the hero scale (visual only; hitboxes are the engine's). */
        const val HS = HeroArt.HS
        /** The zone's neon catching the box's back edge. */
        const val RIM = 0xFF7CF4FF.toInt()
        /** When FOX's takedown kick lands, into the takedown (s). */
        const val KICK_HIT = 0.16f
        /** How far the guard is booted back off her kick by [t] s into the takedown. */
        fun kickPush(t: Float) = 0.34f * Rig.easeOut((t - KICK_HIT) / 0.07f) + 0.3f * Rig.smooth((t - KICK_HIT - 0.06f) / 0.16f)
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
        art.hero = f.w.hero
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
        val hiddenArrival = pl.state == PlayerState.PASSAGE && f.w.silent && pl.stateTime >= f.w.passageTime * 0.5f
        val hidden = pl.state == PlayerState.DOOR || hiddenArrival
        // Stepping through a passage: into the dark doorway, then out of the far one.
        val passing = if (pl.state == PlayerState.PASSAGE) {
            val half = f.w.passageTime * 0.5f
            Rig.smooth(if (pl.stateTime < half) pl.stateTime / half else (f.w.passageTime - pl.stateTime) / half)
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
            PlayerState.DOOR -> art.doorHide(pl.x, foot, dir, f.t)
            PlayerState.PASSAGE -> if (hiddenArrival) art.doorHide(pl.x, foot, dir, f.t) else {
                // Walking into the doorway's dark (a touch smaller: deeper in), then out.
                poseHero(pl.x, foot, dir)
                p.flat = 0xFF04050A.toInt()
                p.flatAmt = 0.92f * passing
                val sc = 1f - 0.07f * passing
                g.save()
                g.translate(pl.x, foot)
                g.scale(sc, sc)
                g.translate(-pl.x, -foot)
                art.draw(ghost = false)
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
                        p.flat = if (i % 2 == 0) art.kit.accent else art.kit.echo
                        p.flatAmt = 1f
                        poseHero(pl.x - pl.vx * 0.045f * i, foot, dir)
                        art.draw(ghost = true)
                    }
                    p.noInk = false
                    p.alphaMul = keepA
                    p.flatAmt = keepF
                    p.flat = keepC
                }
                // FRAGILE: a hit that misses shimmers the figure, glassy, like it slipped right past.
                if (pl.fragileTime > 0f) fragileShimmer(pl.x, foot, dir, pl.fragileTime)
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
                if (pl.state == PlayerState.TAKEDOWN && f.w.hero == Hero.FOX) {
                    // FOX kicks: the guard reels off her boot, then she's drawn over him.
                    val victim = takedownVictim()
                    if (victim != null) {
                        val keepA = p.alphaMul
                        val keepF = p.flatAmt
                        val keepC = p.flat
                        cast.kickedVictim(victim, gy, dir, pl.stateTime, KICK_HIT, kickPush(pl.stateTime))
                        p.alphaMul = keepA; p.flatAmt = keepF; p.flat = keepC
                        poseHero(pl.x, foot, dir)
                    }
                    art.draw(ghost = false)
                    kickImpact(pl.stateTime - KICK_HIT, k.legF)
                } else if (pl.state == PlayerState.TAKEDOWN) {
                    art.draw(ghost = false, skipFrontArm = true)
                    val victim = takedownVictim()
                    if (victim != null) {
                        val keepA = p.alphaMul
                        val keepF = p.flatAmt
                        val keepC = p.flat
                        cast.chokedVictim(victim, gy, heroNeckX, heroNeckY, pl.stateTime)
                        p.alphaMul = keepA; p.flatAmt = keepF; p.flat = keepC
                        poseHero(pl.x, foot, dir)
                    }
                    // The front arm over the victim, in full kit: pad, tape and all.
                    art.push()
                    p.twoPass { art.frontArm() }
                    art.strips()
                    art.pop()
                } else {
                    art.draw(ghost = false)
                }
                if (flip != 0f) g.restore()
                if (pl.flyingKickTime > 0f) kickImpact(World.KICK_POSE_TIME - pl.flyingKickTime, k.legF)
                if (pl.spinKickTime > 0f) spinSweep(pl.x, foot, dir, 1f - pl.spinKickTime / World.KICK_POSE_TIME)
            }
        }
        p.reset()
    }

    /** The boot landing: a hot flash and a burst ring at the heel, [age] s after contact. */
    private fun kickImpact(age: Float, l: Limb) {
        if (age > -0.07f && age < 0.05f) {
            // Speed lines trailing the shin as the leg snaps out.
            val a = 1f - abs(age + 0.01f) / 0.06f
            g.blend(Gfx.Blend.ADD)
            val nx = -(l.ey - l.jy)
            val ny = l.ex - l.jx
            for (i in -1..1) {
                val o = i * 0.07f
                g.line(l.jx + nx * o, l.jy + ny * o - 0.02f, l.ex + nx * o - (l.ex - l.jx) * 0.25f, l.ey + ny * o - (l.ey - l.jy) * 0.25f, 0.025f, Col.alpha(0xFFFFF0E0.toInt(), 0.5f * a.coerceIn(0f, 1f)))
            }
            g.blend(Gfx.Blend.NORMAL)
        }
        if (age < 0f || age > 0.12f) return
        val q = age / 0.12f
        val x = l.ex + cos(l.pitch) * k.dir * 0.04f
        val y = l.ey - 0.02f
        g.blend(Gfx.Blend.ADD)
        g.glow(x, y, 0.22f + 0.2f * q, Col.alpha(0xFFFFE0B0.toInt(), 0.9f * (1f - q)))
        g.strokeCircle(x, y, 0.1f + 0.3f * q, 0.05f * (1f - q) + 0.01f, Col.alpha(art.kit.accent, 1f - q))
        g.blend(Gfx.Blend.NORMAL)
    }

    /** SPIN KICK: one bright smear round her at knee-to-hip height as the boot comes round. */
    private fun spinSweep(x: Float, foot: Float, dir: Int, u: Float) {
        val a = (1f - u).coerceIn(0f, 1f)
        if (a <= 0.02f) return
        g.save()
        g.translate(x, foot - 0.62f * HS)
        g.scale(1f, 0.32f)
        g.blend(Gfx.Blend.ADD)
        val start = if (dir > 0) 200f + 300f * u else -20f - 300f * u
        g.glow(0f, 0f, 1.0f, Col.alpha(art.kit.accent, 0.25f * a))
        g.strokeArc(0f, 0f, 0.85f, start, 150f * dir, 0.24f, Col.alpha(art.kit.accent, 0.7f * a))
        g.strokeArc(0f, 0f, 0.85f, start + 60f * dir, 90f * dir, 0.08f, Col.alpha(0xFFFFF0E0.toInt(), 0.8f * a))
        g.blend(Gfx.Blend.NORMAL)
        g.restore()
    }

    /**
     * FRAGILE's near miss: the figure goes glassy (mostly see-through) with two pale, heat-haze echoes
     * wavering either side of it, fading out as the shimmer wears off.
     */
    private fun fragileShimmer(x: Float, foot: Float, dir: Int, left: Float) {
        val keepA = p.alphaMul
        val keepF = p.flatAmt
        val keepC = p.flat
        val q = (left / 0.4f).coerceIn(0f, 1f)
        p.noInk = true
        p.flatAmt = 1f
        p.flat = 0xFFD8FFE8.toInt()
        for (i in 0..1) {
            p.alphaMul = keepA * 0.28f * q
            val off = (if (i == 0) -1f else 1f) * (0.035f + 0.025f * sin(f.t * 47f + i * 2f))
            poseHero(x + off, foot, dir)
            art.draw(ghost = true)
        }
        p.noInk = false
        p.flatAmt = keepF
        p.flat = keepC
        p.alphaMul = keepA * (1f - 0.62f * q)
    }

    private var heroNeckX = 0f
    private var heroNeckY = 0f

    /**
     * The agent's beacon, so he's found in half a second on any floor: a backlight in the
     * hero's accent hugging the silhouette and a crisp ring on the floor under him. Three calls, all additive.
     */
    private fun signature(x: Float, gy: Float, foot: Float, z: Float, boxed: Boolean, k: Float) {
        val a = p.alphaMul * k
        if (a <= 0.01f) return
        val cy = if (boxed) foot - 0.4f else foot - 0.85f
        g.blend(Gfx.Blend.ADD)
        g.save()
        g.translate(x, cy)
        g.scale(if (boxed) 1f else 0.62f, 1f)
        val accent = art.kit.accent
        g.glow(0f, 0f, if (boxed) 1.15f else 1.6f, Col.alpha(accent, 0.4f * a))
        g.restore()
        val fz = (1f - z / 2.5f).coerceIn(0f, 1f)
        if (fz > 0f) {
            val pulse = 0.85f + 0.15f * sin(f.t * 3.2f)
            val r = if (boxed) 0.66f else 0.5f
            g.save()
            g.translate(x, gy - 0.02f)
            g.scale(1f, 0.24f)
            g.glow(0f, 0f, r * 1.7f, Col.alpha(accent, 0.4f * a * fz))
            g.strokeCircle(0f, 0f, r, 0.1f, Col.alpha(art.kit.rim, 0.55f * a * fz * pulse))
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
        if (pl.state != PlayerState.NORMAL || pl.jumpsUsed < 2 || pl.z <= 0.01f || pl.flyingKickTime > 0f) return 0f
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
        posePlayer(x, foot, dir)
        // Hand the pose's gun and hair state to the painter.
        val pl = f.w.player
        art.gunKind = gunKind
        art.gunUp = gunUp
        art.gunX = gunX
        art.gunY = gunY
        art.showGun = showGun
        art.flash = flash
        art.magInHand = magInHand
        art.holstered = !showGun && f.w.silent
        art.live = pl.state != PlayerState.DEAD && pl.state != PlayerState.DOOR
        art.run = if (art.live) (abs(pl.vx) / 5f).coerceIn(0f, 1f) else 0f
        art.fall = if (art.live) (-pl.vz / 9f).coerceIn(0f, 1f) else 0f
        art.bob = if (art.live) sin(pl.runTime * 2.05f * Rig.TAU * 2f) * 0.1f * art.run else 0f
        art.idle = if (art.live) sin(f.t * 1.7f) * 0.03f else 0f
        art.dim = 1f
    }

    private fun posePlayer(x: Float, foot: Float, dir: Int) {
        val pl = f.w.player
        val state = pl.state
        art.setup(dir, foot)
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
        // MONKEY has no takedowns: his rifle comes off his back the moment it has someone to shoot.
        val drawn = !f.w.holstered && (shooting || aimTarget() != null)
        val silent = f.w.silent && !drawn && !pl.reloading && (state == PlayerState.NORMAL || state == PlayerState.ELEVATOR || state == PlayerState.PASSAGE)
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
                if (pl.flyingKickTime > 0f) flyingKick(x, World.KICK_POSE_TIME - pl.flyingKickTime)
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
            state == PlayerState.TAKEDOWN && f.w.hero == Hero.FOX -> {
                kickPose(x, foot, dir, pl.stateTime)
                lean = k.lean
                showGun = false
            }
            state == PlayerState.TAKEDOWN -> {
                val q = (pl.stateTime / World.TAKEDOWN_TIME).coerceIn(0f, 1f)
                val grab = Rig.easeOut(min(1f, pl.stateTime / 0.08f))
                k.stand(x, 0.1f + 0.04f * grab, 0.24f, -0.26f)
                lean = -0.14f - 0.14f * q
                k.spine(lean, 0.15f)
                heroNeckX = art.shownX(k.neckX)
                heroNeckY = art.shownY(k.neckY)
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
                k.stand(x, 0.015f + breathe * 0.006f + squash, Rig.mix(0.1f, 0.17f, wide), Rig.mix(-0.13f, -0.2f, wide))
                lean = -0.01f + breathe * 0.01f + squash * 1.2f
                k.spine(lean, -0.04f)
                k.armFK(k.armB, -0.12f - breathe * 0.02f, 0.3f)
                k.armFK(k.armF, 0.25f, 0.5f)
            }
        }
        heroNeckX = art.shownX(k.neckX)
        heroNeckY = art.shownY(k.neckY)
        if (state == PlayerState.TAKEDOWN || state == PlayerState.INTRO && f.w.difficulty.startFloor == 0) return
        if (pl.flyingKickTime > 0f && airborne) {
            // The kick owns the arms for its moment: no aiming over it.
            gunX = k.armF.ex; gunY = k.armF.ey; gunUp = 0.5f
            return
        }

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

    /**
     * FOX's takedown: a side kick at hip height. The knee chambers up to her chest, the leg
     * shoots out heel first into the guard as she leans back from it over the planted leg,
     * fists up in guard, then the boot comes back half-way while he reels.
     */
    private fun kickPose(x: Float, foot: Float, dir: Int, t: Float) {
        val chamber = Rig.smooth(t / 0.1f)
        val ext = Rig.easeOut((t - 0.1f) / (KICK_HIT - 0.1f)) * (1f - 0.45f * Rig.smooth((t - 0.26f) / 0.1f))
        // She rocks back off the planted leg to give the kick its length.
        k.stand(x - 0.18f * dir * chamber, 0.05f + 0.03f * chamber, 0.02f, -0.1f)
        val hx = k.hipX
        val hy = k.hipY
        // Chambered: the knee high, the heel tucked under the hip; extended: the heel into his
        // back (the engine has him 0.45 ahead of her), then riding him as he's booted off it.
        val cx = hx + 0.12f * dir * HS
        val cy = hy + 0.2f * HS
        val reach = (0.45f + 0.18f * chamber - 0.12f + kickPush(t)).coerceAtMost(0.8f * HS)
        val ex = hx + reach * dir
        val ey = hy - 0.06f * HS
        val sx = k.legF.ex
        val sy = k.legF.ey
        val tx = if (ext > 0f) Rig.mix(cx, ex, ext) else Rig.mix(sx, cx, chamber)
        val ty = if (ext > 0f) Rig.mix(cy, ey, ext) else Rig.mix(sy, cy, chamber)
        k.ik(k.legF, tx, ty, true)
        k.legF.pitch = Rig.mix(Rig.mix(0f, 0.7f, chamber), -1.35f, ext)
        k.spine(-0.12f * chamber - 0.5f * ext, 0.3f * ext)
        // Guard: the near fist up by the chin, the far one out for balance.
        k.armFK(k.armF, Rig.mix(0.4f, 0.9f, chamber), Rig.mix(0.8f, 2.1f, chamber))
        k.armFK(k.armB, Rig.mix(0.3f, 1.1f, ext), Rig.mix(1.2f, 1.9f, chamber))
    }

    /** FOX's FLYING KICK over the air pose: the near leg shot out straight, the other tucked. */
    private fun flyingKick(x: Float, age: Float) {
        val ext = Rig.easeOut(age / 0.05f) * (1f - Rig.smooth((age - 0.2f) / 0.1f))
        val hy = k.hipY
        k.hip(x, hy)
        k.legFK(k.legF, Rig.mix(1.35f, 1.95f, ext), Rig.mix(2.1f, 0.04f, ext))
        k.legFK(k.legB, Rig.mix(0.5f, 0.35f, ext), Rig.mix(1.9f, 2.3f, ext))
        k.legF.pitch = Rig.mix(0.5f, -1.2f, ext)
        k.legB.pitch = 0.9f
        k.spine(Rig.mix(k.lean, -0.35f, ext), 0.2f * ext)
        k.armFK(k.armF, Rig.mix(1.2f, 0.9f, ext), Rig.mix(0.9f, 2.0f, ext))
        k.armFK(k.armB, Rig.mix(-1.2f, -1.6f, ext), Rig.mix(0.9f, 0.4f, ext))
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

    // ------------------------------------------------------------ hide, die

    private fun deadHero(x: Float, gy: Float, dir: Int) {
        val pl = f.w.player
        val t = pl.stateTime
        val fall = if (abs(pl.vx) > 0.2f) (if (pl.vx > 0f) 1 else -1) else -dir
        p.flat = 0xFFFF2030.toInt()
        p.flatAmt = max(0f, 0.5f - t * 0.8f)
        art.live = false
        art.holstered = false
        art.dim = 0.3f
        art.kit.look(art.look, ghost = false)
        art.push(x, gy)
        cast.ragdoll(x, gy, pl.z, dir, HS, fall, t, 0.55f, CastDeath.KNOCK, art.look, heroRagdollHead, bulk = art.kit.bulk, arm = heroRagdollArm)
        art.pop()
        p.flatAmt = 0f
    }

    private val heroRagdollHead: (Int) -> Unit = { d -> art.ragdollDress(d) }
    private val heroRagdollArm: () -> Unit = { art.ragdollArm() }

    // --------------------------------------------------------------- box

    /** The cardboard box: printed markings, a peek slit with eyes, a waddle, the "!" moment. */
    private fun box(x: Float, gy: Float, dir: Int, vx: Float, t: Float) {
        val pl = f.w.player
        val kit = art.kit
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

        // The commando's own box: a size up, and it's seen some jungle.
        val battered = kit.batteredBox
        val bs = if (battered) 1.08f else 1f
        g.save()
        g.translate(x + shake, gy - bob - hop - peek)
        g.rotate(tilt)
        g.scale(bs, sqY * bs)
        if (moving || peek > 0.02f) {
            // Boots shuffling under the box.
            val s = if (moving) sin(ph) * 0.13f else 0f
            p.twoPass {
                p.seg(-0.2f + s, -0.02f + bob + peek, -0.08f + s, -0.02f + bob + peek, 0.09f, kit.boxFeet)
                p.seg(0.12f - s, -0.02f + bob + peek, 0.24f - s, -0.02f + bob + peek, 0.09f, kit.boxFeet)
            }
            if (peek > 0.02f) {
                g.fillRect(-hw + 0.04f, -0.02f, hw - 0.04f, peek * 0.8f, p.c(0xE0050308.toInt()))
                val ex = 0.18f * dir
                f.glowDot(ex - 0.05f, peek * 0.35f, 0.022f, kit.eyes, 0.9f * p.alphaMul)
                f.glowDot(ex + 0.05f, peek * 0.35f, 0.022f, kit.eyes, 0.9f * p.alphaMul)
            }
        }
        val d = 0.1f // depth offset of the receding top/side
        val rs = -dir.toFloat() // the receding side face is on the trailing side
        val fl = 0.06f * sin(f.t * 1.3f)
        p.twoPass {
            // Side face, lid, the two flaps, the front face: each painted like lamp-lit board.
            p.begin().add(hw * rs, -h).add(hw * rs + d * 0.8f * rs, -h - d).add(hw * rs + d * 0.8f * rs, -d * 0.8f).add(hw * rs, 0f)
            p.shape(BoxArt.BOX_SIDE)
            p.shapeGradDetail(BoxArt.BOX_SIDE, ActorPaint.shade(BoxArt.BOX_SIDE), 0f, -h, 0f, 0f)
            p.begin().add(-hw, -h).add(hw, -h).add(hw + d * 0.8f * rs, -h - d).add(-hw + d * 0.8f * rs, -h - d)
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
            // The face turns away from the lamp toward the trailing corner.
            p.begin().add(hw * rs, -h).add(hw * rs - 0.16f * rs, -h).add(hw * rs - 0.16f * rs, 0f).add(hw * rs, 0f)
            p.shapeGradDetail(0x3A140A18, 0x00140A18, hw * rs, 0f, hw * rs - 0.16f * rs, 0f)
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
            val bx = hw * rs + d * 0.8f * rs
            g.line(bx, -h - d + 0.02f, bx, -d * 0.8f - 0.02f, 0.02f, p.c(Col.alpha(RIM, 0.35f)))
            g.blend(Gfx.Blend.NORMAL)
        }
        // Packing tape: over the lid seam and down the front, a glossy strip with a torn end.
        val tw = 0.065f
        val te = -h + 0.3f
        p.begin().add(-tw + d * 0.8f * rs, -h - d).add(tw + d * 0.8f * rs, -h - d).add(tw, -h).add(tw, te).add(tw * 0.5f, te + 0.025f)
            .add(0f, te - 0.004f).add(-tw * 0.5f, te + 0.028f).add(-tw, te).add(-tw, -h)
        p.shapeDetail(BoxArt.BOX_TAPE)
        if (p.shading) {
            p.detail(-tw + 0.012f, -h - d + 0.01f, -tw + 0.012f, te - 0.01f, 0.01f, Col.alpha(ActorPaint.shade(BoxArt.BOX_FRONT), 0.35f))
            g.blend(Gfx.Blend.ADD)
            g.line(tw - 0.02f, -h + 0.02f, tw - 0.02f, te - 0.03f, 0.016f, p.c(0x50FFFFFF))
            g.line(-tw * 0.3f, -h - d + 0.015f, tw * 0.7f, -h - d + 0.015f, 0.012f, p.c(0x40FFFFFF))
            g.blend(Gfx.Blend.NORMAL)
        }
        if (battered) batter(hw, h, rs)
        // "This side up" arrows in a printed frame on the trailing half.
        val px = 0.27f * rs
        Glyphs.arrow(g, px - 0.07f, -0.28f, 0.11f, 0f, -1f, 0.036f, p.c(BoxArt.BOX_PRINT))
        Glyphs.arrow(g, px + 0.07f, -0.28f, 0.11f, 0f, -1f, 0.036f, p.c(BoxArt.BOX_PRINT))
        g.strokeRect(px - 0.145f, -0.425f, px + 0.145f, -0.13f, 0.014f, p.c(BoxArt.BOX_PRINT))
        // A red FRAGILE stamp, inked on a slant, on the leading half.
        val stx = -0.25f * rs
        g.save()
        g.translate(stx, -0.22f)
        g.rotate(-7f * dir)
        g.strokeRoundRect(-0.14f, -0.075f, 0.14f, 0.075f, 0.02f, 0.022f, p.c(BoxArt.BOX_STAMP))
        p.detail(-0.085f, -0.018f, 0.085f, -0.018f, 0.028f, BoxArt.BOX_STAMP)
        p.detail(-0.085f, 0.03f, 0.04f, 0.03f, 0.016f, BoxArt.BOX_STAMP)
        g.restore()
        kit.boxDecal(rs)
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
            g.line(hx - 0.09f + look, sy, hx - 0.03f + look, sy, 0.02f, p.c(kit.eyes))
            g.line(hx + 0.03f + look, sy, hx + 0.09f + look, sy, 0.02f, p.c(kit.eyes))
        } else {
            g.fillCircle(hx - 0.058f + look, sy, 0.036f, p.c(kit.eyes))
            g.fillCircle(hx + 0.058f + look, sy, 0.036f, p.c(kit.eyes))
            g.blend(Gfx.Blend.ADD)
            g.glow(hx + look, sy, 0.2f, p.c(Col.alpha(kit.eyes, 0.6f)))
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

    /** Field wear on the commando's box: a crushed corner, a patch of tape, mud and a scrawled mark. */
    private fun batter(hw: Float, h: Float, rs: Float) {
        // The leading top corner crushed in.
        p.begin().add(-hw * rs, -h).add(-hw * rs + 0.2f * rs, -h).add(-hw * rs, -h + 0.16f)
        p.shapeDetail(ActorPaint.shade(BoxArt.BOX_FRONT))
        p.detail(-hw * rs + 0.2f * rs, -h, -hw * rs, -h + 0.16f, 0.014f, BoxArt.BOX_CUT)
        // A tape patch slapped over a tear, on a slant.
        p.begin().add(0.06f * rs, -0.5f).add(0.34f * rs, -0.58f).add(0.37f * rs, -0.5f).add(0.09f * rs, -0.42f)
        p.shapeDetail(Col.alpha(0xFFB8B8A8.toInt(), 0.9f))
        if (!p.shading) return
        p.detail(0.14f * rs, -0.55f, 0.26f * rs, -0.47f, 0.01f, Col.alpha(ActorPaint.shade(BoxArt.BOX_FRONT), 0.7f))
        // Mud up from the floor and a dent crease.
        p.begin().add(-hw, -0.1f).add(-hw + 0.3f, -0.2f).add(0.1f, -0.08f).add(hw - 0.2f, -0.16f).add(hw, -0.06f).add(hw, 0f).add(-hw, 0f)
        p.shapeGradDetail(0x00302418, 0x90302418.toInt(), 0f, -0.2f, 0f, 0f)
        p.detail(-0.1f * rs, -h + 0.08f, 0.02f * rs, -0.6f, 0.012f, Col.alpha(ActorPaint.shade(BoxArt.BOX_FRONT), 0.6f))
    }

    /** The cardboard: board tones, the pale corrugated cut edge, tape and printer's inks. */
    private object BoxArt {
        const val BOX_FRONT = 0xFFC48E56.toInt()
        const val BOX_SIDE = 0xFF94643A.toInt()
        const val BOX_TOP = 0xFFDCAA6C.toInt()
        const val BOX_FLAP = 0xFFD09A62.toInt()
        const val BOX_FLAP_FAR = 0xFFB07A46.toInt()
        const val BOX_CUT = 0xFFEED2A0.toInt()
        const val BOX_TAPE = 0xE0ECD4A2.toInt()
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

    /**
     * A pickup's icon in the world: the very same pictogram as its HUD pill, so what you see on
     * the floor is what the HUD says you picked up. (Cash spells its $ in world text.)
     */
    fun pickupIcon(kind: PickupKind, x: Float, y: Float, col: Int, s: Float = 1f) {
        if (kind == PickupKind.CASH) {
            g.strokeCircle(x, y, 0.15f * s, 0.03f * s, col)
            f.worldText("$", x, y + 0.075f * s, 0.2f * s, col, Gfx.Font.TITLE)
            return
        }
        HudIcons.pickup(g, kind, x, y, 0.36f * s, col)
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
