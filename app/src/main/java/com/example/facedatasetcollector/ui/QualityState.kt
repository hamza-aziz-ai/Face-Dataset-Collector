package com.example.facedatasetcollector.ui

sealed class QualityState {
    data object Good : QualityState()
    data object TooDark : QualityState()
    data object TooBright : QualityState()
    data object TooBlurry : QualityState()
    data object NoFaceDetected : QualityState()
    data object MultipleFaces : QualityState()
    data object DetectorFailed : QualityState()
}
