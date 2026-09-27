package com.bradflaugher.aboutthataction.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A two-bone chain: root (a) -> joint (j) -> end (e), plus the end's pitch (foot/hand angle, radians). */
internal class Limb {
    var ax = 0f
    var ay = 0f
    var jx = 0f
    var jy = 0f
    var ex = 0f
    var ey = 0f
    var len1 = 0f
    var len2 = 0f
    var pitch = 0f

    fun shift(dx: Float, dy: Float) {
        ax += dx; ay += dy; jx += dx; jy += dy; ex += dx; ey += dy
    }
}

/**
 * One humanoid pose in world units (y down), facing [dir]. Everything that
 * walks is built on this: forward kinematics for the keyframed cycles, two-bone
 * IK for planted feet, aimed guns and grapples. Allocation-free.
 *
 * Angles are measured from straight down, positive toward the facing side.
 */
internal class Rig {
    var dir = 1
    var hs = 1f
    var ground = 0f

    var hipX = 0f
    var hipY = 0f
    var neckX = 0f
    var neckY = 0f
    var headX = 0f
    var headY = 0f
    var headR = 0f
    var lean = 0f
    /** Unit spine axis (hip -> neck) and the chest-forward normal. */
    var ux = 0f
    var uy = -1f
    var nx = 1f
    var ny = 0f

    /** Near (front) and far (back) limbs. */
    val legF = Limb()
    val legB = Limb()
    val armF = Limb()
    val armB = Limb()

    var spineLen = 0f
    var neckLen = 0f
    var limbW = 0f
    var chestD = 0f
    var waistD = 0f

    /** Sets proportions. [bulk] widens the torso and limbs (heavies). */
    fun setup(dir: Int, ground: Float, hs: Float, bulk: Float = 1f) {
        this.dir = dir
        this.ground = ground
        this.hs = hs
        legF.len1 = 0.37f * hs; legF.len2 = 0.37f * hs
        legB.len1 = legF.len1; legB.len2 = legF.len2
        armF.len1 = 0.265f * hs; armF.len2 = 0.25f * hs
        armB.len1 = armF.len1; armB.len2 = armF.len2
        spineLen = 0.47f * hs
        neckLen = 0.06f * hs
        headR = 0.125f * hs
        limbW = 0.115f * hs * bulk
        chestD = 0.29f * hs * bulk
        waistD = 0.22f * hs * bulk
        legF.pitch = 0f; legB.pitch = 0f; armF.pitch = 0f; armB.pitch = 0f
    }

    fun hip(x: Float, y: Float) {
        hipX = x
        hipY = y
        legF.ax = x + 0.025f * dir * hs; legF.ay = y
        legB.ax = x - 0.025f * dir * hs; legB.ay = y
    }

    /** Places the torso and head over the hip. [lean] radians forward; [nod] tilts the head. */
    fun spine(lean: Float, nod: Float = 0f) {
        this.lean = lean
        ux = sin(lean) * dir
        uy = -cos(lean)
        nx = -uy * dir
        ny = ux * dir
        neckX = hipX + ux * spineLen
        neckY = hipY + uy * spineLen
        val hl = lean * 0.45f + nod
        val hd = neckLen + headR
        headX = neckX + sin(hl) * hd * dir + 0.018f * dir * hs
        headY = neckY - cos(hl) * hd
        // Shoulders sit just under the neck; the far one a touch back and up.
        val sx = neckX - ux * 0.055f * hs
        val sy = neckY - uy * 0.055f * hs
        armF.ax = sx + nx * 0.01f * hs; armF.ay = sy + ny * 0.01f * hs
        armB.ax = sx - nx * 0.035f * hs; armB.ay = sy - ny * 0.035f * hs - 0.01f * hs
    }

    // ------------------------------------------------------------ kinematics

    /** Leg FK: thigh angle [a1], knee [bend] (positive folds the shin back). */
    fun legFK(l: Limb, a1: Float, bend: Float) {
        l.jx = l.ax + sin(a1) * l.len1 * dir
        l.jy = l.ay + cos(a1) * l.len1
        val a2 = a1 - bend
        l.ex = l.jx + sin(a2) * l.len2 * dir
        l.ey = l.jy + cos(a2) * l.len2
    }

    /** Arm FK: shoulder angle [a1], elbow [bend] (positive folds the forearm forward/up). */
    fun armFK(l: Limb, a1: Float, bend: Float) {
        l.jx = l.ax + sin(a1) * l.len1 * dir
        l.jy = l.ay + cos(a1) * l.len1
        val a2 = a1 + bend
        l.ex = l.jx + sin(a2) * l.len2 * dir
        l.ey = l.jy + cos(a2) * l.len2
    }

    /** Two-bone IK to (tx, ty). Knees bend toward the facing side; elbows bend down/back. */
    fun ik(l: Limb, tx: Float, ty: Float, knee: Boolean) {
        var dx = tx - l.ax
        var dy = ty - l.ay
        var d = sqrt(dx * dx + dy * dy)
        val reach = (l.len1 + l.len2) * 0.999f
        if (d < 1e-4f) {
            dx = 0f; dy = 1f; d = 1e-4f
        }
        val ux = dx / d
        val uy = dy / d
        val dd = min(d, reach).coerceAtLeast(abs(l.len1 - l.len2) + 1e-3f)
        val a = (l.len1 * l.len1 - l.len2 * l.len2 + dd * dd) / (2f * dd)
        val h = sqrt(max(0f, l.len1 * l.len1 - a * a))
        val sgn = if (knee) -dir else dir
        l.jx = l.ax + ux * a + (-uy) * h * sgn
        l.jy = l.ay + uy * a + ux * h * sgn
        l.ex = l.ax + ux * dd
        l.ey = l.ay + uy * dd
    }

