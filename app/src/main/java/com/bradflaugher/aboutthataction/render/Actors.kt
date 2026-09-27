package com.bradflaugher.aboutthataction.render

import com.bradflaugher.aboutthataction.engine.Body
import com.bradflaugher.aboutthataction.engine.Enemy
import com.bradflaugher.aboutthataction.engine.EnemyKind
import com.bradflaugher.aboutthataction.engine.EnemyState
import com.bradflaugher.aboutthataction.engine.FloorState
import com.bradflaugher.aboutthataction.engine.Geo
import com.bradflaugher.aboutthataction.engine.Heat
import com.bradflaugher.aboutthataction.engine.PickupKind
import com.bradflaugher.aboutthataction.engine.PlayerState
import com.bradflaugher.aboutthataction.engine.Zone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Everyone who moves: the agent, the guards, the drones, the demons, the loot. */
internal class Actors(private val f: Frame) {
    private val g get() = f.g
    private val poly get() = f.poly
    private val k = Skel()

    /** Colour modifiers applied by [c]: global alpha and a flat override (hit flash / silhouette). */
    private var alphaMul = 1f
    private var flat = 0
    private var flatAmt = 0f

    private fun c(color: Int): Int {
        var col = color
        if (flatAmt > 0f) col = Col.lerp(col, flat or (col and 0xFF000000.toInt()), flatAmt)
        return if (alphaMul >= 1f) col else Col.fade(col, alphaMul)
    }

    private fun resetTint() {
        alphaMul = 1f
        flatAmt = 0f
    }

    companion object {
        const val SUIT = 0xFF26304C.toInt()
        const val SUIT_DARK = 0xFF171D30.toInt()
        const val SUIT_RIM = 0xFF5AE6FF.toInt()
        const val VISOR = 0xFF3CF4FF.toInt()
        const val SCARF = 0xFFFF3B2F.toInt()
        const val SCARF_DARK = 0xFFB81E22.toInt()
        const val GUN = 0xFF0B0B10.toInt()
        const val OUTLINE = 0xFFB8F6FF.toInt()
    }

    // ================================================================ skeleton

    /** Joint positions for one humanoid pose, in world units (y down). */
    private class Skel {
        var dir = 1
        var hs = 1f
        var foot = 0f
        var hipX = 0f; var hipY = 0f
        var neckX = 0f; var neckY = 0f
        var headX = 0f; var headY = 0f; var headR = 0f
        var kneeFX = 0f; var kneeFY = 0f; var footFX = 0f; var footFY = 0f
        var kneeBX = 0f; var kneeBY = 0f; var footBX = 0f; var footBY = 0f
        var elbowFX = 0f; var elbowFY = 0f; var handFX = 0f; var handFY = 0f
        var elbowBX = 0f; var elbowBY = 0f; var handBX = 0f; var handBY = 0f
        var limbW = 0f
        var torsoW = 0f

        fun legs(x: Float, foot: Float, dir: Int, hs: Float, phase: Float, run: Float, crouch: Float, air: Float) {
            this.dir = dir
            this.hs = hs
            this.foot = foot
            val l1 = 0.38f * hs
            val l2 = 0.38f * hs
            val legLen = l1 + l2
            var aF: Float
            var bF: Float
            var aB: Float
            var bB: Float
            if (air > 0f) {
                aF = 0.9f; bF = 1.5f; aB = 0.2f; bB = 1.2f
            } else {
                val s = sin(phase)
                aF = s * 0.75f * run + 0.06f * (1 - run)
                aB = -s * 0.75f * run - 0.06f * (1 - run)
                bF = (0.15f + max(0f, cos(phase)) * 1.1f) * run + 0.05f
                bB = (0.15f + max(0f, -cos(phase)) * 1.1f) * run + 0.05f
            }
            if (crouch > 0f) {
                aF = aF * (1 - crouch) + 1.35f * crouch
                bF = bF * (1 - crouch) + 1.45f * crouch
                aB = aB * (1 - crouch) + 0.1f * crouch
                bB = bB * (1 - crouch) + 1.6f * crouch
            }
            val bob = if (air > 0f) 0f else abs(sin(phase)) * 0.05f * run * hs
            val hipH = legLen * (1f - 0.45f * crouch) - (if (air > 0f) 0.05f else 0.02f) + bob
            hipX = x
            hipY = foot - hipH
            kneeFX = hipX + sin(aF) * l1 * dir; kneeFY = hipY + cos(aF) * l1
            footFX = kneeFX + sin(aF - bF) * l2 * dir; footFY = min(foot, kneeFY + cos(aF - bF) * l2)
            kneeBX = hipX + sin(aB) * l1 * dir; kneeBY = hipY + cos(aB) * l1
            footBX = kneeBX + sin(aB - bB) * l2 * dir; footBY = min(foot, kneeBY + cos(aB - bB) * l2)
            limbW = 0.13f * hs
            torsoW = 0.27f * hs
        }

        fun torso(lean: Float, torsoLen: Float = 0.48f) {
            neckX = hipX + lean * dir * 0.25f * hs
            neckY = hipY - torsoLen * hs
            headR = 0.13f * hs
            headX = neckX + 0.03f * dir * hs
            headY = neckY - 0.16f * hs
        }

        /** Arms: mode 0 = swing, 1 = aim at height [aimY], 2 = raised, 3 = reach to (tx, ty), 4 = limp back. */
        fun arms(mode: Int, phase: Float, run: Float, aimY: Float = 0f, tx: Float = 0f, ty: Float = 0f) {
            val sx = neckX
            val sy = neckY + 0.06f * hs
            val u = 0.28f * hs
            when (mode) {
                1 -> {
                    handFX = sx + 0.52f * hs * dir; handFY = aimY
                    elbowFX = (sx + handFX) / 2f; elbowFY = (sy + handFY) / 2f + 0.03f
                    handBX = sx + 0.4f * hs * dir; handBY = aimY + 0.04f
                    elbowBX = sx + 0.12f * dir; elbowBY = (sy + handBY) / 2f + 0.1f
                }
                2 -> {
                    elbowFX = sx - 0.1f * dir; elbowFY = sy - u
                    handFX = sx + 0.05f * dir; handFY = sy - u * 1.9f
                    elbowBX = sx - 0.18f * dir; elbowBY = sy + u * 0.6f
                    handBX = sx - 0.05f * dir; handBY = sy + u * 1.2f
                }
                3 -> {
                    elbowFX = (sx + tx) / 2f; elbowFY = (sy + ty) / 2f + 0.08f
                    handFX = tx; handFY = ty
                    elbowBX = (sx + tx) / 2f - 0.05f * dir; elbowBY = (sy + ty) / 2f - 0.05f
                    handBX = tx - 0.08f * dir; handBY = ty - 0.08f
                }
                4 -> {
                    elbowFX = sx - 0.25f * dir; elbowFY = sy + 0.1f
                    handFX = sx - 0.5f * dir; handFY = sy + 0.05f
                    elbowBX = sx - 0.2f * dir; elbowBY = sy - 0.15f
                    handBX = sx - 0.45f * dir; handBY = sy - 0.25f
                }
                else -> {
                    val s = sin(phase) * 0.8f * run
                    val aF = -s + 0.15f
                    val aB = s + 0.15f
                    elbowFX = sx + sin(aF) * u * dir; elbowFY = sy + cos(aF) * u
                    handFX = elbowFX + sin(aF + 0.5f) * u * dir; handFY = elbowFY + cos(aF + 0.5f) * u
                    elbowBX = sx + sin(aB) * u * dir; elbowBY = sy + cos(aB) * u
                    handBX = elbowBX + sin(aB + 0.5f) * u * dir; handBY = elbowBY + cos(aB + 0.5f) * u
                }
            }
        }
    }

    private fun limb(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, w: Float, color: Int) {
        g.line(x1, y1, x2, y2, w, color)
        g.line(x2, y2, x3, y3, w * 0.92f, color)
    }

    private fun backLimbs(leg: Int, arm: Int) {
        limb(k.hipX, k.hipY, k.kneeBX, k.kneeBY, k.footBX, k.footBY, k.limbW * 1.1f, c(leg))
        limb(k.neckX, k.neckY + 0.06f * k.hs, k.elbowBX, k.elbowBY, k.handBX, k.handBY, k.limbW * 0.9f, c(arm))
    }

