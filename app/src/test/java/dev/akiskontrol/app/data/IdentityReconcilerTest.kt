package dev.akiskontrol.app.data

import org.junit.Assert.*
import org.junit.Test

class IdentityReconcilerTest {
    @Test fun reinstallAtNewUidCannotDuplicatePackagesInOurOwnBackup() {
        val result = reconcileIdentities(mapOf(10001 to listOf("dev.example.app")),
            mapOf(10002 to listOf("dev.example.app")), setOf(10001))
        assertEquals(listOf("dev.example.app"), result.identities[10002])
        assertFalse(result.identities.containsKey(10001))
        assertTrue(result.identities.getValue(result.archivedUids.getValue(10001)).isEmpty())
        val packages = result.identities.values.flatten()
        assertEquals(packages.distinct(), packages)
    }

    @Test fun reusedUidKeepsOldPackageHistoryAwayFromItsNewOwner() {
        val result = reconcileIdentities(mapOf(10001 to listOf("dev.old.app")),
            mapOf(10001 to listOf("dev.new.app")), setOf(10001, Int.MAX_VALUE))
        val archive = result.archivedUids.getValue(10001)
        assertEquals(Int.MAX_VALUE - 1, archive)
        assertEquals(listOf("dev.old.app"), result.identities[archive])
        assertEquals(listOf("dev.new.app"), result.identities[10001])
    }

    @Test fun survivingSharedUidKeepsItsRuleIdentityAndPackageNamesUnique() {
        val result = reconcileIdentities(mapOf(10001 to listOf("dev.shared.one", "dev.shared.two")),
            mapOf(10001 to listOf("dev.shared.one"), 10002 to listOf("dev.shared.two")), setOf(10001))
        assertTrue(result.archivedUids.isEmpty())
        assertEquals(listOf("dev.shared.one"), result.identities[10001])
        assertEquals(listOf("dev.shared.two"), result.identities[10002])
    }

    @Test fun refreshingSameDiscoveryDoesNotArchiveAgain() {
        val discovery = mapOf(10001 to listOf("dev.new.app"))
        val first = reconcileIdentities(mapOf(10001 to listOf("dev.old.app")), discovery, setOf(10001))
        val next = reconcileIdentities(first.identities, discovery, first.identities.keys)
        assertEquals(first.identities, next.identities)
        assertTrue(next.archivedUids.isEmpty())
    }
}
