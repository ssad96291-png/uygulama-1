package dev.akiskontrol.app.data

internal data class IdentityReconciliation(
    val identities: Map<Int, List<String>>,
    val archivedUids: Map<Int, Int>,
)

/** Keep package names unique when Android reassigns UIDs or an app is reinstalled. */
internal fun reconcileIdentities(
    previous: Map<Int, List<String>>,
    discovered: Map<Int, List<String>>,
    usedUids: Set<Int>,
): IdentityReconciliation {
    val currentOwners = discovered.flatMap { (uid, packages) -> packages.map { it to uid } }.toMap()
    val retained = linkedMapOf<Int, List<String>>()
    val archived = linkedMapOf<Int, Int>()
    val occupied = (usedUids + previous.keys + discovered.keys).toMutableSet()
    var candidate = Int.MAX_VALUE
    previous.forEach { (uid, packages) ->
        val current = discovered[uid]
        val retainedPackages = packages.filter { currentOwners[it] == null || currentOwners[it] == uid }
        val reused = current != null && packages.none { it in current }
        val moved = packages.isNotEmpty() && retainedPackages.isEmpty()
        if (reused || moved) {
            while (candidate in occupied && candidate > 0) candidate--
            check(candidate !in occupied) { "Kullanım arşivi için kimlik ayrılamadı" }
            val archiveUid = candidate
            occupied.add(archiveUid)
            archived[uid] = archiveUid
            // Already reinstalled packages belong solely to their current UID. Empty package
            // lists retain local historical totals without falsely identifying a new owner.
            retained[archiveUid] = packages.filter { it !in currentOwners }
        } else retained[uid] = retainedPackages
    }
    retained.putAll(discovered)
    return IdentityReconciliation(retained, archived)
}
