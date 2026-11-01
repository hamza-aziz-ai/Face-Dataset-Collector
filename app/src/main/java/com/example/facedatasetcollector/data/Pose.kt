package com.example.facedatasetcollector.data

/** One required photo. [wireName] is the multipart part name in the CCTV contract and must not change without telling that project. */
enum class Pose(val wireName: String, val label: String, val withGlasses: Boolean) {
    FRONT("front", "Front", false),
    CHIN_UP("chin_up", "Chin up", false),
    CHIN_DOWN("chin_down", "Chin down", false),
    LEFT_30("left_30", "Left 30", false),
    RIGHT_30("right_30", "Right 30", false),
    FRONT_GLASSES("front_glasses", "Front", true),
    CHIN_UP_GLASSES("chin_up_glasses", "Chin up", true),
    CHIN_DOWN_GLASSES("chin_down_glasses", "Chin down", true),
    LEFT_30_GLASSES("left_30_glasses", "Left 30", true),
    RIGHT_30_GLASSES("right_30_glasses", "Right 30", true);

    companion object {
        /** The server takes the 5 glasses poses all or none, so this is the only way to build a set. */
        fun setFor(wearsGlasses: Boolean): List<Pose> = if (wearsGlasses) entries else entries.filter { !it.withGlasses }
    }
}
