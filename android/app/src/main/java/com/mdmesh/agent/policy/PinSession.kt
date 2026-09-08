package com.mdmesh.agent.policy



/**

 * In-process flag so Accessibility never interrupts PIN screens

 * even before SharedPreferences commits finish.

 */

object PinSession {

    @Volatile

    var active: Boolean = false

}


