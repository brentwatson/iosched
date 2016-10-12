package com.google.samples.apps.iosched.explore.data

import java.util.*

open class ItemGroup {
    var title: String? = null
    var id: String? = null
    val sessions = ArrayList<SessionData>()

    fun addSessionData(session: SessionData) {
        sessions.add(session)
    }

    /**
     * Trim the session data to `sessionLimit` using the
     * `random Random Number Generator`.
     */
    fun trimSessionData(sessionLimit: Int) {
        while (sessions.size > sessionLimit) {
            sessions.removeAt(0)
        }
    }
}
