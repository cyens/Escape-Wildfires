package com.ewf.escapewildfire

enum class TimeSlot (val index: Int) {
    NOW(0),
    FIFTEEN(1),
    THIRTY(2),
    FOURTYFIVE(3),
    SIXTY(4);

    companion object {
        fun get(Index:Int): TimeSlot {
            return when(Index) {
                0 -> NOW
                1 -> FIFTEEN
                2 -> THIRTY
                3 -> FOURTYFIVE
                4 -> SIXTY
                else -> NOW
            }
        }
    }
}