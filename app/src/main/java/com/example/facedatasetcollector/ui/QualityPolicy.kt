package com.example.facedatasetcollector.ui

/**
 * Every capture quality threshold lives here so a change is one reviewable diff. Bump [VERSION] when any value changes.
 */
object QualityPolicy {
    const val VERSION = 2

    // ponytail: kept from the old whole-frame check, not calibrated for a face crop yet. Calibrate on both devices before capture depends on them.
    const val MIN_FACE_LUMA = 100
    const val MAX_FACE_LUMA = 220

    // ponytail: every value below is a first guess from the reference set and the brief. Tune them all in the real-face test on both devices.

    // Head angles in degrees, ML Kit Euler. X is positive looking up.
    const val STRAIGHT_LIMIT = 10f
    const val TURN_MIN = 20f
    const val TURN_MAX = 40f
    const val CHIN_MIN = 8f
    const val CHIN_MAX = 30f
    const val MAX_ROLL = 15f

    // Calibration knob: makes positive yaw mean the person's own left. Unmirrored frames are assumed. Flip it if left and right swap on a device.
    const val YAW_SIGN = 1f

    // Holding still.
    const val STEADY_MS = 700L
    const val STEADY_DEGREES = 3f
    const val MAX_FRAME_GAP_MS = 500L

    // Framing, as fractions of the upright frame width.
    const val MIN_FACE_WIDTH = 0.35f
    const val MAX_FACE_WIDTH = 0.75f
    const val MAX_CENTRE_OFFSET = 0.15f

    // Face detail in the kept photo. CCTV needs 28 px and asks the app to aim for 60.
    const val MIN_PUPIL_PIXELS = 60f

    // Variance of Laplacian on a fixed-size sample, so the score does not change with distance.
    const val BLUR_SAMPLE_SIZE = 128
    const val MIN_BLUR_SCORE = 60.0

    // The kept 4:5 photo, from the reference set: the face fills about 45% of the width, with space above the hair.
    const val CROP_FACE_FRACTION = 0.45f
    const val CROP_TOP_MARGIN = 0.14f

    // Server limit is 2 MB per photo. Tried in order until a photo fits.
    const val MAX_PHOTO_BYTES = 2 * 1024 * 1024
    val JPEG_QUALITIES = listOf(90, 80, 70)

    // Glasses warning (D-021). Edge score across the nose bridge, from the owner's face only: 1.5 to 2.1 without glasses, 2.3 to 4.4 with, on CPH2381 and OPD2203. Chin up scored lowest with glasses on both, so one photo alone is not trusted.
    // ponytail: one face and one pair of glasses. Recheck with more people before it does more than warn.
    const val GLASSES_BRIDGE_EDGE = 2.4f
    const val GLASSES_VOTES = 3

    // Leaving the app for longer than this clears photos and signs staff out.
    const val BACKGROUND_LIMIT_MS = 2 * 60 * 1000L
}