    private fun frontLeg(leg: Int, boot: Int) {
        limb(k.hipX, k.hipY, k.kneeFX, k.kneeFY, k.footFX, k.footFY, k.limbW * 1.1f, c(leg))
        g.line(k.footFX - 0.02f * k.dir, k.footFY - 0.03f, k.footFX + 0.1f * k.dir * k.hs, k.footFY - 0.03f, k.limbW * 0.8f, c(boot))
        g.line(k.footBX - 0.02f * k.dir, k.footBY - 0.03f, k.footBX + 0.1f * k.dir * k.hs, k.footBY - 0.03f, k.limbW * 0.8f, c(Col.mul(boot, 0.7f)))
    }

    private fun torso(color: Int, width: Float = k.torsoW) {
        g.line(k.hipX, k.hipY, k.neckX, k.neckY + 0.04f, width, c(color))
    }

    private fun frontArm(color: Int) {
        limb(k.neckX, k.neckY + 0.06f * k.hs, k.elbowFX, k.elbowFY, k.handFX, k.handFY, k.limbW * 0.95f, c(color))
    }

    private fun pistol(hx: Float, hy: Float, dir: Int, long: Float = 0.24f, color: Int = GUN) {
        g.line(hx - 0.02f * dir, hy, hx + long * dir, hy - 0.01f, 0.08f, c(color))
        g.line(hx, hy, hx - 0.03f * dir, hy + 0.1f, 0.06f, c(color))
    }

    private fun muzzleFlash(x: Float, y: Float, dir: Int, size: Float) {
        val s = size
        g.fillCircle(x, y, s * 0.9f, c(0x55FFD060))
        poly.begin().add(x, y - s * 0.35f).add(x + s * 1.6f * dir, y).add(x, y + s * 0.35f).add(x + s * 0.3f * dir, y).fill(g, c(0xFFFFE890.toInt()))
        poly.begin().add(x + s * 0.2f * dir, y - s * 0.8f).add(x + s * 0.7f * dir, y).add(x + s * 0.2f * dir, y + s * 0.8f).add(x + s * 0.45f * dir, y).fill(g, c(0xFFFFB030.toInt()))
        g.fillCircle(x, y, s * 0.3f, c(0xFFFFFFFF.toInt()))
    }

    // ================================================================= player

    fun player(force: Boolean = false) {
        val p = f.w.player
        if (p.state == PlayerState.ELEVATOR && !force) return
        if (p.state == PlayerState.INTEL) return
        val gy = Geo.groundY(p.floorF)
        if (f.w.floors[0] != null) helicopter()
        if (p.state == PlayerState.INTRO && f.w.difficulty.startFloor > 0) hatch(p.x, p.floorF)
        val foot = gy - p.z
        val dir = if (p.facing >= 0) 1 else -1

        resetTint()
        if (p.invuln > 0f && p.state != PlayerState.DEAD && !f.inPerk) {
            if ((f.t * 14f).toInt() % 2 == 0) alphaMul = 0.4f
        }

        // Backlight halo: the player is always the easiest thing to find.
        if (p.state != PlayerState.DOOR) {
            g.fillRadialGradient(p.x, foot - 0.75f, 1.4f, Col.alpha(VISOR, 0.22f * alphaMul), Col.alpha(VISOR, 0f))
        }
        // Slow-mo afterimages.
        if (f.w.slowMo && abs(p.vx) > 0.5f) {
            val keep = alphaMul
            for (i in 1..3) {
                alphaMul = keep * (0.22f - i * 0.05f)
                flat = if (i % 2 == 0) 0xFF3CF4FF.toInt() else 0xFFFF3D9A.toInt()
                flatAmt = 1f
                agent(p.x - p.vx * 0.045f * i, foot, dir, p.runTime, p.state, p.z, p.sinceShot, p.stateTime, true)
            }
            flatAmt = 0f
            alphaMul = keep
        }
        when (p.state) {
            PlayerState.BOX -> box(p.x, gy, dir, p.vx, p.stateTime)
            PlayerState.DOOR -> {
                flat = 0xFF05060A.toInt()
                flatAmt = 0.92f
                agent(p.x, foot, dir, 0f, PlayerState.NORMAL, 0f, 9f, 0f, false)
                flatAmt = 0f
                // Just the visor glinting out of the dark.
                g.fillRoundRect(k.headX - 0.02f * dir - 0.08f, k.headY - 0.03f, k.headX + 0.14f * dir * 0f + 0.1f, k.headY + 0.04f, 0.03f, c(VISOR))
                f.glowDot(k.headX + 0.06f * dir, k.headY, 0.03f, VISOR, 0.8f)
            }
            PlayerState.DEAD -> deadAgent(p.x, gy, dir, p.stateTime, p.z)
            else -> {
                // Neon rim: a light outline so the agent reads on any wall.
                val keep = alphaMul
                flat = OUTLINE
                flatAmt = 1f
                alphaMul = keep * 0.9f
                for (i in 0 until 4) {
                    val ox = if (i % 2 == 0) -0.032f else 0.032f
                    val oy = if (i < 2) -0.032f else 0.032f
                    agent(p.x + ox, foot + oy, dir, p.runTime, p.state, p.z, p.sinceShot, p.stateTime, true)
                }
                flatAmt = 0f
                alphaMul = keep
                agent(p.x, foot, dir, p.runTime, p.state, p.z, p.sinceShot, p.stateTime, false)
            }
        }
        resetTint()
    }

