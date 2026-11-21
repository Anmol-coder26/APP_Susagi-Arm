package com.susagi.audio

/**
 * Distinguishes the acoustic origin of an audio or transcript stream.
 */
enum class AudioSource {
    LOCAL,   // Local microphone / device user
    REMOTE   // Remote caller / interlocutor
}
