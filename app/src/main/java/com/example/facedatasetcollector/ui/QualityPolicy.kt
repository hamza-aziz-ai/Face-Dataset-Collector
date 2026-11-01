package com.example.facedatasetcollector.ui

/**
 * Every capture quality threshold lives here so a change is one reviewable diff. Bump [VERSION] when any value changes.
 */
object QualityPolicy {
    const val VERSION = 1

    // ponytail: kept from the old whole-frame check, not calibrated for a face crop yet. Calibrate on both devices before capture depends on them.
    const val MIN_FACE_LUMA = 100
    const val MAX_FACE_LUMA = 220
}