    private fun abs(v: Float) = if (v < 0f) -v else v

    fun shiftAll(dx: Float, dy: Float) {
        hipX += dx; hipY += dy; neckX += dx; neckY += dy; headX += dx; headY += dy
        legF.shift(dx, dy); legB.shift(dx, dy); armF.shift(dx, dy); armB.shift(dx, dy)
    }

    // ---------------------------------------------------------------- cycles

    /**
     * Keyframed locomotion. [u] = cycle position (1 = two steps), [run] blends walk (0) to sprint (1).
     * Places hip so the lowest foot touches [ground], then adds the flight lift.
     */
    fun locomote(x: Float, u: Float, run: Float, crouch: Float = 0f) {
        val uF = u
        val uB = u + 0.5f
        val tF = mix(cyc(WALK_THIGH, uF), cyc(RUN_THIGH, uF), run)
        val kF = mix(cyc(WALK_KNEE, uF), cyc(RUN_KNEE, uF), run) + crouch * 0.5f
        val tB = mix(cyc(WALK_THIGH, uB), cyc(RUN_THIGH, uB), run)
        val kB = mix(cyc(WALK_KNEE, uB), cyc(RUN_KNEE, uB), run) + crouch * 0.5f
        hip(x, 0f)
        legFK(legF, tF + crouch * 0.3f, kF)
        legFK(legB, tB + crouch * 0.3f, kB)
        legF.pitch = mix(cyc(WALK_PITCH, uF), cyc(RUN_PITCH, uF), run)
        legB.pitch = mix(cyc(WALK_PITCH, uB), cyc(RUN_PITCH, uB), run)
        val low = max(legF.ey, legB.ey)
        val lift = run * cyc(RUN_LIFT, u * 2f) * hs
        val dy = ground - low - lift
        hipY = dy
        legF.shift(0f, dy); legB.shift(0f, dy)
        // Feet never sink through the floor.
        if (legF.ey > ground) legF.ey = ground
        if (legB.ey > ground) legB.ey = ground
    }

    /** Arm swing for locomotion (opposite the legs). */
    fun swingArms(u: Float, run: Float, amount: Float = 1f) {
        val sF = -mix(cyc(WALK_THIGH, u + 0.5f), cyc(RUN_THIGH, u + 0.5f), run) * mix(0.75f, 1.05f, run) * amount
        val sB = -mix(cyc(WALK_THIGH, u), cyc(RUN_THIGH, u), run) * mix(0.75f, 1.05f, run) * amount
        armFK(armF, -sF * 1f + 0.05f, mix(0.25f, 1.35f, run) + max(0f, -sF) * 0.6f)
        armFK(armB, -sB * 1f + 0.05f, mix(0.25f, 1.35f, run) + max(0f, -sB) * 0.6f)
    }

    /** Stand with both feet planted around [x]; [drop] bends the knees (world units). */
    fun stand(x: Float, drop: Float, stanceF: Float = 0.1f, stanceB: Float = -0.13f) {
        val legLen = legF.len1 + legF.len2
        hip(x, ground - legLen * 0.965f + drop)
        ik(legF, x + stanceF * dir * hs, ground, true)
        ik(legB, x + stanceB * dir * hs, ground, true)
        legF.pitch = 0f
        legB.pitch = 0f
    }

    companion object {
        const val TAU = (2.0 * PI).toFloat()

        // Eight keys per cycle for the near leg; the far leg runs half a cycle later.
        // contact, recoil, passing, push, toe-off, kick, swing, reach
        val RUN_THIGH = floatArrayOf(0.62f, 0.34f, 0.02f, -0.42f, -0.72f, -0.38f, 0.28f, 0.8f)
        val RUN_KNEE = floatArrayOf(0.18f, 0.55f, 0.35f, 0.18f, 0.55f, 1.85f, 1.95f, 0.95f)
        val RUN_PITCH = floatArrayOf(-0.1f, 0f, 0f, 0.35f, 0.8f, 0.9f, 0.3f, -0.15f)
        /** Flight lift per step (4 keys = one step). */
        val RUN_LIFT = floatArrayOf(0f, -0.01f, 0.02f, 0.06f)

        val WALK_THIGH = floatArrayOf(0.42f, 0.3f, 0.06f, -0.2f, -0.4f, -0.18f, 0.18f, 0.42f)
        val WALK_KNEE = floatArrayOf(0.05f, 0.2f, 0.08f, 0.1f, 0.45f, 0.95f, 0.7f, 0.2f)
        val WALK_PITCH = floatArrayOf(-0.15f, 0f, 0f, 0.1f, 0.5f, 0.3f, 0f, -0.2f)

        fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

        /** Cyclic Catmull-Rom through [keys] at [u] (period 1). */
        fun cyc(keys: FloatArray, u: Float): Float {
            val n = keys.size
            val x = (u - floor(u)) * n
            val i = x.toInt() % n
            val t = x - floor(x)
            val p0 = keys[(i - 1 + n) % n]
            val p1 = keys[i]
            val p2 = keys[(i + 1) % n]
            val p3 = keys[(i + 2) % n]
            val t2 = t * t
            val t3 = t2 * t
            return 0.5f * ((2f * p1) + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3)
        }

        fun smooth(t: Float): Float {
            val u = t.coerceIn(0f, 1f)
            return u * u * (3f - 2f * u)
        }

        /** Overshooting ease-out for pops. */
        fun backOut(t: Float): Float {
            val u = t.coerceIn(0f, 1f) - 1f
            return 1f + u * u * (2.7f * u + 1.7f)
        }

        fun easeOut(t: Float): Float {
            val u = 1f - t.coerceIn(0f, 1f)
            return 1f - u * u
        }
    }
}