    /** After the darkness overlay: shield bubble, reload arc. */
    fun playerOverlay() {
        val p = f.w.player
        if (p.state == PlayerState.INTEL || p.state == PlayerState.DEAD) return
        val gy = Geo.groundY(p.floorF)
        val foot = gy - p.z
        val cy = if (p.state == PlayerState.BOX) foot - 0.45f else foot - 0.78f
        if (p.shield) {
            val r = if (p.state == PlayerState.BOX) 0.72f else 0.98f
            val pulse = 0.75f + 0.25f * sin(f.t * 5f)
            g.fillCircle(p.x, cy, r, Col.alpha(0xFF3CC8FF.toInt(), 0.1f * pulse))
            g.strokeCircle(p.x, cy, r, 0.05f, Col.alpha(0xFF7AE8FF.toInt(), 0.75f * pulse))
            for (i in 0 until 6) {
                val a = f.t * 1.4f + i * PI.toFloat() / 3f
                g.fillCircle(p.x + cos(a) * r, cy + sin(a) * r, 0.04f, 0xFFBFF6FF.toInt())
            }
        }
        if (p.armorReady && p.state != PlayerState.DOOR) {
            g.strokeCircle(p.x, cy, 0.85f, 0.025f, Col.alpha(0xFFFFD24A.toInt(), 0.35f))
        }
        if (p.reloading && p.state != PlayerState.DOOR) {
            val t = 1f - p.reloadTime / max(0.01f, p.reloadTotal)
            val ry = foot - (if (p.state == PlayerState.BOX) 1.15f else 1.95f)
            reloadArc(p.x, ry, 0.2f, t)
        }
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

    /** The protagonist: slate sneaking suit, cyan visor, a long red scarf. */
    private fun agent(x: Float, foot: Float, dir: Int, runTime: Float, state: PlayerState, z: Float, sinceShot: Float, stateTime: Float, ghost: Boolean) {
        val p = f.w.player
        val air = if (state == PlayerState.INTRO || (state == PlayerState.NORMAL && z > 0.02f)) 1f else 0f
        var phase = runTime * 2f * PI.toFloat() * 1.45f
        var run = min(1f, abs(p.vx) / 3f)
        if (state == PlayerState.STAIRS) {
            phase = stateTime * 14f
            run = 1f
        }
        if (state == PlayerState.TAKEDOWN || state == PlayerState.ELEVATOR) run = 0f
        val breathe = sin(f.t * 2.2f) * 0.012f
        k.legs(x, foot, dir, 1f, phase, run, 0f, air)
        k.torso(if (run > 0.3f) 0.35f else 0.05f + breathe * 2f)
        k.neckY += breathe
        k.headY += breathe
        val shooting = sinceShot < 0.28f && !p.reloading
        val recoil = if (sinceShot < 0.08f) (0.08f - sinceShot) * 1.2f else 0f
        when {
            state == PlayerState.TAKEDOWN -> {
                val tx = x + 0.42f * dir
                k.torso(-0.25f)
                k.arms(3, 0f, 0f, tx = tx, ty = foot - 1.22f)
            }
            state == PlayerState.INTRO && f.w.difficulty.startFloor == 0 -> k.arms(2, 0f, 0f)
            shooting -> {
                k.arms(1, 0f, 0f, aimY = foot - 1.02f)
                k.handFX -= recoil * dir
                k.elbowFX -= recoil * dir
            }
            p.reloading -> {
                k.arms(3, 0f, 0f, tx = x + 0.25f * dir, ty = foot - 0.82f)
            }
            else -> k.arms(0, phase, max(run, air * 0.4f))
        }
        if (!ghost) scarf(dir, run, air, z)
        backLimbs(SUIT_DARK, SUIT_DARK)
        frontLeg(SUIT, 0xFF0C0E16.toInt())
        torso(SUIT)
        // Belt, harness strap, rim light on the back.
        g.line(k.hipX - 0.13f, k.hipY + 0.02f, k.hipX + 0.13f, k.hipY + 0.02f, 0.06f, c(0xFF0C0E16.toInt()))
        g.line(k.neckX - 0.1f * dir, k.neckY + 0.08f, k.hipX + 0.1f * dir, k.hipY - 0.05f, 0.035f, c(0xFF3A4668.toInt()))
        g.line(k.hipX - 0.12f * dir, k.hipY - 0.05f, k.neckX - 0.12f * dir, k.neckY + 0.1f, 0.03f, c(Col.alpha(SUIT_RIM, 0.55f)))
        // Head: hood + visor.
        g.fillCircle(k.headX, k.headY, k.headR, c(SUIT))
        g.fillCircle(k.headX - 0.02f * dir, k.headY - 0.03f, k.headR * 0.95f, c(SUIT_DARK))
        g.fillRoundRect(
            min(k.headX - 0.02f * dir, k.headX + 0.15f * dir), k.headY - 0.035f,
            max(k.headX - 0.02f * dir, k.headX + 0.15f * dir), k.headY + 0.045f, 0.035f, c(VISOR),
        )
        if (!ghost) g.fillCircle(k.headX + 0.1f * dir, k.headY, 0.09f, c(Col.alpha(VISOR, 0.3f)))
        // Arm + gun.
        frontArm(SUIT)
        val weapon = p.weapon
        val gunLen = if (weapon == PickupKind.SHOTGUN) 0.42f else if (weapon == PickupKind.MINIGUN) 0.5f else 0.24f
        if (state != PlayerState.TAKEDOWN && state != PlayerState.INTRO) {
            pistol(k.handFX, k.handFY, dir, gunLen, if (weapon == null) GUN else 0xFF2A2A34.toInt())
            if (weapon == PickupKind.MINIGUN) g.line(k.handFX, k.handFY + 0.06f, k.handFX + gunLen * dir, k.handFY + 0.05f, 0.05f, c(0xFF4A4A56.toInt()))
            if (sinceShot < 0.06f && !ghost) muzzleFlash(k.handFX + (gunLen + 0.05f) * dir, k.handFY - 0.01f, dir, if (weapon == PickupKind.SHOTGUN) 0.3f else 0.2f)
        }
        g.fillCircle(k.handFX, k.handFY, 0.055f, c(0xFF0C0E16.toInt()))
    }

    private val scarfPts = FloatArray(12)

    private fun scarf(dir: Int, run: Float, air: Float, z: Float) {
        val p = f.w.player
        val baseX = k.neckX - 0.05f * dir
        val baseY = k.neckY + 0.06f
        // Knot around the neck.
        g.line(k.neckX - 0.1f, k.neckY + 0.06f, k.neckX + 0.1f, k.neckY + 0.05f, 0.1f, c(SCARF))
        val speed = min(1f, abs(p.vx) / 4f + air * 0.6f)
        val up = if (p.vz < -1f) -0.6f else if (p.state == PlayerState.INTRO) -0.8f else 0f
        var x = baseX
        var y = baseY
        scarfPts[0] = x; scarfPts[1] = y
        for (i in 1..5) {
            val seg = 0.16f
            val wave = sin(f.t * 9f - i * 1.1f) * (0.04f + speed * 0.05f) * i
            val droop = (1f - speed) * 0.7f + 0.1f
            x += -dir * seg * (0.45f + speed * 0.55f)
            y += seg * droop + wave * 0.5f + up * seg
            scarfPts[i * 2] = x
            scarfPts[i * 2 + 1] = y
        }
        for (i in 0 until 5) {
            val w = 0.1f - i * 0.012f
            g.line(scarfPts[i * 2], scarfPts[i * 2 + 1], scarfPts[i * 2 + 2], scarfPts[i * 2 + 3], w, c(if (i % 2 == 0) SCARF else Col.lerp(SCARF, SCARF_DARK, 0.4f)))
        }
        // Frayed twin tails.
        val ex = scarfPts[10]
        val ey = scarfPts[11]
        g.line(ex, ey, ex - dir * 0.12f, ey + 0.05f + sin(f.t * 11f) * 0.03f, 0.04f, c(SCARF))
        g.line(ex, ey, ex - dir * 0.1f, ey - 0.04f + sin(f.t * 13f) * 0.03f, 0.035f, c(SCARF_DARK))
    }

    private fun deadAgent(x: Float, gy: Float, dir: Int, t: Float, z: Float) {
        val ang = min(90f, t * 260f) * -dir
        g.save()
        g.translate(x, gy - z)
        g.rotate(ang)
        flat = 0xFFFF2030.toInt()
        flatAmt = 0.25f
        agent(0f, 0f, dir, 0f, PlayerState.NORMAL, 0f, 9f, 0f, false)
        flatAmt = 0f
        g.restore()
    }

    /** The cardboard box. Peeking eyes; a "!" when someone is looking right at it. */
    private fun box(x: Float, gy: Float, dir: Int, vx: Float, t: Float) {
        val moving = abs(vx) > 0.2f
        val bob = if (moving) abs(sin(f.t * 14f)) * 0.05f else 0f
        val tilt = if (moving) sin(f.t * 14f) * 2.5f else 0f
        val h = Body.BOX_HEIGHT
        val w = 0.95f
        g.save()
        g.translate(x, gy - bob)
        g.rotate(tilt)
        if (moving) {
            // Little feet shuffling under the box.
            val s = sin(f.t * 14f) * 0.12f
            g.line(-0.15f + s, -0.05f, -0.15f + s, 0.0f, 0.1f, c(0xFF0C0E16.toInt()))
            g.line(0.15f - s, -0.05f, 0.15f - s, 0.0f, 0.1f, c(0xFF0C0E16.toInt()))
        }
        g.fillRect(-w / 2f, -h, w / 2f, -0.04f, c(0xFFB9854E.toInt()))
        g.fillRect(-w / 2f, -h, w / 2f, -h + 0.1f, c(0xFFD49A5E.toInt()))
        g.fillRect(-w / 2f, -0.14f, w / 2f, -0.04f, c(0xFF9A6A3A.toInt()))
        // Flaps.
        poly.quad(g, -w / 2f, -h, -w / 2f + 0.3f, -h, -w / 2f + 0.22f, -h - 0.1f, -w / 2f - 0.05f, -h - 0.08f, c(0xFFC8905A.toInt()))
        poly.quad(g, w / 2f, -h, w / 2f - 0.3f, -h, w / 2f - 0.2f, -h - 0.09f, w / 2f + 0.05f, -h - 0.07f, c(0xFFA87844.toInt()))
        // Tape and print.
        g.fillRect(-0.06f, -h, 0.06f, -0.04f, c(0xFFD8B070.toInt()))
        g.strokeRect(-w / 2f, -h, w / 2f, -0.04f, 0.025f, c(0xFF6A4424.toInt()))
        Glyphs.arrow(g, -0.3f * dir, -0.45f, 0.1f, 0f, -1f, 0.03f, c(0xFF6A4424.toInt()))
        Glyphs.arrow(g, -0.2f * dir, -0.45f, 0.1f, 0f, -1f, 0.03f, c(0xFF6A4424.toInt()))
        // Handle slot with eyes.
        val hx = 0.24f * dir
        g.fillRoundRect(hx - 0.13f, -h + 0.2f, hx + 0.13f, -h + 0.31f, 0.05f, c(0xFF120A04.toInt()))
        val look = dir * 0.025f
        g.fillCircle(hx - 0.05f + look, -h + 0.255f, 0.028f, c(VISOR))
        g.fillCircle(hx + 0.05f + look, -h + 0.255f, 0.028f, c(VISOR))
        g.restore()
        // Enemy looking at the box? Sweat it out.
        val p = f.w.player
        var watched = false
        for (e in f.w.enemies) {
            if (e.floor != p.floor || !e.alive) continue
            val dx = p.x - e.x
            if (abs(dx) < 4f && (dx > 0) == (e.facing > 0) && e.state != EnemyState.PATROL) {
                watched = true
                break
            }
        }
        if (watched) {
            val pop = 1f + 0.15f * sin(f.t * 16f)
            f.worldText("!", x, gy - h - 0.25f, 0.5f * pop, 0xFFFF3B2F.toInt(), Gfx.Font.TITLE)
        }
    }

    // ============================================================= helicopter

    private fun helicopter() {
        val w = f.w
        val gy0 = Geo.groundY(0)
        val drift = max(0f, w.time - 1.2f)
        val hx = 2.3f + drift * drift * 3.2f + drift * 1.2f
        val hy = gy0 - 9.35f - drift * 1.5f + sin(f.t * 1.7f) * 0.08f
        if (hx > Geo.FLOOR_W + 4f || hy < f.camY - 3f) return
        val p = w.player
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
        if (p.state == PlayerState.INTRO && p.floorF == 0f) {
            val py = gy0 - p.z - 1.3f
            g.line(hx - 0.3f, hy + 0.4f, p.x + 0.02f, py, 0.035f, 0xFF1A1622.toInt())
        } else if (drift < 2f) {
            val sway = sin(f.t * 2f) * 0.3f
            g.line(hx - 0.3f, hy + 0.4f, hx - 0.3f + sway, hy + 2.2f - drift, 0.035f, 0xFF1A1622.toInt())
        }
        val body = 0xFF1A1830.toInt()
        val bodyHi = 0xFF2E2A4E.toInt()
        // Tail boom + fin + tail rotor.
        poly.quad(g, hx - 0.8f, hy - 0.05f, hx - 3.1f, hy - 0.25f, hx - 3.1f, hy - 0.08f, hx - 0.8f, hy + 0.3f, body)
        poly.quad(g, hx - 2.9f, hy - 0.2f, hx - 3.25f, hy - 0.75f, hx - 3.35f, hy - 0.72f, hx - 3.15f, hy - 0.1f, body)
        val tr = f.t * 60f
        g.line(hx - 3.2f + cos(tr) * 0.35f, hy - 0.4f + sin(tr) * 0.35f, hx - 3.2f - cos(tr) * 0.35f, hy - 0.4f - sin(tr) * 0.35f, 0.04f, 0x90A0A0C0.toInt())
        g.strokeCircle(hx - 3.2f, hy - 0.4f, 0.35f, 0.015f, 0x30A0A0C0)
        // Fuselage.
        poly.begin()
            .add(hx - 1.0f, hy - 0.45f).add(hx + 0.5f, hy - 0.5f).add(hx + 1.25f, hy - 0.1f)
            .add(hx + 1.3f, hy + 0.2f).add(hx + 0.8f, hy + 0.5f).add(hx - 0.8f, hy + 0.5f).add(hx - 1.15f, hy + 0.1f)
            .fill(g, body)
        poly.begin().add(hx - 1.0f, hy - 0.45f).add(hx + 0.5f, hy - 0.5f).add(hx + 0.9f, hy - 0.3f).add(hx - 1.05f, hy - 0.3f).fill(g, bodyHi)
        // Cockpit glass.
        poly.begin().add(hx + 0.45f, hy - 0.42f).add(hx + 1.15f, hy - 0.08f).add(hx + 1.18f, hy + 0.12f).add(hx + 0.45f, hy + 0.1f).fill(g, 0xFF2CD8FF.toInt())
        poly.begin().add(hx + 0.55f, hy - 0.36f).add(hx + 0.8f, hy - 0.24f).add(hx + 0.6f, hy + 0.05f).add(hx + 0.5f, hy + 0.05f).fill(g, 0x80FFFFFF.toInt())
        // Open side door.
        g.fillRect(hx - 0.55f, hy - 0.3f, hx + 0.05f, hy + 0.4f, 0xFF07060C.toInt())
        g.fillRect(hx - 0.55f, hy - 0.3f, hx + 0.05f, hy - 0.25f, 0x60FF3D9A)
        // Neon stripe.
        g.fillRect(hx - 1.1f, hy + 0.22f, hx + 1.25f, hy + 0.27f, 0xFFFF2E88.toInt())
        // Skids.
        g.line(hx - 0.6f, hy + 0.5f, hx - 0.7f, hy + 0.75f, 0.04f, body)
        g.line(hx + 0.6f, hy + 0.5f, hx + 0.7f, hy + 0.75f, 0.04f, body)
        g.line(hx - 1.1f, hy + 0.75f, hx + 1.2f, hy + 0.75f, 0.05f, body)
        // Rotor mast and blur.
        g.fillRect(hx - 0.05f, hy - 0.72f, hx + 0.05f, hy - 0.48f, body)
        val blade = abs(sin(f.t * 40f))
        g.fillRoundRect(hx - 2.6f, hy - 0.8f, hx + 2.6f, hy - 0.7f, 0.05f, 0x40B0B0D0)
        g.line(hx - 2.6f * blade, hy - 0.75f, hx + 2.6f * blade, hy - 0.75f, 0.05f, 0xA0303048.toInt())
        // Nav lights.
        if (sin(f.t * 5f) > 0f) f.glowDot(hx - 3.3f, hy - 0.75f, 0.05f, 0xFFFF3040.toInt())
        if (sin(f.t * 5f + 2f) > 0f) f.glowDot(hx + 1.2f, hy + 0.25f, 0.045f, 0xFF40FF80.toInt())
    }

    private fun hatch(x: Float, floorF: Float) {
        val fi = floorF.toInt()
        val rt = fi * Geo.FLOOR_H + Building.SLAB
        val p = f.w.player
        val a = min(1f, p.z / 1.2f)
        g.fillRect(x - 0.45f, rt - 0.35f, x + 0.45f, rt + 0.02f, 0xFF050308.toInt())
        poly.quad(g, x - 0.45f, rt, x + 0.45f, rt, x + 0.8f, rt + 3.2f, x - 0.8f, rt + 3.2f, Col.alpha(0xFFE8F8FF.toInt(), 0.1f * a))
        // Hatch flap hanging open.
        poly.quad(g, x + 0.45f, rt, x + 0.5f, rt, x + 0.62f, rt + 0.85f, x + 0.56f, rt + 0.85f, 0xFF4A4858.toInt())
    }

    // ================================================================ enemies

    fun floorActors(fi: Int, fs: FloorState) {
        val gy = Geo.groundY(fi)
        if (!f.visibleY(gy - Geo.FLOOR_H, gy + 0.5f)) return
        val pal = f.palette(fs)
        val w = f.w
        for (pk in w.pickups) if (pk.floor == fi) pickup(pk.kind, pk.x, gy - pk.z - 0.15f, pk.age, pk.life)
        for (e in w.enemies) if (e.floor == fi) enemy(e, gy, pal, fs)
        for (gr in w.grenades) if (gr.floor == fi) grenade(gr.x, gy - gr.z, gr.fuse)
    }

    private fun zoneOf(fs: FloorState) = if (fs.plan.index == 0) Zone.TOWER else fs.plan.zone

    private fun enemy(e: Enemy, gy: Float, pal: Palette, fs: FloorState) {
        resetTint()
        val dir = if (e.facing >= 0) 1 else -1
        val zone = zoneOf(fs)
        if (e.state == EnemyState.EMERGING) alphaMul = min(1f, 0.2f + e.stateTime / 0.45f)
        if (e.state == EnemyState.DEAD) {
            alphaMul = if (e.stateTime > 1.05f) max(0f, 1f - (e.stateTime - 1.05f) / 0.5f) else 1f
            flat = 0xFF000000.toInt()
            flatAmt = min(0.5f, e.stateTime * 0.8f)
        }
        if (e.hurtFlash > 0f) {
            flat = 0xFFFFFFFF.toInt()
            flatAmt = 1f
        }
        if (fs.plan.isVoid && hash((f.t * 10f).toInt(), e.id) > 0.85f) {
            // Glitch: RGB-split echo.
            val keep = alphaMul
            alphaMul = keep * 0.5f
            flat = 0xFF2BFFE0.toInt(); flatAmt = 1f
            enemyBody(e, e.x + 0.08f, gy, dir, zone, pal, fs)
            flat = 0xFFFF2BD6.toInt()
            enemyBody(e, e.x - 0.08f, gy, dir, zone, pal, fs)
            flatAmt = 0f
            alphaMul = keep
        }
        // AIM telegraph goes under the body so the gun sits on top of the line.
        if (e.state == EnemyState.AIM) aimTelegraph(e, gy, dir, pal, fs)
        if (e.state == EnemyState.WINDUP) windupTelegraph(e, gy, dir)
        enemyBody(e, e.x, gy, dir, zone, pal, fs)
        resetTint()
        if (e.alive) statusMarks(e, gy, dir)
    }

    private fun enemyBody(e: Enemy, x: Float, gy: Float, dir: Int, zone: Zone, pal: Palette, fs: FloorState) {
        when (e.kind) {
            EnemyKind.DRONE -> drone(e, x, gy, dir, pal)
            EnemyKind.TURRET -> turret(e, x, gy, pal)
            else -> {
                if (e.state == EnemyState.DEAD) {
                    val ang = min(95f, e.stateTime * 320f) * (if (e.deathVx >= 0f) 1f else -1f)
                    g.save()
                    g.translate(x, gy - e.z)
                    g.rotate(ang)
                    humanoidEnemy(e, 0f, 0f, dir, zone, pal)
                    g.restore()
                } else {
                    humanoidEnemy(e, x, gy - e.z, dir, zone, pal)
                }
            }
        }
    }

    private fun humanoidEnemy(e: Enemy, x: Float, foot: Float, dir: Int, zone: Zone, pal: Palette) {
        val hs = when (e.kind) {
            EnemyKind.HEAVY -> 1.12f
            EnemyKind.DEMON -> 1.1f
            else -> 1f
        }
        val moving = abs(e.vx) > 0.1f
        val phase = e.walkPhase * 1.6f
        val run = if (moving) min(1f, abs(e.vx) / 2.5f + 0.4f) else 0f
        val crouch = when {
            e.ducking -> 1f
            e.kind == EnemyKind.NINJA && e.state == EnemyState.ALERT && moving -> 0.35f
            e.kind == EnemyKind.DEMON -> 0.18f
            else -> 0f
        }
        k.legs(x, foot, dir, hs, phase, run, crouch, 0f)
        val lean = when {
            e.kind == EnemyKind.NINJA && moving -> 0.9f
            e.kind == EnemyKind.DEMON -> 0.6f
            e.state == EnemyState.CHOKED -> -0.7f
            else -> if (run > 0.5f) 0.25f else 0f
        }
        k.torso(lean, if (e.ducking) 0.44f else 0.48f)
        val aimH = when {
            e.ducking -> foot - 0.5f
            else -> foot - Body.HIGH
        }
        when (e.state) {
            EnemyState.AIM -> k.arms(1, 0f, 0f, aimY = aimH)
            EnemyState.WINDUP -> k.arms(2, 0f, 0f)
            EnemyState.CHOKED -> {
                // Clawing at the arm around the neck; legs kicking.
                k.arms(3, 0f, 0f, tx = k.neckX - 0.15f * dir, ty = k.neckY + 0.02f)
                val kick = sin(f.t * 22f) * 0.12f
                k.footFX += kick; k.footFY -= abs(kick)
            }
            EnemyState.DEAD -> k.arms(4, 0f, 0f)
            EnemyState.ALERT -> if (e.kind == EnemyKind.AGENT || e.kind == EnemyKind.HEAVY) k.arms(1, 0f, 0f, aimY = aimH + 0.25f) else k.arms(0, phase, run)
            else -> k.arms(0, phase, run)
        }
        when (e.kind) {
            EnemyKind.HEAVY -> heavy(e, dir, zone, pal)
            EnemyKind.NINJA -> ninja(e, dir, pal)
            EnemyKind.DEMON -> demon(e, dir, zone)
            else -> guard(e, dir, zone, pal)
        }
        // Health pips on damaged tough guys.
        if (e.alive && e.maxHp > 1 && e.hp < e.maxHp) hpPips(e, k.headX, k.headY - k.headR - 0.2f)
    }

    private fun hpPips(e: Enemy, x: Float, y: Float) {
        val n = e.maxHp
        val pw = 0.11f
        val total = n * pw + (n - 1) * 0.03f
        var px = x - total / 2f
        g.fillRoundRect(px - 0.04f, y - 0.07f, px + total + 0.04f, y + 0.07f, 0.04f, 0xB0000000.toInt())
        for (i in 0 until n) {
            g.fillRect(px, y - 0.035f, px + pw, y + 0.035f, if (i < e.hp) 0xFFFF4A5E.toInt() else 0x50FFFFFF)
            px += pw + 0.03f
        }
    }

    /** The rank and file, reskinned per zone. */
    private fun guard(e: Enemy, dir: Int, zone: Zone, pal: Palette) {
        val main = pal.enemyMain
        val dark = Col.mul(main, if (Col.r(main) > 128) 0.72f else 0.6f)
        val skin = pal.enemySkin
        val robe = zone == Zone.HELL
        backLimbs(dark, dark)
        if (robe) {
            // Hooded cultist robe.
            poly.begin()
                .add(k.neckX - 0.14f, k.neckY + 0.02f).add(k.neckX + 0.14f, k.neckY + 0.02f)
                .add(k.hipX + 0.32f, k.foot - 0.02f).add(k.hipX - 0.32f, k.foot - 0.02f)
                .fill(g, c(main))
            g.fillRect(k.hipX - 0.3f, k.foot - 0.12f, k.hipX + 0.3f, k.foot - 0.02f, c(pal.enemyAccent))
        } else {
            frontLeg(if (zone == Zone.LABS || zone == Zone.MAGMA) main else dark, 0xFF0A0A0E.toInt())
            torso(main)
        }
        when (zone) {
            Zone.TOWER, Zone.ROOFTOP, Zone.VOID -> {
                // Black suit: shirt V, red tie.
                poly.tri(g, k.neckX - 0.07f, k.neckY + 0.05f, k.neckX + 0.07f, k.neckY + 0.05f, k.neckX + 0.02f * dir, k.neckY + 0.28f, c(0xFFE8E8F0.toInt()))
                g.line(k.neckX + 0.01f * dir, k.neckY + 0.07f, k.neckX + 0.02f * dir, k.neckY + 0.26f, 0.04f, c(pal.enemyAccent))
            }
            Zone.METRO -> {
                g.line(k.hipX - 0.14f, k.hipY - 0.15f, k.hipX + 0.14f, k.hipY - 0.15f, 0.05f, c(pal.enemyAccent))
                g.line(k.neckX - 0.13f, k.neckY + 0.12f, k.neckX + 0.13f, k.neckY + 0.12f, 0.05f, c(pal.enemyAccent))
            }
            Zone.MINES -> {
                g.line(k.neckX - 0.08f * dir, k.neckY + 0.05f, k.hipX - 0.05f * dir, k.hipY - 0.05f, 0.04f, c(0xFF22303C.toInt()))
                g.line(k.hipX - 0.13f, k.hipY - 0.2f, k.hipX + 0.13f, k.hipY - 0.2f, 0.04f, c(0xFFFFD21E.toInt()))
            }
            Zone.LABS -> g.line(k.hipX - 0.13f, k.hipY - 0.22f, k.hipX + 0.13f, k.hipY - 0.22f, 0.04f, c(pal.enemyAccent))
            Zone.MAGMA -> g.line(k.neckX - 0.14f, k.neckY + 0.15f, k.neckX + 0.14f, k.neckY + 0.15f, 0.05f, c(0xFF7A7068.toInt()))
            else -> Unit
        }
        // Head per zone.
        val hx = k.headX
        val hy = k.headY
        val r = k.headR
        when (zone) {
            Zone.TOWER, Zone.ROOFTOP, Zone.VOID -> {
                g.fillCircle(hx, hy, r, c(skin))
                // Shades.
                g.fillRoundRect(min(hx, hx + 0.14f * dir), hy - 0.03f, max(hx, hx + 0.14f * dir), hy + 0.035f, 0.02f, c(0xFF050508.toInt()))
                // Fedora.
                g.fillRect(hx - 0.2f, hy - r * 0.55f, hx + 0.2f, hy - r * 0.35f, c(0xFF0A0A10.toInt()))
                g.fillRoundRect(hx - 0.12f, hy - r * 1.35f, hx + 0.12f, hy - r * 0.45f, 0.04f, c(0xFF0A0A10.toInt()))
                g.fillRect(hx - 0.12f, hy - r * 0.7f, hx + 0.12f, hy - r * 0.55f, c(pal.enemyAccent))
            }
            Zone.LABS -> {
                g.fillCircle(hx, hy, r * 1.08f, c(main))
                g.fillRoundRect(min(hx - 0.02f * dir, hx + 0.15f * dir), hy - 0.06f, max(hx - 0.02f * dir, hx + 0.15f * dir), hy + 0.03f, 0.03f, c(0xFF101818.toInt()))
                g.fillCircle(hx + 0.07f * dir, hy - 0.015f, 0.035f, c(pal.enemyAccent))
                g.fillCircle(hx + 0.13f * dir, hy + 0.07f, 0.05f, c(0xFF4A5858.toInt()))
            }
            Zone.METRO -> {
                g.fillCircle(hx, hy, r, c(skin))
                g.fillCircle(hx - 0.01f * dir, hy - 0.02f, r * 1.15f, c(0xFF1A2230.toInt()))
                g.fillRoundRect(min(hx, hx + 0.16f * dir), hy - 0.05f, max(hx, hx + 0.16f * dir), hy + 0.07f, 0.03f, c(0xB04A7AA8.toInt()))
            }
            Zone.MINES -> {
                g.fillCircle(hx, hy, r, c(skin))
                g.fillRect(hx - 0.17f, hy - r * 0.5f, hx + 0.17f, hy - r * 0.3f, c(0xFFFFD21E.toInt()))
                g.fillRoundRect(hx - 0.13f, hy - r * 1.3f, hx + 0.13f, hy - r * 0.4f, 0.1f, c(0xFFFFD21E.toInt()))
                // Headlamp beam.
                if (e.alive) {
                    val lx = hx + 0.12f * dir
                    val ly = hy - r * 0.8f
                    f.glowDot(lx, ly, 0.035f, 0xFFFFF4C0.toInt(), alphaMul)
                    poly.quad(g, lx, ly - 0.03f, lx, ly + 0.03f, lx + 2.2f * dir, ly + 0.9f, lx + 2.2f * dir, ly - 0.35f, c(0x1AFFF4C0))
                }
            }
            Zone.MAGMA -> {
                g.fillCircle(hx, hy, r * 1.12f, c(main))
                g.fillRoundRect(min(hx - 0.03f * dir, hx + 0.16f * dir), hy - 0.07f, max(hx - 0.03f * dir, hx + 0.16f * dir), hy + 0.06f, 0.05f, c(0xFFFFB81E.toInt()))
                g.fillRect(hx + 0.02f * dir, hy - 0.05f, hx + 0.05f * dir, hy + 0.0f, c(0xA0FFFFFF.toInt()))
            }
            Zone.HELL -> {
                // Hood with burning eyes.
                poly.tri(g, hx - 0.18f, hy + 0.12f, hx + 0.18f, hy + 0.12f, hx - 0.06f * dir, hy - 0.28f, c(main))
                g.fillCircle(hx + 0.02f * dir, hy, r * 0.9f, c(Col.mul(main, 0.6f)))
                g.fillCircle(hx + 0.05f * dir, hy + 0.02f, r * 0.62f, c(0xFF100204.toInt()))
                g.fillCircle(hx + 0.08f * dir, hy, 0.022f, c(0xFFFFC040.toInt()))
                g.fillCircle(hx + 0.01f * dir, hy, 0.022f, c(0xFFFFC040.toInt()))
            }
        }
        frontArm(if (robe) main else if (zone == Zone.LABS || zone == Zone.MAGMA) main else dark)
        if (e.state != EnemyState.CHOKED && e.state != EnemyState.DEAD) pistol(k.handFX, k.handFY, dir)
        if (e.state == EnemyState.AIM) {
            g.fillCircle(k.handFX + 0.26f * dir, k.handFY - 0.01f, 0.04f, c(pal.laser))
        }
        g.fillCircle(k.handFX, k.handFY, 0.05f, c(if (zone == Zone.TOWER || zone == Zone.ROOFTOP) skin else dark))
    }

    private fun heavy(e: Enemy, dir: Int, zone: Zone, pal: Palette) {
        val main = if (zone == Zone.LABS || zone == Zone.MAGMA) Col.mul(pal.enemyMain, 0.75f) else Col.lerp(pal.enemyMain, 0xFF2A3040.toInt(), 0.5f)
        val plate = Col.lerp(main, 0xFF8A90A0.toInt(), 0.35f)
        val dark = Col.mul(main, 0.65f)
        k.limbW *= 1.35f
        backLimbs(dark, dark)
        frontLeg(main, 0xFF08080C.toInt())
        torso(main, k.torsoW * 1.6f)
        // Chest plate and shoulder pads.
        g.fillRoundRect(k.hipX - 0.26f, k.neckY + 0.08f, k.hipX + 0.26f, k.hipY - 0.08f, 0.08f, c(plate))
        g.fillRect(k.hipX - 0.2f, k.neckY + 0.28f, k.hipX + 0.2f, k.neckY + 0.32f, c(pal.enemyAccent))
        g.fillCircle(k.neckX - 0.14f * dir, k.neckY + 0.1f, 0.14f, c(plate))
        // Helmet with a glowing visor slit.
        g.fillCircle(k.headX, k.headY, k.headR * 1.2f, c(plate))
        g.fillRect(k.headX - k.headR * 1.2f, k.headY + 0.02f, k.headX + k.headR * 1.2f, k.headY + k.headR * 0.9f, c(dark))
        g.fillRect(min(k.headX, k.headX + 0.17f * dir), k.headY - 0.03f, max(k.headX, k.headX + 0.17f * dir), k.headY + 0.02f, c(pal.enemyAccent))
        frontArm(main)
        if (e.state != EnemyState.CHOKED && e.state != EnemyState.DEAD) {
            // Chunky rotary gun.
            g.line(k.handFX - 0.15f * dir, k.handFY, k.handFX + 0.45f * dir, k.handFY, 0.14f, c(0xFF14141A.toInt()))
            g.line(k.handFX + 0.1f * dir, k.handFY - 0.04f, k.handFX + 0.55f * dir, k.handFY - 0.04f, 0.04f, c(0xFF3A3A46.toInt()))
            g.line(k.handFX + 0.1f * dir, k.handFY + 0.04f, k.handFX + 0.55f * dir, k.handFY + 0.04f, 0.04f, c(0xFF3A3A46.toInt()))
        }
    }

    private fun ninja(e: Enemy, dir: Int, pal: Palette) {
        val main = 0xFF0C0C12.toInt()
        val dark = 0xFF050508.toInt()
        val band = if (f.w.floors[e.floor]?.plan?.zone == Zone.HELL) 0xFFFFB020.toInt() else 0xFFFF2244.toInt()
        backLimbs(dark, dark)
        frontLeg(main, dark)
        torso(main)
        g.line(k.hipX - 0.13f, k.hipY - 0.02f, k.hipX + 0.13f, k.hipY - 0.02f, 0.06f, c(band))
        g.fillCircle(k.headX, k.headY, k.headR, c(main))
        g.fillRect(min(k.headX - 0.02f * dir, k.headX + 0.14f * dir), k.headY - 0.03f, max(k.headX - 0.02f * dir, k.headX + 0.14f * dir), k.headY + 0.02f, c(0xFFE8C8A8.toInt()))
        g.fillCircle(k.headX + 0.09f * dir, k.headY - 0.005f, 0.02f, c(0xFF101010.toInt()))
        // Headband tails.
        val tw = sin(f.t * 12f + e.id) * 0.05f
        g.line(k.headX - 0.12f * dir, k.headY - 0.07f, k.headX - 0.38f * dir, k.headY - 0.02f + tw, 0.04f, c(band))
        g.line(k.headX - 0.12f * dir, k.headY - 0.07f, k.headX - 0.34f * dir, k.headY + 0.08f + tw, 0.035f, c(band))
        g.line(k.headX - 0.13f, k.headY - 0.07f, k.headX + 0.13f, k.headY - 0.07f, 0.05f, c(band))
        frontArm(main)
        // Blade: raised in windup, low otherwise.
        if (e.state != EnemyState.DEAD && e.state != EnemyState.CHOKED) {
            val bx2: Float
            val by2: Float
            if (e.state == EnemyState.WINDUP) {
                bx2 = k.handFX - 0.35f * dir; by2 = k.handFY - 0.55f
            } else {
                bx2 = k.handFX + 0.6f * dir; by2 = k.handFY + 0.15f
            }
            g.line(k.handFX, k.handFY, bx2, by2, 0.05f, c(0xFFD8E0F0.toInt()))
            g.line(k.handFX, k.handFY, bx2, by2, 0.015f, c(0xFFFFFFFF.toInt()))
            g.line(k.handFX - 0.05f * dir, k.handFY + 0.02f, k.handFX + 0.05f * dir, k.handFY - 0.02f, 0.06f, c(band))
        }
    }

    private fun demon(e: Enemy, dir: Int, zone: Zone) {
        val hell = zone == Zone.HELL
        val main = if (hell) 0xFFB0101E.toInt() else 0xFF1A1414.toInt()
        val dark = if (hell) 0xFF6A0612.toInt() else 0xFF0C0808.toInt()
        val glow = if (hell) 0xFFFFD040.toInt() else 0xFFFF6A10.toInt()
        // Tail.
        val tw = sin(f.t * 5f + e.id) * 0.15f
        g.line(k.hipX - 0.1f * dir, k.hipY, k.hipX - 0.45f * dir, k.hipY + 0.2f + tw, 0.07f, c(dark))
        g.line(k.hipX - 0.45f * dir, k.hipY + 0.2f + tw, k.hipX - 0.65f * dir, k.hipY - 0.05f + tw, 0.05f, c(dark))
        poly.tri(g, k.hipX - 0.65f * dir, k.hipY - 0.12f + tw, k.hipX - 0.75f * dir, k.hipY + 0.02f + tw, k.hipX - 0.58f * dir, k.hipY + 0.02f + tw, c(dark))
        k.limbW *= 1.15f
        backLimbs(dark, dark)
        frontLeg(main, dark)
        torso(main, k.torsoW * 1.35f)
        if (!hell) {
            // Lava cracks on obsidian skin.
            g.line(k.hipX - 0.05f, k.hipY - 0.1f, k.neckX + 0.05f, k.neckY + 0.15f, 0.025f, c(glow))
        }
        // Head with horns.
        val hx = k.headX
        val hy = k.headY
        g.fillCircle(hx, hy, k.headR * 1.05f, c(main))
        poly.tri(g, hx - 0.1f, hy - 0.06f, hx - 0.02f, hy - 0.1f, hx - 0.2f * dir - 0.05f, hy - 0.38f, c(0xFFE8D8C0.toInt()))
        poly.tri(g, hx + 0.02f, hy - 0.1f, hx + 0.1f, hy - 0.06f, hx + 0.12f * dir + 0.05f, hy - 0.4f, c(0xFFD8C8B0.toInt()))
        g.fillCircle(hx + 0.08f * dir, hy - 0.01f, 0.03f, c(glow))
        g.fillCircle(hx + 0.02f * dir, hy - 0.01f, 0.025f, c(glow))
        g.fillCircle(hx + 0.08f * dir, hy - 0.01f, 0.07f, c(Col.alpha(glow, 0.3f)))
        poly.tri(g, hx + 0.02f * dir, hy + 0.06f, hx + 0.14f * dir, hy + 0.06f, hx + 0.08f * dir, hy + 0.1f, c(0xFFFFFFFF.toInt()))
        frontArm(main)
        // Claws / fireball in hand.
        for (i in -1..1) g.line(k.handFX, k.handFY, k.handFX + 0.12f * dir, k.handFY + i * 0.06f, 0.03f, c(0xFFE8D8C0.toInt()))
        if (e.state == EnemyState.AIM) {
            val tAim = e.stateTime / Heat.aimTime(f.w.floors[e.floor]?.plan?.heat ?: 1f)
            val r = 0.08f + 0.12f * tAim
            g.fillCircle(k.handFX, k.handFY - 0.1f, r * 2f, c(0x40FF6A10))
            g.fillCircle(k.handFX, k.handFY - 0.1f, r, c(0xFFFFB030.toInt()))
        }
    }

    private fun drone(e: Enemy, x: Float, gy: Float, dir: Int, pal: Palette) {
        var y = gy - e.z - 0.2f
        val dead = e.state == EnemyState.DEAD
        g.save()
        g.translate(x, y)
        if (dead) g.rotate(e.stateTime * 500f * (if (e.deathVx >= 0f) 1f else -1f))
        else g.rotate(e.vx * 6f)
        val body = 0xFF1E2230.toInt()
        // Arms + rotor blurs.
        g.line(-0.38f, -0.08f, 0.38f, -0.08f, 0.05f, c(body))
        for (s in -1..1 step 2) {
            val rx = s * 0.38f
            g.fillRect(rx - 0.03f, -0.16f, rx + 0.03f, -0.08f, c(body))
            val b = if (dead) 0.2f else abs(sin(f.t * 50f + s))
            g.fillRoundRect(rx - 0.22f, -0.2f, rx + 0.22f, -0.15f, 0.03f, c(0x60A0A8C0))
            g.line(rx - 0.22f * b, -0.175f, rx + 0.22f * b, -0.175f, 0.03f, c(0xC0404858.toInt()))
        }
        // Hull.
        g.fillRoundRect(-0.26f, -0.1f, 0.26f, 0.12f, 0.1f, c(body))
        g.fillRoundRect(-0.26f, -0.1f, 0.26f, -0.03f, 0.05f, c(0xFF3A4058.toInt()))
        g.fillRect(-0.26f, 0.02f, 0.26f, 0.04f, c(pal.enemyAccent))
        // Eye.
        val eyeX = 0.12f * dir
        if (!dead) {
            val eyeC = if (e.state == EnemyState.AIM || e.state == EnemyState.ALERT) 0xFFFF2A40.toInt() else pal.neon2
            g.fillCircle(eyeX, 0.02f, 0.13f, c(Col.alpha(eyeC, 0.25f)))
            g.fillCircle(eyeX, 0.02f, 0.065f, c(eyeC))
            g.fillCircle(eyeX + 0.02f * dir, 0.0f, 0.025f, c(0xFFFFFFFF.toInt()))
        }
        // Gun pod.
        g.line(0.05f * dir, 0.12f, 0.3f * dir, 0.13f, 0.05f, c(0xFF0A0A10.toInt()))
        g.restore()
        if (!dead && e.state == EnemyState.PATROL) {
            // Scanning fan.
            val sweep = sin(f.t * 2f + e.id) * 0.3f
            poly.tri(g, x + 0.12f * dir, y + 0.04f, x + 2.2f * dir, y + 0.6f + sweep, x + 2.2f * dir, y + 1.3f + sweep, c(Col.alpha(pal.neon2, 0.07f)))
        }
    }

    private fun turret(e: Enemy, x: Float, gy: Float, pal: Palette) {
        val rt = gy - Geo.FLOOR_H + Building.SLAB
        val y = gy - e.z
        val dead = e.state == EnemyState.DEAD
        val p = f.w.player
        // Barrel angle toward the player's chest.
        var ang = if (e.facing >= 0) 0f else 180f
        if (!dead && p.floor == e.floor) {
            val dx = p.x - x
            val dy = (Geo.groundY(p.floorF) - p.z - 0.9f) - y
            ang = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }
        if (dead) ang = 110f
        g.fillRect(x - 0.08f, rt, x + 0.08f, y - 0.2f, c(0xFF2A2A34.toInt()))
        g.fillRect(x - 0.3f, rt, x + 0.3f, rt + 0.08f, c(0xFF3A3A46.toInt()))
        g.save()
        g.translate(x, y)
        g.rotate(ang)
        g.fillRect(0.1f, -0.06f, 0.55f, 0.06f, c(0xFF15151C.toInt()))
        g.fillRect(0.45f, -0.08f, 0.55f, 0.08f, c(0xFF2A2A34.toInt()))
        g.restore()
        g.fillCircle(x, y - 0.05f, 0.24f, c(0xFF2E3040.toInt()))
        g.fillRect(x - 0.3f, y - 0.25f, x + 0.3f, y - 0.1f, c(0xFF3A3C50.toInt()))
        if (!dead) {
            val eyeC = if (e.state == EnemyState.AIM || e.state == EnemyState.ALERT) 0xFFFF2A40.toInt() else pal.neon2
            f.glowDot(x, y, 0.06f, eyeC, alphaMul)
        } else if (hash((f.t * 12f).toInt(), e.id) > 0.8f) {
            f.glowDot(x + 0.1f, y + 0.1f, 0.04f, 0xFFFFE080.toInt())
        }
    }

    // ============================================================ telegraphs

    /** The single most important read in the game: where and when the shot comes. */
    private fun aimTelegraph(e: Enemy, gy: Float, dir: Int, pal: Palette, fs: FloorState) {
        val aimT = Heat.aimTime(fs.plan.heat)
        val t = (e.stateTime / aimT).coerceIn(0f, 1f)
        val laser = 0xFFFF1E3C.toInt()
        val blink = if (t > 0.65f) (if (sin(f.t * 60f) > 0f) 1f else 0.45f) else 1f
        val a = (0.35f + 0.65f * t) * blink
        if (e.kind == EnemyKind.TURRET) {
            val p = f.w.player
            val y0 = gy - e.z
            val tx = p.x
            val ty = Geo.groundY(p.floorF) - p.z - 0.9f
            val dx = tx - e.x
            val dy = ty - y0
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val ex = e.x + dx / len * 12f
            val ey = y0 + dy / len * 12f
            g.save()
            g.clipRect(0f, gy - Geo.FLOOR_H + Building.SLAB, Geo.FLOOR_W, gy)
            f.glowLine(e.x, y0, ex, ey, 0.025f + 0.03f * t, Col.fade(laser, a), 0)
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
            // Fireball arc preview: dotted parabola.
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
        // Lane marker: a chevron at the far end tells you to jump (low) or duck/hide (high).
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

    private fun windupTelegraph(e: Enemy, gy: Float, dir: Int) {
        val t = (e.stateTime / 0.3f).coerceIn(0f, 1f)
        val cx = e.x + dir * 0.2f
        val cy = gy - 0.8f
        val n = 10
        for (i in 0 until n) {
            if (i > n * t) break
            val a0 = -1.2f + i * (2.4f / n)
            val a1 = a0 + 2.4f / n
            g.line(cx + cos(a0) * 1.1f * dir, cy + sin(a0) * 1.1f, cx + cos(a1) * 1.1f * dir, cy + sin(a1) * 1.1f, 0.06f + 0.05f * t, Col.alpha(0xFFFF2A40.toInt(), 0.35f + 0.5f * t))
        }
    }

    private fun statusMarks(e: Enemy, gy: Float, dir: Int) {
        val top = gy - e.z - e.height - 0.42f
        when (e.state) {
            EnemyState.ALERT -> if (e.stateTime < 0.9f) {
                val pop = if (e.stateTime < 0.12f) 1f + (0.12f - e.stateTime) * 5f else 1f
                val bx = e.x
                g.fillRoundRect(bx - 0.17f * pop, top - 0.46f * pop, bx + 0.17f * pop, top + 0.06f, 0.08f, 0xE0FFD21E.toInt())
                f.worldText("!", bx, top - 0.02f, 0.46f * pop, 0xFF1A0A00.toInt(), Gfx.Font.TITLE)
            }
            EnemyState.SEARCH -> {
                val sway = sin(f.t * 4f + e.id) * 0.06f
                g.fillRoundRect(e.x - 0.17f + sway, top - 0.46f, e.x + 0.17f + sway, top + 0.06f, 0.08f, 0xD0E8ECFF.toInt())
                f.worldText("?", e.x + sway, top - 0.02f, 0.42f, 0xFF1A1A30.toInt(), Gfx.Font.TITLE)
            }
            EnemyState.STUNNED -> {
                for (i in 0 until 3) {
                    val a = f.t * 5f + i * 2.1f
                    g.fillCircle(e.x + cos(a) * 0.25f, top + 0.15f + sin(a) * 0.07f, 0.045f, 0xFFFFE070.toInt())
                }
            }
            else -> Unit
        }
    }

    /** In the dark, alive enemies are silhouettes with glowing eyes. */
    fun darkEyes(fi: Int, fs: FloorState) {
        val d = fs.darkness
        if (d < 0.3f) return
        val gy = Geo.groundY(fi)
        if (!f.visibleY(gy - Geo.FLOOR_H, gy)) return
        for (e in f.w.enemies) {
            if (e.floor != fi || !e.alive) continue
            val dir = if (e.facing >= 0) 1 else -1
            val alarmed = e.state == EnemyState.ALERT || e.state == EnemyState.AIM || e.state == EnemyState.WINDUP
            val col = if (alarmed) 0xFFFF2A3A.toInt() else 0xFFFFE8A0.toInt()
            when (e.kind) {
                EnemyKind.DRONE -> f.glowDot(e.x + 0.12f * dir, gy - e.z - 0.18f, 0.05f, col, d)
                EnemyKind.TURRET -> f.glowDot(e.x, gy - e.z, 0.05f, col, d)
                else -> {
                    val hs = if (e.kind == EnemyKind.HEAVY) 1.12f else 1f
                    val ey = gy - e.z - (if (e.ducking) 0.95f else 1.36f * hs) + 0.02f
                    val ex = e.x + 0.1f * dir
                    f.glowDot(ex, ey, 0.028f, col, d)
                    f.glowDot(ex - 0.06f * dir, ey, 0.024f, col, d)
                }
            }
            if (e.state == EnemyState.AIM) aimTelegraph(e, gy, dir, f.palette(fs), fs)
        }
    }

    // ================================================================= loot

    private fun pickup(kind: PickupKind, x: Float, y: Float, age: Float, life: Float) {
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
        g.fillCircle(x, cy, 0.42f, Col.alpha(col, 0.14f))
        g.fillRoundRect(x - 0.22f, cy - 0.22f, x + 0.22f, cy + 0.22f, 0.07f, 0xE0101018.toInt())
        g.strokeRoundRect(x - 0.22f, cy - 0.22f, x + 0.22f, cy + 0.22f, 0.07f, 0.035f, col)
        pickupIcon(kind, x, cy, col)
        // Beam up so it's findable.
        g.fillRect(x - 0.015f, cy - 0.9f, x + 0.015f, cy - 0.25f, Col.alpha(col, 0.35f))
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
        g.fillCircle(x, yy, 0.09f, 0xFF3A4A2A.toInt())
        g.fillCircle(x - 0.03f, yy - 0.03f, 0.03f, 0xFF6A7A4A.toInt())
        val blink = sin(f.t * (20f + (1f - fuse) * 40f)) > 0f
        if (blink) f.glowDot(x, yy - 0.1f, 0.03f, 0xFFFF3030.toInt())
        // Danger ring when about to blow.
        if (fuse < 0.4f) g.strokeCircle(x, yy, 2.3f * (1f - fuse / 0.4f) * 0.4f + 0.2f, 0.02f, Col.alpha(0xFFFF3030.toInt(), 0.4f))
    }
}
